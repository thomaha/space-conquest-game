package com.spaceconquest.engine.governance;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.SystemEconomy;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.StationModule;

import java.util.*;

/**
 * Dynamically calculates galactic territorial control based on range of influence (I_v).
 * Implements the rule: "Any celestial body that is within the influence range of an empire 
 * is considered part of that empire."
 */
public class TerritoryProcessor {

    private final DiplomacyProcessor diplomacyProcessor;

    public TerritoryProcessor(DiplomacyProcessor diplomacyProcessor) {
        this.diplomacyProcessor = diplomacyProcessor;
    }

    /**
     * Returns updated list of empires with dynamic controlledSystemIds
     */
    public List<Empire> updateTerritorialControl(GameState state) {
        if (state == null || state.solarSystems() == null || state.empires() == null) {
            return state != null ? state.empires() : List.of();
        }

        Map<String, String> planetToSystemId = buildPlanetToSystemMap(state.solarSystems());
        Map<String, Map<String, Double>> systemInfluenceMap = new HashMap<>();

        for (SolarSystem system : state.solarSystems()) {
            Map<String, Long> empirePops = calculateSystemPopulations(system, state.systemEconomies());
            Map<String, Double> moduleFactors = calculateSystemModuleFactors(system, state.industrialFacilities(), planetToSystemId);
            Map<String, Double> hangarModifiers = calculateSystemHangarModifiers(system, state.orbitalStations());

            Map<String, Double> influences = calculateSystemInfluences(empirePops, moduleFactors, hangarModifiers);
            systemInfluenceMap.put(system.id(), influences);
        }

        Map<String, List<String>> newControlMap = assignSystemControls(systemInfluenceMap, state.empires());

        return state.empires().stream().map(emp -> new Empire(
                emp.id(), emp.name(), emp.raceId(), emp.societyStructure(),
                emp.treasuryCredits(), emp.corporateTaxRate(),
                newControlMap.getOrDefault(emp.id(), List.of()),
                emp.ministries(), emp.systemGovernorAssignments(),
                emp.unlockedTechIds(), emp.activeShipDesignIds()
        )).toList();
    }

    private Map<String, String> buildPlanetToSystemMap(List<SolarSystem> solarSystems) {
        Map<String, String> planetToSystemId = new HashMap<>();
        for (SolarSystem sys : solarSystems) {
            for (Planet p : sys.planets()) {
                planetToSystemId.put(p.id(), sys.id());
                if (p.moons() != null) {
                    for (Moon m : p.moons()) {
                        planetToSystemId.put(m.id(), sys.id());
                    }
                }
            }
        }
        return planetToSystemId;
    }

    private Map<String, Long> calculateSystemPopulations(SolarSystem system, List<SystemEconomy> economies) {
        Map<String, Long> empirePopPerSystem = new HashMap<>();
        if (system.planets() != null) {
            for (Planet p : system.planets()) {
                if (p.populations() != null) {
                    for (Population pop : p.populations()) {
                        String ownerId = getEmpireIdForSystem(system.id(), economies);
                        if (ownerId != null) empirePopPerSystem.merge(ownerId, pop.totalCount(), Long::sum);
                    }
                }
                if (p.moons() != null) {
                    for (Moon m : p.moons()) {
                        if (m.populations() != null) {
                            for (Population pop : m.populations()) {
                                String ownerId = getEmpireIdForSystem(system.id(), economies);
                                if (ownerId != null) empirePopPerSystem.merge(ownerId, pop.totalCount(), Long::sum);
                            }
                        }
                    }
                }
            }
        }
        return empirePopPerSystem;
    }

    private Map<String, Double> calculateSystemModuleFactors(SolarSystem system, List<IndustrialFacility> facilities, Map<String, String> planetToSystemId) {
        Map<String, Double> moduleFactors = new HashMap<>();
        if (facilities != null) {
            for (IndustrialFacility facility : facilities) {
                String sysId = planetToSystemId.get(facility.planetId());
                if (system.id().equals(sysId)) {
                    moduleFactors.merge(facility.ownerEntityId(), 0.1, Double::sum);
                }
            }
        }
        return moduleFactors;
    }

    private Map<String, Double> calculateSystemHangarModifiers(SolarSystem system, List<OrbitalStation> stations) {
        Map<String, Double> hangarModifiers = new HashMap<>();
        if (stations != null) {
            for (OrbitalStation station : stations) {
                if (station.systemId().equals(system.id())) {
                    double modifier = station.modules().stream()
                            .filter(m -> m.isOnline() && StationModule.TYPE_MILITARY_HANGAR.equals(m.type()))
                            .count() * 0.5;
                    hangarModifiers.merge(station.ownerEntityId(), modifier, Double::sum);
                }
            }
        }
        return hangarModifiers;
    }

    private Map<String, Double> calculateSystemInfluences(Map<String, Long> empirePops, Map<String, Double> moduleFactors, Map<String, Double> hangarModifiers) {
        Map<String, Double> influences = new HashMap<>();
        Set<String> empiresInSystem = new HashSet<>(empirePops.keySet());
        empiresInSystem.addAll(moduleFactors.keySet());
        empiresInSystem.addAll(hangarModifiers.keySet());

        for (String empireId : empiresInSystem) {
            long pop = empirePops.getOrDefault(empireId, 0L);
            double moduleFactor = 1.0 + moduleFactors.getOrDefault(empireId, 0.0);
            double hangarMod = hangarModifiers.getOrDefault(empireId, 0.0);

            double influence = diplomacyProcessor.calculateInfluenceValue(pop, moduleFactor, hangarMod);
            if (influence > 0 || (pop == 0 && hangarMod > 0)) {
                double finalInfluence = (pop == 0 && hangarMod > 0) ? hangarMod : influence;
                influences.put(empireId, finalInfluence);
            }
        }
        return influences;
    }

    private Map<String, List<String>> assignSystemControls(Map<String, Map<String, Double>> systemInfluenceMap, List<Empire> empires) {
        Map<String, List<String>> newControlMap = new HashMap<>();
        for (Empire emp : empires) {
            newControlMap.put(emp.id(), new ArrayList<>());
        }

        for (Map.Entry<String, Map<String, Double>> entry : systemInfluenceMap.entrySet()) {
            String systemId = entry.getKey();
            Map<String, Double> influences = entry.getValue();

            String topEmpire = influences.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .filter(e -> e.getValue() > 0)
                    .map(Map.Entry::getKey)
                    .orElse(null);

            if (topEmpire != null && newControlMap.containsKey(topEmpire)) {
                newControlMap.get(topEmpire).add(systemId);
            }
        }
        return newControlMap;
    }

    private String getEmpireIdForSystem(String systemId, List<SystemEconomy> economies) {
        if (economies == null || systemId == null) return null;
        for (SystemEconomy economy : economies) {
            if (systemId.equals(economy.systemId())) {
                return economy.empireId();
            }
        }
        return null;
    }
}
