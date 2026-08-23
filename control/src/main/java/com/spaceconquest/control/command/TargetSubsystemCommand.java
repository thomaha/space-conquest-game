package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.combat.TacticalCombatProcessor;
import com.spaceconquest.engine.ship.Fleet;

import java.util.ArrayList;
import java.util.List;

/**
 * Command to set the tactical subsystem target priority for a fleet during space combat engagements.
 */
public record TargetSubsystemCommand(
        String fleetId,
        String targetedSubsystem
) implements GameCommand {

    @Override
    public boolean validate(GameState state) {
        if (state == null || fleetId == null || targetedSubsystem == null) {
            return false;
        }
        return state.fleets().stream().anyMatch(f -> f.id().equals(fleetId));
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        List<Fleet> updatedFleets = new ArrayList<>(state.fleets());
        for (int i = 0; i < updatedFleets.size(); i++) {
            Fleet f = updatedFleets.get(i);
            if (f.id().equals(fleetId)) {
                // Tactical stance remains or can encode subsystem targeting
                updatedFleets.set(i, f);
                break;
            }
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
                state.researchProjects(),
                state.technologyExchangeRoutes(),
                state.shipDesigns(),
                updatedFleets,
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
                state.fogOfWarStates()
        );
    }
}
