package com.spaceconquest.engine.market;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ShadowSyndicate;
import com.spaceconquest.engine.SystemGovernor;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.industry.IndustryAccount;

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
        return grossTransactionValue * tariffRate * Math.clamp(ratio, 0.0, 1.0);
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

        Map<String, Double> dailyTradingByBody = dailyTradingByBody(state);
        Map<String, Integer> hubsByBody = new HashMap<>();
        for (CommercialHub hub : state.commercialHubs())
            hubsByBody.merge(hub.entityId(), 1, Integer::sum);

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

            String sysId = planetToSystemMap.get(hub.entityId());
            SystemGovernor governor = governorMap.get(sysId);
            double governorBonus = governor == null ? 0.0 : governor.crimeReductionBonus();
            double systemLawLevel = sysId != null ? systemLawMap.getOrDefault(sysId, 1.0) : 1.0;

            double policeEfficiency = 0.10; // baseline police
            double crimeMetric = calculateCrimeMetric(hub, isHiveMind, policeEfficiency, governorBonus, systemLawLevel);

            double grossValue = dailyTradingByBody.getOrDefault(hub.entityId(), 0.0)
                    / hubsByBody.getOrDefault(hub.entityId(), 1);
            double leakage = calculateBlackMarketLeakage(grossValue, hub.transactionTariffRate(), crimeMetric, policeEfficiency);
            totalGalaxyLeakage += leakage;

            if (leakage > 0.0) {
                syndicateMap.put(empire.id(), advanceSyndicate(
                        syndicateMap.get(empire.id()), empire, leakage));
            }
        }

        return new CrimeResult(
                new ArrayList<>(empireMap.values()),
                new ArrayList<>(syndicateMap.values()),
                totalGalaxyLeakage
        );
    }

    private ShadowSyndicate advanceSyndicate(ShadowSyndicate current, Empire empire,
                                             double leakage) {
        double pool = (current == null ? 0.0 : current.shadowCapitalPool()) + leakage;
        List<String> ships = current == null ? new ArrayList<>()
                : new ArrayList<>(current.rogueShipIds());
        // Ship construction takes time; at most one abstract raider can appear per day.
        if (pool >= ROGUE_SHIP_BUILD_COST) {
            pool -= ROGUE_SHIP_BUILD_COST;
            ships.add("rogue_raider_" + (ships.size() + 1));
        }
        return new ShadowSyndicate(current == null ? "syndicate_" + empire.id() : current.id(),
                current == null ? empire.name() + " Shadow Syndicate" : current.name(),
                empire.id(), current == null
                ? (empire.controlledSystemIds().isEmpty() ? "unknown"
                    : empire.controlledSystemIds().getFirst()) : current.baseSystemId(),
                pool, ships);
    }

    private Map<String, Double> dailyTradingByBody(GameState state) {
        Map<String, Double> trading = new HashMap<>();
        state.householdAccounts().forEach(account -> trading.merge(account.bodyId(),
                account.marketSpendingCredits(), Double::sum));
        Map<String, String> facilityBodies = new HashMap<>();
        for (IndustrialFacility facility : state.industrialFacilities())
            facilityBodies.put(facility.id(), facility.planetId());
        for (IndustryAccount account : state.industryAccounts()) {
            String bodyId = facilityBodies.get(account.facilityId());
            if (bodyId != null && Double.isFinite(account.inputCostsCredits())
                    && account.inputCostsCredits() > 0.0)
                trading.merge(bodyId, account.inputCostsCredits(), Double::sum);
        }
        return trading;
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
