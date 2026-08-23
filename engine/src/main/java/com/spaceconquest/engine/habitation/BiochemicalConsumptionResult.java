package com.spaceconquest.engine.habitation;

import java.util.Map;

/**
 * Encapsulates the results of per-turn biochemical nutrient and life support consumption for a population.
 *
 * @param raceId            species identifier
 * @param consumedResources map of resource IDs to amounts consumed in kg or kWh
 * @param isDeficit         true if required nutrients were unavailable in storage
 * @param missingResource   identifier of missing critical nutrient if in deficit
 * @param happinessModifier net modifier to local civilian happiness (-0.5 to +0.3)
 * @param crimeRateModifier net modifier to local crime generation (-0.1 to +0.25)
 * @param growthModifier    multiplier applied to birth rate (0.0 to 1.5)
 */
public record BiochemicalConsumptionResult(
        String raceId,
        Map<String, Double> consumedResources,
        boolean isDeficit,
        String missingResource,
        double happinessModifier,
        double crimeRateModifier,
        double growthModifier
) {
    public BiochemicalConsumptionResult {
        if (consumedResources == null) consumedResources = Map.of();
    }
}
