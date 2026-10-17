package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.ConstructionMaterialCatalog;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import com.spaceconquest.engine.macrostructure.ConstructionDeploymentProject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Queues a space elevator whose materials and work are consumed on daily ticks. */
public record BuildSpaceElevatorCommand(String planetId, String ownerEntityId,
                                        double throughputCapacityKgPerTurn) implements GameCommand {
    @Override
    public boolean validate(GameState state) {
        if (state == null || planetId == null || ownerEntityId == null) return false;
        String systemId = ConstructionMaterials.systemForBody(state, planetId);
        if (systemId == null || ConstructionMaterials.bodyForSystem(state, systemId, planetId) == null)
            return false;
        boolean owned = state.empires().stream().anyMatch(empire -> empire.id().equals(ownerEntityId)
                && empire.controlledSystemIds().contains(systemId));
        var service = com.spaceconquest.engine.logistics.InfrastructureService.find(ConstructionDeploymentProject.TYPE_SPACE_ELEVATOR);
        var empire = state.empires().stream().filter(e -> e.id().equals(ownerEntityId)).findFirst().orElse(null);
        return owned && service != null && com.spaceconquest.engine.technology.ApplicationProduction.isAvailable(empire, service.applicationId())
                && Double.isFinite(throughputCapacityKgPerTurn) && throughputCapacityKgPerTurn >= 0
                && throughputCapacityKgPerTurn <= service.capability().capacityKgPerTier()
                && state.spaceElevators().stream().noneMatch(e -> planetId.equals(e.planetId()))
                && state.constructionProjects().stream().noneMatch(project ->
                planetId.equals(project.targetCelestialId())
                        && ConstructionDeploymentProject.TYPE_SPACE_ELEVATOR.equals(
                        project.targetStructureType()));
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) return state;
        var capability = com.spaceconquest.engine.logistics.InfrastructureService.find(ConstructionDeploymentProject.TYPE_SPACE_ELEVATOR).capability();
        ConstructionDeploymentProject project = new ConstructionDeploymentProject(
                "elevator_" + UUID.randomUUID(), "",
                ConstructionMaterials.systemForBody(state, planetId), planetId,
                ConstructionDeploymentProject.TYPE_SPACE_ELEVATOR,
                0.0, capability.infrastructure().constructionWorkPerTier(), ownerEntityId, ConstructionMaterialCatalog.spaceElevator(),
                Map.of(), null, null, null, null, 0, "steel", 0.0,
                throughputCapacityKgPerTurn > 0.0 ? throughputCapacityKgPerTurn : capability.capacityKgPerTier(),
                false);
        List<ConstructionDeploymentProject> projects = new ArrayList<>(state.constructionProjects());
        projects.add(project);
        return state.toBuilder().constructionProjects(projects).build();
    }
}
