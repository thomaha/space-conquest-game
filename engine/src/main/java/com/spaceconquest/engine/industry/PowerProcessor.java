package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.Moon;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.SolarSystem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Resolves turn-based planetary electrical grid balance sheets, battery buffering and brownout load shedding.
 */
public class PowerProcessor {
    private static final double HOURS_PER_DAY = 24.0;

    public record DayResult(List<PowerGridState> grids, Map<String, Integer> poweredWorkers) {}

    /**
     * Balances the planetary power grid for one turn cycle.
     */
    public PowerGridState balanceGrid(
            String entityId,
            double totalGenerationKw,
            double totalDemandKw,
            double currentBatteryKwh,
            double maxBatteryCapacityKwh
    ) {
        double gen = Math.max(0.0, totalGenerationKw);
        double dem = Math.max(0.0, totalDemandKw);
        double netBalance = gen - dem;
        double maxCap = Math.max(0.0, maxBatteryCapacityKwh);
        double stored = Math.max(0.0, currentBatteryKwh);
        boolean brownout = false;

        if (netBalance >= 0.0) {
            stored = Math.min(maxCap, stored + netBalance * HOURS_PER_DAY);
            brownout = false;
        } else {
            double deficit = -netBalance * HOURS_PER_DAY;
            if (stored >= deficit) {
                stored -= deficit;
                brownout = false;
            } else {
                stored = 0.0;
                brownout = true;
            }
        }

        return new PowerGridState(
                entityId,
                gen,
                dem,
                netBalance,
                maxCap,
                stored,
                brownout
        );
    }

    /** Balances generated plant output against local demand and caps industrial shifts during shortages. */
    public DayResult balanceDay(GameState state, Map<String, Double> generationKw,
                                Map<String, Integer> paidWorkers) {
        Map<String, Integer> powered = new HashMap<>(paidWorkers);
        List<PowerGridState> grids = new ArrayList<>();
        Set<String> bodies = new HashSet<>(generationKw.keySet());
        for (PowerGridState grid : state.powerGrids()) bodies.add(grid.entityId());
        for (IndustrialFacility facility : state.industrialFacilities()) {
            if (IndustryRecipeCatalog.find(facility.applicationId()) != null) bodies.add(facility.planetId());
        }
        for (String bodyId : bodies) {
            PowerGridState prior = state.powerGrids().stream()
                    .filter(grid -> grid.entityId().equals(bodyId)).findFirst().orElse(null);
            double baseKw = baselineDemandKw(state, bodyId,
                    prior == null ? 0.0 : prior.totalDemandKw());
            double generated = generationKw.getOrDefault(bodyId, 0.0);
            double battery = prior == null ? 0.0 : prior.currentStoredKwh();
            double capacity = prior == null ? 0.0 : prior.batteryCapacityKwh();
            double availableKw = Math.max(0.0, generated + battery / HOURS_PER_DAY - baseKw);
            double demandKw = baseKw;
            List<IndustrialFacility> consumers = state.industrialFacilities().stream()
                    .filter(facility -> bodyId.equals(facility.planetId()))
                    .filter(facility -> IndustryRecipeCatalog.find(facility.applicationId()) != null)
                    .sorted(Comparator.comparingInt(this::powerPriority)).toList();
            for (IndustrialFacility facility : consumers) {
                int workers = paidWorkers.getOrDefault(facility.id(), 0);
                IndustryRecipeCatalog.Recipe recipe = IndustryRecipeCatalog.find(facility.applicationId());
                if (workers <= 0 || recipe == null) continue;
                double requested = requestedIndustryKw(facility, workers);
                demandKw += requested;
                double fraction = requested <= 0.0 ? 1.0 : Math.min(1.0, availableKw / requested);
                powered.put(facility.id(), (int) Math.floor(workers * fraction));
                availableKw = Math.max(0.0, availableKw - requested * fraction);
            }
            grids.add(balanceGrid(bodyId, generated, demandKw, battery, capacity));
        }
        return new DayResult(List.copyOf(grids), Map.copyOf(powered));
    }

    private int powerPriority(IndustrialFacility facility) {
        return "industrial_soil_cultivation".equals(facility.applicationId()) ? 0 : 1;
    }

    public static double requestedIndustryKw(IndustrialFacility facility, int workers) {
        IndustryRecipeCatalog.Recipe recipe = IndustryRecipeCatalog.find(facility.applicationId());
        return recipe == null || workers <= 0 ? 0.0 : recipe.powerDrawKw()
                * Math.max(1, facility.tier())
                * Math.min(1.0, workers / (double) recipe.workersPerBatch());
    }

    private double baselineDemandKw(GameState state, String bodyId, double fallback) {
        for (SolarSystem system : state.solarSystems()) {
            Empire owner = state.empires().stream()
                    .filter(empire -> empire.controlledSystemIds().contains(system.id()))
                    .findFirst().orElse(null);
            for (Planet planet : system.planets()) {
                if (planet.id().equals(bodyId)) {
                    return PowerBillingProcessor.householdDemandKwh(owner, people(planet.populations())) / HOURS_PER_DAY;
                }
                for (Moon moon : planet.moons()) {
                    if (moon.id().equals(bodyId)) {
                        return PowerBillingProcessor.householdDemandKwh(owner, people(moon.populations())) / HOURS_PER_DAY;
                    }
                }
            }
        }
        return Math.max(0.0, fallback);
    }

    private long people(List<Population> populations) {
        return populations.stream().mapToLong(Population::totalCount).sum();
    }

    /**
     * Executes emergency load-shedding during power deficit brownouts.
     * Heavy metallurgy and manufacturing facilities are throttled to 0 output while protecting life support and farming.
     */
    public List<IndustrialFacility> applyEmergencyLoadShedding(
            List<IndustrialFacility> facilities,
            boolean isBrownoutActive
    ) {
        if (facilities == null || !isBrownoutActive) {
            return facilities != null ? facilities : List.of();
        }

        List<IndustrialFacility> processed = new ArrayList<>();
        for (IndustrialFacility facility : facilities) {
            String appId = facility.applicationId().toLowerCase();
            boolean isHeavyIndustry = appId.contains("refin") || appId.contains("foundry")
                    || appId.contains("metallurgy") || appId.contains("nanotube") || appId.contains("heavy");

            if (isHeavyIndustry) {
                // Shed load by idling workers / pausing throughput
                processed.add(new IndustrialFacility(
                        facility.id(),
                        facility.planetId(),
                        facility.applicationId(),
                        facility.ownerEntityId(),
                        facility.ownershipType(),
                        facility.tier(),
                        0, // Idled during brownout
                        facility.workerProfessionId(),
                        facility.isUndergoingExpansion(),
                        facility.expansionProgress()
                ));
            } else {
                processed.add(facility);
            }
        }
        return processed;
    }
}
