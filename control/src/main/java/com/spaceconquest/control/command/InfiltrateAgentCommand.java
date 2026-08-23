package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.espionage.SleeperAgent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Command to infiltrate a sleeper agent into a target colony, station or corporation.
 */
public record InfiltrateAgentCommand(
        String ownerEmpireId,
        String targetEntityId,
        String coverProfessionId,
        int infiltrationLevel
) implements GameCommand {

    @Override
    public boolean validate(GameState state) {
        return state != null && ownerEmpireId != null && targetEntityId != null;
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        String agentId = "agent_" + UUID.randomUUID().toString().substring(0, 8);
        SleeperAgent agent = new SleeperAgent(
                agentId,
                ownerEmpireId,
                targetEntityId,
                coverProfessionId != null ? coverProfessionId : "bureaucrat",
                infiltrationLevel > 0 ? infiltrationLevel : 1,
                false
        );

        List<SleeperAgent> updated = new ArrayList<>(state.sleeperAgents());
        updated.add(agent);

        return new GameState(
                state.turn(), state.status(), state.solarSystems(), state.empires(),
                state.corporations(), state.commercialHubs(), state.shadowSyndicates(),
                state.diplomaticRelations(), state.systemGovernors(), state.researchProjects(),
                state.technologyExchangeRoutes(), state.shipDesigns(), state.fleets(),
                state.geologicalDeposits(), state.powerGrids(), state.industrialFacilities(),
                state.expansionProjects(), state.orbitalStations(), state.spaceElevators(),
                state.constructionProjects(), updated, state.espionageOperations(),
                state.pirateBases()
        );
    }
}
