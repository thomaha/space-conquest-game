package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.macrostructure.ConstructionDeploymentProject;

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
        return state != null && targetSystemId != null && targetCelestialId != null && targetStructureType != null;
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        String projId = "proj_" + UUID.randomUUID().toString().substring(0, 8);
        ConstructionDeploymentProject project = new ConstructionDeploymentProject(
                projId,
                constructionShipId != null ? constructionShipId : "ship_const_01",
                targetSystemId,
                targetCelestialId,
                targetStructureType,
                0.0,
                requiredTurns > 0.0 ? requiredTurns : 3.0,
                Map.of("steel", 5000.0),
                false
        );

        List<ConstructionDeploymentProject> updated = new ArrayList<>(state.constructionProjects());
        updated.add(project);

        return state.toBuilder()
                .constructionProjects(updated)
                .build();
    }
}
