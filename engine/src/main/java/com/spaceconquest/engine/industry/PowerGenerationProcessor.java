package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.MarketOrder;
import com.spaceconquest.engine.Moon;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.economy.MarketAccount;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Buys reactor fuel and measures staffed generation on each local grid. */
public class PowerGenerationProcessor {
    public record Result(List<Empire> empires, List<Corporation> corporations,
                         List<CommercialHub> hubs, List<MarketAccount> marketAccounts,
                         List<IndustryAccount> accounts, Map<String, Double> generationKw) {}

    public Result process(GameState state, Map<String, Integer> paidWorkers,
                          Map<String, Double> paidWages) {
        Ledger ledger = new Ledger(state);
        List<IndustryAccount> accounts = new ArrayList<>();
        Map<String, Double> generation = new HashMap<>();
        for (IndustrialFacility facility : state.industrialFacilities()) {
            PowerPlantCatalog.Plant plant = PowerPlantCatalog.find(facility.applicationId());
            if (plant == null) continue;
            int staffed = IndustrialFacility.HIVE_GRID.equals(facility.ownershipType())
                    ? facility.allocatedWorkers() : paidWorkers.getOrDefault(facility.id(), 0);
            double wages = paidWages.getOrDefault(facility.id(), 0.0);
            double kw = 0.0;
            double fuelCost = 0.0;
            Empire technologyOwner = ledger.technologyOwner(facility);
            if (technologyOwner != null && technologyOwner.unlockedTechIds().contains("electricity")
                    && technologyOwner.unlockedTechIds().contains(plant.requiredTechnology())
                    && staffed > 0 && facility.tier() > 0) {
                double staffing = Math.min(1.0, (double) staffed / plant.requiredWorkers());
                double environment = environmentFactor(state.solarSystems(), facility);
                double scale = facility.tier() * staffing * facility.getEffectiveThroughputMultiplier()
                        / Math.pow(1.5, Math.max(0, facility.tier() - 1));
                kw = plant.kwPerTier() * scale * environment;
                if (plant.fuelMaterialId() != null && kw > 0.0) {
                    double[] fuel = ledger.buyFuel(facility, plant.fuelMaterialId(),
                            plant.fuelKgPerTierDay() * scale);
                    kw *= fuel[0];
                    fuelCost = fuel[1];
                }
            }
            generation.merge(facility.planetId(), kw, Double::sum);
            accounts.add(new IndustryAccount(facility.id(), Map.of(), Map.of(), Map.of(),
                    fuelCost, wages, 0.0, 0.0, kw * 24.0, 0.0));
        }
        return new Result(List.copyOf(ledger.empires.values()), List.copyOf(ledger.corporations.values()),
                List.copyOf(ledger.hubs.values()), List.copyOf(ledger.accounts.values()),
                List.copyOf(accounts), Map.copyOf(generation));
    }

    private double environmentFactor(List<SolarSystem> systems, IndustrialFacility facility) {
        for (SolarSystem system : systems) {
            for (Planet planet : system.planets()) {
                if (planet.id().equals(facility.planetId())) {
                    return environmentFactor(facility.applicationId(), planet.distance(),
                            planet.atmosphere(), planet.hasLiquidWater());
                }
                for (Moon moon : planet.moons()) {
                    if (moon.id().equals(facility.planetId())) {
                        return environmentFactor(facility.applicationId(), planet.distance(),
                                moon.atmosphere(), moon.hasLiquidWater());
                    }
                }
            }
        }
        return 1.0;
    }

    private double environmentFactor(String applicationId, double orbitalDistanceKm,
                                     String atmosphere, boolean hasLiquidWater) {
        return switch (applicationId) {
            case "solar_power" -> Math.clamp(Math.pow(149_600_000.0
                    / Math.max(1.0, orbitalDistanceKm), 2.0), 0.1, 2.0)
                    * ("none".equalsIgnoreCase(atmosphere) ? 1.0 : 0.85);
            case "wind_power" -> "none".equalsIgnoreCase(atmosphere) ? 0.0 : 1.0;
            case "hydropower" -> hasLiquidWater ? 1.0 : 0.0;
            default -> 1.0;
        };
    }

    private static final class Ledger {
        private final Map<String, Empire> empires = new LinkedHashMap<>();
        private final Map<String, Corporation> corporations = new LinkedHashMap<>();
        private final Map<String, CommercialHub> hubs = new LinkedHashMap<>();
        private final Map<String, MarketAccount> accounts = new LinkedHashMap<>();

        private Ledger(GameState state) {
            for (Empire empire : state.empires()) empires.put(empire.id(), empire);
            for (Corporation corporation : state.corporations()) corporations.put(corporation.id(), corporation);
            for (CommercialHub hub : state.commercialHubs()) hubs.put(hub.id(), hub);
            for (MarketAccount account : state.marketAccounts()) accounts.put(account.hubId(), account);
        }

        private Empire technologyOwner(IndustrialFacility facility) {
            if (!IndustrialFacility.PRIVATE_CORPORATE.equals(facility.ownershipType())) {
                return empires.get(facility.ownerEntityId());
            }
            Corporation corporation = corporations.get(facility.ownerEntityId());
            return corporation == null ? null : empires.get(corporation.empireId());
        }

        private double[] buyFuel(IndustrialFacility facility, String material, double requestedKg) {
            CommercialHub hub = hubs.values().stream()
                    .filter(candidate -> candidate.entityId().equals(facility.planetId()))
                    .findFirst().orElse(null);
            if (hub == null || requestedKg <= 0.0) return new double[]{0.0, 0.0};
            MarketOrder order = hub.activeOrders().get(material);
            if (order == null || order.supplyKg() <= 0.0) return new double[]{0.0, 0.0};
            double price = Math.max(0.0, order.pricePerKg());
            double available = IndustrialFacility.HIVE_GRID.equals(facility.ownershipType())
                    ? Double.MAX_VALUE : ownerBalance(facility);
            double kg = Math.min(requestedKg, Math.min(order.supplyKg(),
                    price == 0.0 ? requestedKg : available / price));
            if (kg <= 0.0) return new double[]{0.0, 0.0};
            double cost = IndustrialFacility.HIVE_GRID.equals(facility.ownershipType()) ? 0.0 : kg * price;
            Map<String, MarketOrder> orders = new HashMap<>(hub.activeOrders());
            orders.put(material, new MarketOrder(material, Math.max(0.0, order.supplyKg() - kg),
                    order.demandKg(), order.pricePerKg(), order.shortcomingScore()));
            hubs.put(hub.id(), new CommercialHub(hub.id(), hub.entityId(), hub.transactionTariffRate(),
                    hub.storageCapacityKg(), Math.max(0.0, hub.currentStoredWeightKg() - kg),
                    hub.logisticsRangeUnits(), orders));
            if (cost > 0.0) {
                debitOwner(facility, cost);
                MarketAccount previous = accounts.get(hub.id());
                accounts.put(hub.id(), new MarketAccount(hub.id(),
                        (previous == null ? 0.0 : previous.unsettledSalesCredits()) + cost));
            }
            return new double[]{kg / requestedKg, cost};
        }

        private double ownerBalance(IndustrialFacility facility) {
            if (IndustrialFacility.PRIVATE_CORPORATE.equals(facility.ownershipType())) {
                Corporation owner = corporations.get(facility.ownerEntityId());
                return owner == null ? 0.0 : owner.liquidCapitalReserves();
            }
            Empire owner = empires.get(facility.ownerEntityId());
            return owner == null ? 0.0 : owner.treasuryCredits();
        }

        private void debitOwner(IndustrialFacility facility, double cost) {
            if (IndustrialFacility.PRIVATE_CORPORATE.equals(facility.ownershipType())) {
                Corporation owner = corporations.get(facility.ownerEntityId());
                corporations.put(owner.id(), new Corporation(owner.id(), owner.name(), owner.empireId(),
                        owner.headquartersEntityId(), owner.marketOrientation(),
                        Math.max(0.0, owner.liquidCapitalReserves() - cost), owner.ownedFacilityIds(),
                        owner.ownedShipIds(), owner.claimedVeinIds()));
            } else {
                Empire owner = empires.get(facility.ownerEntityId());
                empires.put(owner.id(), new Empire(owner.id(), owner.name(), owner.raceId(),
                        owner.societyStructure(), Math.max(0.0, owner.treasuryCredits() - cost),
                        owner.corporateTaxRate(), owner.controlledSystemIds(), owner.ministries(),
                        owner.systemGovernorAssignments(), owner.unlockedTechIds(), owner.activeShipDesignIds()));
            }
        }
    }
}
