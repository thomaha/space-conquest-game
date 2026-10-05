package com.spaceconquest.engine.macrostructure;

import java.util.Map;

/**
 * Tracks daily assembly of orbital stations, elevators and station modules.
 *
 * @param projectId               unique project identifier
 * @param constructionShipId      construction ship platform ID
 * @param targetSystemId          target solar system ID
 * @param targetCelestialId       target planet, moon or orbit coordinate ID
 * @param targetStructureType     project type (ORBITAL_STATION, SPACE_ELEVATOR, STATION_MODULE)
 * @param accumulatedProgressTurns turns of construction completed
 * @param requiredProgressTurns    total turns required to complete assembly
 * @param requiredMaterialsKg     physical construction bill
 * @param consumedMaterialsKg     materials bought and incorporated during construction
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
        String ownerEntityId,
        Map<String, Double> requiredMaterialsKg,
        Map<String, Double> consumedMaterialsKg,
        String structureName,
        String ownershipType,
        String targetStationId,
        StationModule plannedModule,
        int totalSlots,
        String armorMaterialId,
        double armorThicknessCm,
        double throughputCapacityKgPerTurn,
        boolean isCompleted,
        Double parkingAltitudeKm
) {
    public static final String TYPE_ORBITAL_STATION = "ORBITAL_STATION";
    public static final String TYPE_SPACE_ELEVATOR = "SPACE_ELEVATOR";
    public static final String TYPE_OUTPOST = "OUTPOST";
    public static final String TYPE_STATION_MODULE = "STATION_MODULE";

    public ConstructionDeploymentProject {
        if (parkingAltitudeKm != null && (!Double.isFinite(parkingAltitudeKm) || parkingAltitudeKm <= 0))
            throw new IllegalArgumentException("Parking altitude must be finite and positive");
        requiredMaterialsKg = requiredMaterialsKg == null ? Map.of() : Map.copyOf(requiredMaterialsKg);
        consumedMaterialsKg = consumedMaterialsKg == null ? Map.of() : Map.copyOf(consumedMaterialsKg);
    }

    public ConstructionDeploymentProject(String projectId, String constructionShipId, String targetSystemId,
                                         String targetCelestialId, String targetStructureType, double accumulatedProgressTurns,
                                         double requiredProgressTurns, String ownerEntityId, Map<String, Double> requiredMaterialsKg,
                                         Map<String, Double> consumedMaterialsKg, String structureName, String ownershipType,
                                         String targetStationId, StationModule plannedModule, int totalSlots, String armorMaterialId,
                                         double armorThicknessCm, double throughputCapacityKgPerTurn, boolean isCompleted) {
        this(projectId, constructionShipId, targetSystemId, targetCelestialId, targetStructureType, accumulatedProgressTurns,
                requiredProgressTurns, ownerEntityId, requiredMaterialsKg, consumedMaterialsKg, structureName, ownershipType,
                targetStationId, plannedModule, totalSlots, armorMaterialId, armorThicknessCm, throughputCapacityKgPerTurn, isCompleted, null);
    }

    public ConstructionDeploymentProject(String projectId, String constructionShipId,
                                         String targetSystemId, String targetCelestialId,
                                         String targetStructureType, double accumulatedProgressTurns,
                                         double requiredProgressTurns, Map<String, Double> consumedMaterialsKg,
                                         boolean isCompleted) {
        this(projectId, constructionShipId, targetSystemId, targetCelestialId,
                targetStructureType, accumulatedProgressTurns, requiredProgressTurns,
                null, Map.of(), consumedMaterialsKg, null, OrbitalStation.OWNERSHIP_PUBLIC_STATE,
                null, null, 20, "steel", 5.0,
                100_000.0, isCompleted);
    }
}
