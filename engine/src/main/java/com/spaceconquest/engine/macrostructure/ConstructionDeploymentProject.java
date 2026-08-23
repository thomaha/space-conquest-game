package com.spaceconquest.engine.macrostructure;

import java.util.Map;

/**
 * Tracks the deployment and assembly progress of a macro-structure by a construction vessel.
 *
 * @param projectId               unique project identifier
 * @param constructionShipId      construction ship platform ID
 * @param targetSystemId          target solar system ID
 * @param targetCelestialId       target planet, moon or orbit coordinate ID
 * @param targetStructureType     macro-structure type (ORBITAL_STATION, SPACE_ELEVATOR, OUTPOST)
 * @param accumulatedProgressTurns turns of construction completed
 * @param requiredProgressTurns    total turns required to complete assembly
 * @param consumedMaterialsKg     manifest of materials consumed during construction
 * @param isCompleted             true if deployment is finished
 */
public record ConstructionDeploymentProject(
        String projectId,
        String constructionShipId,
        String targetSystemId,
        String targetCelestialId,
        String targetStructureType,
        double accumulatedProgressTurns,
        double requiredProgressTurns,
        Map<String, Double> consumedMaterialsKg,
        boolean isCompleted
) {
    public static final String TYPE_ORBITAL_STATION = "ORBITAL_STATION";
    public static final String TYPE_SPACE_ELEVATOR = "SPACE_ELEVATOR";
    public static final String TYPE_OUTPOST = "OUTPOST";

    public ConstructionDeploymentProject {
        if (consumedMaterialsKg == null) consumedMaterialsKg = Map.of();
    }
}
