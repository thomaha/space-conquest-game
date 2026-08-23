package com.spaceconquest.control.command;

import com.spaceconquest.engine.DiplomaticRelation;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.governance.DiplomacyProcessor;

import java.util.List;

/**
 * Command to set or transition the bilateral diplomatic relation tier between two empires.
 */
public record SetDiplomaticTierCommand(
        String empireAId,
        String empireBId,
        String newTier
) implements GameCommand {

    private static final DiplomacyProcessor diplomacyProcessor = new DiplomacyProcessor();

    @Override
    public boolean validate(GameState state) {
        if (state == null || empireAId == null || empireBId == null || newTier == null) return false;
        if (empireAId.equals(empireBId)) return false;

        boolean empireAExists = state.empires().stream().anyMatch(e -> e.id().equals(empireAId));
        boolean empireBExists = state.empires().stream().anyMatch(e -> e.id().equals(empireBId));
        return empireAExists && empireBExists;
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) return state;

        List<DiplomaticRelation> updatedRelations = diplomacyProcessor.setDiplomaticTier(
                empireAId,
                empireBId,
                newTier,
                state.diplomaticRelations()
        );

        return new GameState(
                state.turn(),
                state.status(),
                state.solarSystems(),
                state.empires(),
                state.corporations(),
                state.commercialHubs(),
                state.shadowSyndicates(),
                updatedRelations,
                state.systemGovernors(),
                state.researchProjects(),
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
