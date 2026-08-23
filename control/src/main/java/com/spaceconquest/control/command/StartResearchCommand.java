package com.spaceconquest.control.command;

import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.technology.ResearchProject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Command to start or update a scientific research project for an empire.
 */
public record StartResearchCommand(
        String empireId,
        String targetTechOrAppId,
        boolean isApplication,
        int assignedScientists
) implements GameCommand {

    @Override
    public boolean validate(GameState state) {
        if (state == null || empireId == null || targetTechOrAppId == null) {
            return false;
        }
        if (assignedScientists <= 0) {
            return false;
        }
        return state.empires().stream().anyMatch(e -> e.id().equals(empireId));
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        List<ResearchProject> updatedProjects = new ArrayList<>();
        boolean replaced = false;

        for (ResearchProject project : state.researchProjects()) {
            if (project.empireId().equals(empireId) && project.targetTechOrAppId().equals(targetTechOrAppId)) {
                updatedProjects.add(new ResearchProject(
                        project.id(),
                        empireId,
                        targetTechOrAppId,
                        isApplication,
                        project.accumulatedPoints(),
                        project.requiredPoints(),
                        assignedScientists,
                        project.speedModifier()
                ));
                replaced = true;
            } else {
                updatedProjects.add(project);
            }
        }

        if (!replaced) {
            double requiredPoints = isApplication ? 500.0 : 1000.0;
            updatedProjects.add(new ResearchProject(
                    "res_" + UUID.randomUUID().toString().substring(0, 8),
                    empireId,
                    targetTechOrAppId,
                    isApplication,
                    0.0,
                    requiredPoints,
                    assignedScientists,
                    1.0
            ));
        }

        return new GameState(
                state.turn(),
                state.status(),
                state.solarSystems(),
                state.empires(),
                state.corporations(),
                state.commercialHubs(),
                state.shadowSyndicates(),
                state.diplomaticRelations(),
                state.systemGovernors(),
                updatedProjects,
                state.technologyExchangeRoutes(),
                state.shipDesigns(),
                state.fleets(),
                state.geologicalDeposits(),
                state.powerGrids(),
                state.industrialFacilities(),
                state.expansionProjects(),
                state.orbitalStations(),
                state.spaceElevators(),
                state.constructionProjects(),
                state.sleeperAgents(),
                state.espionageOperations(),
                state.pirateBases(),
                state.terraformingProjects(),
                state.megastructures(),
                state.galacticCommunity(),
                state.tradeRoutes(),
                state.fogOfWarStates(),
                state.systemEconomies()
        );
    }
}
