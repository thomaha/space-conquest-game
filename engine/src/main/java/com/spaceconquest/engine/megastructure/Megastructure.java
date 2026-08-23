package com.spaceconquest.engine.megastructure;

import java.util.Map;

/**
 * Represents a grand stellar engineering or deep-space megastructure.
 *
 * @param id                             unique megastructure identifier
 * @param name                           display name
 * @param type                           megastructure classification (DYSON_SWARM, DYSON_SPHERE, STAR_LIFTER, RINGWORLD, ORBITAL_HABITAT, HYPERLANE_GATEWAY)
 * @param systemId                       host solar system identifier
 * @param targetCelestialId              targeted star, planet or deep-space coordinate ID
 * @param ownerEmpireId                  empire owning and operating the megastructure
 * @param currentStage                   active completed construction stage
 * @param totalStages                    total stages required for complete construction
 * @param currentStageProgress           turn progress invested into current stage
 * @param requiredStageProgress          turns required to complete current stage
 * @param isOperational                  true if the current stage is functional and providing benefits
 * @param energyYieldKw                  electrical power generation supplied to imperial power grid
 * @param materialHarvestYieldKgPerTurn  harvested raw or refined materials produced per turn
 * @param habitableCapacity              maximum citizen population supported (for habitats and ringworlds)
 */
public record Megastructure(
        String id,
        String name,
        String type,
        String systemId,
        String targetCelestialId,
        String ownerEmpireId,
        int currentStage,
        int totalStages,
        double currentStageProgress,
        double requiredStageProgress,
        boolean isOperational,
        double energyYieldKw,
        Map<String, Double> materialHarvestYieldKgPerTurn,
        int habitableCapacity
) {
    public static final String TYPE_DYSON_SWARM = "DYSON_SWARM";
    public static final String TYPE_DYSON_SPHERE = "DYSON_SPHERE";
    public static final String TYPE_STAR_LIFTER = "STAR_LIFTER";
    public static final String TYPE_RINGWORLD = "RINGWORLD";
    public static final String TYPE_ORBITAL_HABITAT = "ORBITAL_HABITAT";
    public static final String TYPE_HYPERLANE_GATEWAY = "HYPERLANE_GATEWAY";

    public Megastructure {
        if (materialHarvestYieldKgPerTurn == null) materialHarvestYieldKgPerTurn = Map.of();
    }

    public boolean isFullyConstructed() {
        return currentStage >= totalStages;
    }
}
