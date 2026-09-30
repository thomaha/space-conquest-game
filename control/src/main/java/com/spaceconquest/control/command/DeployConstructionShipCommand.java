package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.ConstructionMaterialCatalog;
import com.spaceconquest.engine.macrostructure.ConstructionDeploymentProject;
import com.spaceconquest.engine.ship.FleetPositioning;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Command to deploy a construction vessel to assemble a macro-structure at target coordinates.
 */
public record DeployConstructionShipCommand(
        String constructionShipId,
        String targetSystemId,
        String targetCelestialId,
        String targetStructureType,
        double requiredTurns
) implements GameCommand {

    @Override
    public boolean validate(GameState state) {
        if (state == null || constructionShipId == null || targetSystemId == null
                || targetCelestialId == null || targetStructureType == null) return false;
        String owner = owner(state);
        boolean present = state.fleets().stream().anyMatch(fleet ->
                FleetPositioning.atOrbitalSite(state, fleet, targetSystemId,
                        targetCelestialId) && fleet.ships().stream()
                        .anyMatch(ship -> constructionShipId.equals(ship.id())));
        boolean duplicateElevator = ConstructionDeploymentProject.TYPE_SPACE_ELEVATOR
                .equals(targetStructureType) && (state.spaceElevators().stream()
                .anyMatch(elevator -> targetCelestialId.equals(elevator.planetId()))
                || state.constructionProjects().stream().anyMatch(project ->
                targetCelestialId.equals(project.targetCelestialId())
                        && ConstructionDeploymentProject.TYPE_SPACE_ELEVATOR.equals(
                        project.targetStructureType())));
        return owner != null && present && !duplicateElevator
                && (ConstructionDeploymentProject.TYPE_ORBITAL_STATION
                .equals(targetStructureType) || ConstructionDeploymentProject.TYPE_SPACE_ELEVATOR
                .equals(targetStructureType));
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        String projId = "proj_" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Double> bill = ConstructionDeploymentProject.TYPE_SPACE_ELEVATOR
                .equals(targetStructureType) ? ConstructionMaterialCatalog.spaceElevator()
                : ConstructionMaterialCatalog.orbitalStation("steel", 20);
        ConstructionDeploymentProject project = new ConstructionDeploymentProject(
                projId,
                constructionShipId,
                targetSystemId,
                targetCelestialId,
                targetStructureType,
                0.0,
                requiredTurns > 0.0 ? requiredTurns : 3.0,
                owner(state), bill, Map.of(), null,
                com.spaceconquest.engine.macrostructure.OrbitalStation.OWNERSHIP_PUBLIC_STATE,
                null, null, 20, "steel", 5.0,
                100_000.0, false
        );

        List<ConstructionDeploymentProject> updated = new ArrayList<>(state.constructionProjects());
        updated.add(project);

        return state.toBuilder()
                .constructionProjects(updated)
                .build();
    }

    private String owner(GameState state) {
        return state.fleets().stream().filter(fleet -> fleet.ships().stream()
                .anyMatch(ship -> constructionShipId.equals(ship.id())))
                .map(fleet -> fleet.ownerEntityId()).findFirst().orElse(null);
    }
}
