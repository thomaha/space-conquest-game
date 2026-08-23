package com.spaceconquest.engine.terraforming;

import java.util.Map;

/**
 * Tracks an active environmental engineering or biological seeding project on a planet.
 *
 * @param id                  unique project identifier
 * @param planetId            target celestial body ID
 * @param ownerEmpireId       empire sponsoring the project
 * @param projectType         geoengineering classification
 * @param accumulatedProgress accumulated progress in standard work turns
 * @param requiredProgress    total required turns for full effect
 * @param targetPressureAtm   desired surface pressure target
 * @param targetTemperatureK desired mean surface equilibrium temperature
 * @param targetGasRatios     target gas concentrations
 * @param isCompleted         true if project milestone has completed
 */
public record GeoengineeringProject(
        String id,
        String planetId,
        String ownerEmpireId,
        String projectType,
        double accumulatedProgress,
        double requiredProgress,
        double targetPressureAtm,
        double targetTemperatureK,
        Map<String, Double> targetGasRatios,
        boolean isCompleted
) {
    public static final String TYPE_SOLAR_MIRROR = "SOLAR_MIRROR";
    public static final String TYPE_SOLAR_SHADE = "SOLAR_SHADE";
    public static final String TYPE_GREENHOUSE_FACTORY = "GREENHOUSE_FACTORY";
    public static final String TYPE_CARBON_SEQUESTRATION = "CARBON_SEQUESTRATION";
    public static final String TYPE_MAGNETIC_FIELD_GENERATOR = "MAGNETIC_FIELD_GENERATOR";
    public static final String TYPE_CYANOBACTERIA_SEEDING = "CYANOBACTERIA_SEEDING";
    public static final String TYPE_EXTREMOPHILE_ALGAE_SEEDING = "EXTREMOPHILE_ALGAE_SEEDING";
    public static final String TYPE_LICHEN_SOIL_SEEDING = "LICHEN_SOIL_SEEDING";

    public GeoengineeringProject {
        if (targetGasRatios == null) targetGasRatios = Map.of();
    }
}
