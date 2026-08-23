package com.spaceconquest.engine.market;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.MarketOrder;
import com.spaceconquest.engine.ShadowSyndicate;
import com.spaceconquest.engine.SystemGovernor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simulates systemic crime metrics, police suppression, black market leakage (L_credits),
 * shadow capital pooling, and autonomous pirate fleet construction.
 */
public class CrimeProcessor {

    public static final double ROGUE_SHIP_BUILD_COST = 5000.0;
    private static final double EPSILON = 0.01;

    /**
     * Calculates the local crime metric for an entity/hub.
     *
     * @param hub                  the commercial hub
     * @param isHiveMind           whether the controlling empire is a Hive Mind (100% immune)
     * @param policeEfficiency     police suppression efficiency
     * @param governorCrimeBonus   system governor crime reduction bonus
     * @param systemLawAndOrderLevel system economy law and order funding level index
     * @return net crime metric (0.0 or positive)
     */
    public double calculateCrimeMetric(
            CommercialHub hub,
            boolean isHiveMind,
            double policeEfficiency,
            double governorCrimeBonus,
            double systemLawAndOrderLevel
    ) {
        if (isHiveMind) {
            return 0.0;
        }

        double commercialDensity = hub.activeOrders().size() * 0.10;
        double storedVolumeFactor = (hub.currentStoredWeightKg() / Math.max(1.0, hub.storageCapacityKg())) * 0.20;
        double rawCrime = commercialDensity + storedVolumeFactor;

        double lawFundingBonus = Math.max(0.0, (systemLawAndOrderLevel - 1.0) * 0.15);
        double lawFundingPenalty = systemLawAndOrderLevel < 1.0 ? (1.0 - systemLawAndOrderLevel) * 0.15 : 0.0;

        double netCrime = rawCrime + lawFundingPenalty - policeEfficiency - governorCrimeBonus - lawFundingBonus;
        return Math.max(0.0, netCrime);
    }

    public double calculateCrimeMetric(
            CommercialHub hub,
            boolean isHiveMind,
            double policeEfficiency,
            double governorCrimeBonus
    ) {
        return calculateCrimeMetric(hub, isHiveMind, policeEfficiency, governorCrimeBonus, 1.0);
    }

    /**
     * Calculates black market leakage credits siphoned away from state tariffs.
     * Formula: L_credits = Gross Transaction Value * Tariff Rate * (Local Crime Metric / (Local Police Efficiency + epsilon))
     *
     * @param grossTransactionValue total transaction volume value
     * @param tariffRate            tariff rate
     * @param crimeMetric           local crime score
     * @param policeEfficiency      police suppression efficiency
     * @return siphoned leakage credits
     */
    public double calculateBlackMarketLeakage(
            double grossTransactionValue,
            double tariffRate,
            double crimeMetric,
            double policeEfficiency
    ) {
        if (crimeMetric <= 0.0 || grossTransactionValue <= 0.0 || tariffRate <= 0.0) {
            return 0.0;
        }
        double ratio = crimeMetric / (policeEfficiency + EPSILON);
        return grossTransactionValue * tariffRate * Math.min(1.0, ratio);
    }

    /**
     * Processes crime and black market leakage across all empires, commercial hubs, and shadow syndicates.
     *
     * @param state the current simulation state
     * @return updated CrimeResult containing updated empires, shadow syndicates, and total leaked credits
     */
    public CrimeResult processCrime(GameState state) {
        if (state == null) {
            return new CrimeResult(List.of(), List.of(), 0.0);
        }

        Map<String, Empire> empireMap = new HashMap<>();
        for (Empire e : state.empires()) {
            empireMap.put(e.id(), e);
        }

        Map<String, SystemGovernor> governorMap = new HashMap<>();
        for (SystemGovernor g : state.systemGovernors()) {
            governorMap.put(g.solarSystemId(), g);
        }

        Map<String, ShadowSyndicate> syndicateMap = new HashMap<>();
        for (ShadowSyndicate s : state.shadowSyndicates()) {
            syndicateMap.put(s.empireId(), s);
        }

        Map<String, Double> systemLawMap = new HashMap<>();
        if (state.systemEconomies() != null) {
            for (com.spaceconquest.engine.economy.SystemEconomy se : state.systemEconomies()) {
                systemLawMap.put(se.systemId(), se.lawAndOrderLevel());
            }
        }

        Map<String, String> planetToSystemMap = new HashMap<>();
        if (state.solarSystems() != null) {
            for (com.spaceconquest.engine.SolarSystem sys : state.solarSystems()) {
                if (sys.planets() != null) {
                    for (com.spaceconquest.engine.Planet p : sys.planets()) {
                        planetToSystemMap.put(p.id(), sys.id());
                    }
                }
            }
        }

        double totalGalaxyLeakage = 0.0;

        for (CommercialHub hub : state.commercialHubs()) {
            // Find empire associated with hub
            Empire empire = findEmpireForHub(hub, state.empires());
            if (empire == null) continue;

            boolean isHiveMind = "Hive Mind".equalsIgnoreCase(empire.societyStructure())
                    || "Hive mind".equalsIgnoreCase(empire.societyStructure());

            if (isHiveMind) {
                // 100% immune to crime and black market leakage
                continue;
            }

            double governorBonus = 0.0;
            // Check if governor exists for the system
            for (SystemGovernor g : state.systemGovernors()) {
                governorBonus = Math.max(governorBonus, g.crimeReductionBonus());
            }

            String sysId = planetToSystemMap.get(hub.entityId());
            double systemLawLevel = sysId != null ? systemLawMap.getOrDefault(sysId, 1.0) : 1.0;

            double policeEfficiency = 0.10; // baseline police
            double crimeMetric = calculateCrimeMetric(hub, isHiveMind, policeEfficiency, governorBonus, systemLawLevel);

            double grossValue = calculateHubGrossTransactionValue(hub);
            double leakage = calculateBlackMarketLeakage(grossValue, hub.transactionTariffRate(), crimeMetric, policeEfficiency);
            totalGalaxyLeakage += leakage;

            if (leakage > 0.0) {
                ShadowSyndicate currentSyndicate = syndicateMap.get(empire.id());
                double currentPool = currentSyndicate != null ? currentSyndicate.shadowCapitalPool() : 0.0;
                List<String> rogueShips = currentSyndicate != null ? new ArrayList<>(currentSyndicate.rogueShipIds()) : new ArrayList<>();
                String baseSys = currentSyndicate != null ? currentSyndicate.baseSystemId() : (empire.controlledSystemIds().isEmpty() ? "unknown" : empire.controlledSystemIds().getFirst());
                String syndicateId = currentSyndicate != null ? currentSyndicate.id() : "syndicate_" + empire.id();
                String syndicateName = currentSyndicate != null ? currentSyndicate.name() : empire.name() + " Shadow Syndicate";

                double newPool = currentPool + leakage;
                while (newPool >= ROGUE_SHIP_BUILD_COST) {
                    newPool -= ROGUE_SHIP_BUILD_COST;
                    rogueShips.add("rogue_raider_" + (rogueShips.size() + 1));
                }

                syndicateMap.put(empire.id(), new ShadowSyndicate(
                        syndicateId,
                        syndicateName,
                        empire.id(),
                        baseSys,
                        newPool,
                        rogueShips
                ));
            }
        }

        return new CrimeResult(
                new ArrayList<>(empireMap.values()),
                new ArrayList<>(syndicateMap.values()),
                totalGalaxyLeakage
        );
    }

    private double calculateHubGrossTransactionValue(CommercialHub hub) {
        double gross = 0.0;
        for (MarketOrder order : hub.activeOrders().values()) {
            gross += order.supplyKg() * order.pricePerKg();
        }
        return gross;
    }

    private Empire findEmpireForHub(CommercialHub hub, List<Empire> empires) {
        if (empires.isEmpty()) return null;
        // Default to first matching or first empire
        return empires.getFirst();
    }

    public record CrimeResult(
            List<Empire> empires,
            List<ShadowSyndicate> shadowSyndicates,
            double totalLeakedCredits
    ) {}
}
