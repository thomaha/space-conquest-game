package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.ConstructionMaterialCatalog;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import com.spaceconquest.engine.macrostructure.ConstructionDeploymentProject;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.ship.OrbitalBody;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Queues an orbital station whose materials and work are consumed on daily ticks. */
public record BuildOrbitalStationCommand(
        String name, String systemId, String planetOrbitId, String ownerEntityId,
        String ownershipType, int totalSlots, String armorMaterialId,
        double armorThicknessCm, Double parkingAltitudeKm
) implements GameCommand {
    public BuildOrbitalStationCommand(String name, String systemId, String planetOrbitId, String ownerEntityId,
                                     String ownershipType, int totalSlots, String armorMaterialId, double armorThicknessCm) {
        this(name, systemId, planetOrbitId, ownerEntityId, ownershipType, totalSlots, armorMaterialId, armorThicknessCm, 500.0);
    }
    public BuildOrbitalStationCommand(String ownerEntityId, String systemId, String name) {
        this(name, systemId, "low_orbit", ownerEntityId,
                OrbitalStation.OWNERSHIP_PUBLIC_STATE, 40, "steel", 5.0);
    }

    @Override
    public boolean validate(GameState state) {
        if (state == null || systemId == null || ownerEntityId == null || totalSlots <= 0) return false;
        if (name == null || name.isBlank() || name.length() > 100) return false;
        boolean stateOwned = state.empires().stream().anyMatch(empire -> empire.id().equals(ownerEntityId)
                && empire.controlledSystemIds().contains(systemId)
                && empire.unlockedTechIds().contains("space_stations")
                && empire.unlockedTechIds().contains("rocketry"));
        boolean corporateOwned = state.corporations().stream().anyMatch(corporation ->
                ownerEntityId.equals(corporation.id()) && state.empires().stream()
                        .anyMatch(empire -> corporation.empireId().equals(empire.id())
                                && empire.controlledSystemIds().contains(systemId)
                                && empire.unlockedTechIds().contains("space_stations")
                                && empire.unlockedTechIds().contains("rocketry")));
        boolean ownershipMatches = corporateOwned
                ? OrbitalStation.OWNERSHIP_PRIVATE_CORPORATE.equals(ownershipType)
                : ownershipType == null || OrbitalStation.OWNERSHIP_PUBLIC_STATE.equals(ownershipType)
                || OrbitalStation.OWNERSHIP_HIVE_GRID.equals(ownershipType);
        return (stateOwned || corporateOwned) && ownershipMatches
                && resolvedOrbit(state) != null && validAltitude(state);
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) return state;
        String armor = armorMaterialId == null ? "steel" : armorMaterialId;
        String orbit = resolvedOrbit(state);
        String projectId = "station_" + UUID.randomUUID();
        ConstructionDeploymentProject project = new ConstructionDeploymentProject(projectId,
                "", systemId, orbit,
                ConstructionDeploymentProject.TYPE_ORBITAL_STATION,
                0.0, 5.0, ownerEntityId,
                ConstructionMaterialCatalog.orbitalStation(armor, totalSlots), Map.of(),
                name, ownershipType == null ? OrbitalStation.OWNERSHIP_PUBLIC_STATE : ownershipType,
                null, null, totalSlots, armor,
                armorThicknessCm > 0.0 ? armorThicknessCm : 5.0,
                0.0, false, parkingAltitudeKm);
        List<ConstructionDeploymentProject> projects = new ArrayList<>(state.constructionProjects());
        projects.add(project);
        return state.toBuilder().constructionProjects(projects).build();
    }

    private String resolvedOrbit(GameState state) {
        var system = state.solarSystems().stream().filter(item -> systemId.equals(item.id()))
                .findFirst().orElse(null);
        if (system == null) return null;
        if (planetOrbitId == null || "low_orbit".equals(planetOrbitId))
            return system.planets().isEmpty() ? systemId : system.planets().getFirst().id();
        return systemId.equals(planetOrbitId)
                || ConstructionMaterials.systemForBody(state, planetOrbitId) != null
                && systemId.equals(ConstructionMaterials.systemForBody(state, planetOrbitId))
                ? planetOrbitId : null;
    }

    private boolean validAltitude(GameState state) {
        if (parkingAltitudeKm == null || !Double.isFinite(parkingAltitudeKm) || parkingAltitudeKm <= 0) return false;
        var system = state.solarSystems().stream().filter(item -> item.id().equals(systemId)).findFirst().orElse(null);
        if (system == null) return false;
        String bodyId = resolvedOrbit(state);
        var planet = OrbitalBody.find(state, systemId, bodyId);
        if (planet == null) return parkingAltitudeKm == 500;
        double sphereKm = planet.sphereKm(system);
        return Double.isFinite(sphereKm) && sphereKm > planet.diameter() / 2 + parkingAltitudeKm;
    }
}
