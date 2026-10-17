package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.technology.ApplicationProduction;
import com.spaceconquest.engine.MarketOrder;
import com.spaceconquest.engine.Moon;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.SolarRadiation;
import com.spaceconquest.engine.economy.MarketAccount;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Buys reactor fuel and measures staffed generation on each local grid. */
public class PowerGenerationProcessor {
    private final Map<String, PowerPlantCatalog.Plant> plants;
    public PowerGenerationProcessor() { this(PowerPlantCatalog.all()); }
    public PowerGenerationProcessor(Map<String, PowerPlantCatalog.Plant> plants) { this.plants = Map.copyOf(plants); }
    public record Result(List<Empire> empires, List<Corporation> corporations,
                         List<CommercialHub> hubs, List<MarketAccount> marketAccounts,
                         List<IndustryAccount> accounts, Map<String, Double> generationKw,
                         List<IndustryAccount> industryAccounts) {}

    public Result process(GameState state, Map<String, Integer> paidWorkers,
                          Map<String, Double> paidWages) {
        return process(state, paidWorkers, paidWages, true);
    }
    /** A rated-capacity probe may disable dispatch; ordinary simulation and pricing always follow demand. */
    public Result process(GameState state, Map<String, Integer> paidWorkers,
                          Map<String, Double> paidWages, boolean followDemand) {
        Ledger ledger = new Ledger(state);
        var dispatch = followDemand ? dispatchFractions(state, paidWorkers, ledger) : Map.<String, Double>of();
        List<IndustryAccount> accounts = new ArrayList<>();
        Map<String, Double> generation = new HashMap<>();
        for (IndustrialFacility facility : state.industrialFacilities()) {
            PowerPlantCatalog.Plant plant = plants.get(facility.applicationId());
            if (plant == null) continue;
            int staffed = IndustrialFacility.HIVE_GRID.equals(facility.ownershipType())
                    ? facility.allocatedWorkers() : paidWorkers.getOrDefault(facility.id(), 0);
            double wages = paidWages.getOrDefault(facility.id(), 0.0);
            double kw = 0.0;
            double fuelCost = 0.0;
            Empire technologyOwner = ledger.technologyOwner(facility);
            if (technologyOwner != null && technologyOwner.unlockedTechIds().contains("electricity")
                    && technologyOwner.unlockedTechIds().contains(plant.requiredTechnology())
                    && ApplicationProduction.isAvailable(technologyOwner, facility.applicationId())
                    && staffed > 0 && facility.tier() > 0
                    && FacilityManufacturingCapacity.canOperate(state, facility)) {
                double environment = environmentFactor(state.solarSystems(), facility, plant);
                double scale = GenerationCapacity.scale(facility, plant, staffed);
                if (followDemand) scale *= dispatch.getOrDefault(facility.id(), 0.0);
                kw = plant.kwPerTier() * scale * environment
                        * ApplicationProduction.modifiers(state, technologyOwner.id(), facility.applicationId()).effectMultiplier();
                if (plant.fuelMaterialId() != null && kw > 0.0) {
                    double[] fuel = ledger.buyFuel(facility, plant.fuelMaterialId(),
                            plant.fuelKgPerTierDay() * scale);
                    kw *= fuel[0];
                    fuelCost = fuel[1];
                }
            }
            generation.merge(facility.planetId(), kw, Double::sum);
            accounts.add(ledger.cash.withDay(new IndustryAccount(facility.id(), Map.of(), Map.of(), Map.of(),
                    fuelCost, wages, 0.0, 0.0, kw * 24.0, 0.0).withPaidWorkers(staffed)));
        }
        return new Result(List.copyOf(ledger.empires.values()), List.copyOf(ledger.corporations.values()),
                List.copyOf(ledger.hubs.values()), List.copyOf(ledger.accounts.values()),
                List.copyOf(accounts), Map.copyOf(generation), ledger.cash.snapshot());
    }

    private Map<String, Double> dispatchFractions(GameState state, Map<String, Integer> paidWorkers, Ledger ledger) {
        var capacity = new HashMap<String, Double>();
        var availability = new HashMap<String, Double>();
        for (var facility : state.industrialFacilities()) {
            var plant = plants.get(facility.applicationId()); var owner = ledger.technologyOwner(facility);
            if (plant == null || owner == null || !owner.unlockedTechIds().contains("electricity")
                    || !owner.unlockedTechIds().contains(plant.requiredTechnology()) || !ApplicationProduction.isAvailable(owner, facility.applicationId())
                    || !FacilityManufacturingCapacity.canOperate(state, facility)) continue;
            int staffed = IndustrialFacility.HIVE_GRID.equals(facility.ownershipType()) ? facility.allocatedWorkers() : paidWorkers.getOrDefault(facility.id(), 0);
            double scale = GenerationCapacity.scale(facility, plant, staffed);
            double fraction = availableFuelFraction(ledger, facility, plant, scale);
            availability.put(facility.id(), fraction);
            capacity.merge(facility.planetId(), plant.kwPerTier() * scale * fraction * environmentFactor(state.solarSystems(), facility, plant)
                    * ApplicationProduction.modifiers(state, owner.id(), facility.applicationId()).effectMultiplier(), Double::sum);
        }
        var result = new HashMap<String, Double>();
        for (var grid : new PowerProcessor().balanceDay(state, capacity, paidWorkers).grids()) {
            var storage = state.powerGrids().stream().filter(g -> g.entityId().equals(grid.entityId())).findFirst().orElse(null);
            double chargingKw = storage == null ? 0 : Math.max(0, storage.batteryCapacityKwh() - storage.currentStoredKwh()) / 24;
            double available = capacity.getOrDefault(grid.entityId(), 0.0);
            double fraction = available <= 0 ? 0 : Math.clamp((grid.totalDemandKw() + chargingKw) / available, 0, 1);
            state.industrialFacilities().stream().filter(f -> f.planetId().equals(grid.entityId())).forEach(f ->
                    result.put(f.id(), fraction * availability.getOrDefault(f.id(), 0.0)));
        }
        return Map.copyOf(result);
    }
    private double availableFuelFraction(Ledger ledger, IndustrialFacility facility, PowerPlantCatalog.Plant plant, double scale) {
        if (plant.fuelMaterialId() == null || plant.fuelKgPerTierDay() * scale <= 0) return 1;
        var hub = ledger.hubs.values().stream().filter(h -> h.entityId().equals(facility.planetId())).findFirst().orElse(null);
        var fuel = hub == null ? null : hub.activeOrders().get(plant.fuelMaterialId());
        if (fuel == null) return 0;
        double required = plant.fuelKgPerTierDay() * scale;
        double fraction = Math.clamp(fuel.supplyKg() / required, 0, 1);
        double cost = required * Math.max(0, fuel.pricePerKg());
        return cost <= 0 || IndustrialFacility.HIVE_GRID.equals(facility.ownershipType()) ? fraction
                : Math.min(fraction, Math.clamp(ledger.ownerBalance(facility) / cost, 0, 1));
    }

    public static double environmentFactor(List<SolarSystem> systems, IndustrialFacility facility) {
        return environmentFactor(systems, facility, PowerPlantCatalog.find(facility.applicationId()));
    }

    private static double environmentFactor(List<SolarSystem> systems, IndustrialFacility facility, PowerPlantCatalog.Plant plant) {
        if (plant == null) return 0;
        for (SolarSystem system : systems) {
            for (Planet planet : system.planets()) {
                if (planet.id().equals(facility.planetId())) {
                    return environmentFactor(system, plant, planet.distance(),
                            planet.atmosphere(), planet.hasLiquidWater());
                }
                for (Moon moon : planet.moons()) {
                    if (moon.id().equals(facility.planetId())) {
                        return environmentFactor(system, plant, planet.distance(),
                                moon.atmosphere(), moon.hasLiquidWater());
                    }
                }
            }
        }
        return plant.environment() == PowerPlantCatalog.Environment.ANY ? 1 : 0;
    }

    private static double environmentFactor(SolarSystem system, PowerPlantCatalog.Plant plant, double orbitalDistanceKm,
                                     String atmosphere, boolean hasLiquidWater) {
        return switch (plant.environment()) {
            case SOLAR -> SolarRadiation.factor(system, orbitalDistanceKm)
                    * ("none".equalsIgnoreCase(atmosphere) ? 1.0 : plant.atmosphericTransmission());
            case ATMOSPHERE -> "none".equalsIgnoreCase(atmosphere) ? 0.0 : 1.0;
            case LIQUID_WATER -> hasLiquidWater ? 1.0 : 0.0;
            case ANY -> 1.0;
        };
    }

    private static final class Ledger {
        private final Map<String, Empire> empires = new LinkedHashMap<>();
        private final Map<String, Corporation> corporations = new LinkedHashMap<>();
        private final Map<String, CommercialHub> hubs = new LinkedHashMap<>();
        private final Map<String, MarketAccount> accounts = new LinkedHashMap<>();
        private final IndustryOperatingLedger cash;

        private Ledger(GameState state) {
            cash = new IndustryOperatingLedger(state.industryAccounts());
            for (Empire empire : state.empires()) empires.put(empire.id(), empire);
            for (Corporation corporation : state.corporations()) corporations.put(corporation.id(), corporation);
            for (CommercialHub hub : state.commercialHubs()) hubs.put(hub.id(), hub.observeDay(state.turn()));
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
            hubs.put(hub.id(), hub.withInventory(hub.stockHistory().currentTurn(),
                    Math.max(0.0, hub.currentStoredWeightKg() - kg), orders));
            if (cost > 0.0) {
                debitOwner(facility, cost);
                MarketAccount previous = accounts.get(hub.id());
                accounts.put(hub.id(), new MarketAccount(hub.id(),
                        (previous == null ? 0.0 : previous.unsettledSalesCredits()) + cost));
            }
            return new double[]{kg / requestedKg, cost};
        }

        private double ownerBalance(IndustrialFacility facility) {
            if (IndustrialFacility.PUBLIC_STATE.equals(facility.ownershipType())) return cash.balance(facility);
            if (IndustrialFacility.PRIVATE_CORPORATE.equals(facility.ownershipType())) {
                Corporation owner = corporations.get(facility.ownerEntityId());
                return owner == null ? 0.0 : owner.liquidCapitalReserves();
            }
            Empire owner = empires.get(facility.ownerEntityId());
            return owner == null ? 0.0 : owner.treasuryCredits();
        }

        private void debitOwner(IndustrialFacility facility, double cost) {
            if (IndustrialFacility.PUBLIC_STATE.equals(facility.ownershipType())) {
                cash.change(facility, -cost);
                return;
            }
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
