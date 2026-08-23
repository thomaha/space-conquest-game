package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.logistics.TradeRoute;

import java.util.ArrayList;
import java.util.List;

/**
 * Command to cancel or deactivate an active automated trade route.
 */
public record CancelTradeRouteCommand(
        String routeId,
        String ownerEntityId
) implements GameCommand {

    @Override
    public boolean validate(GameState state) {
        if (routeId == null || state.tradeRoutes() == null) return false;
        return state.tradeRoutes().stream().anyMatch(r -> r.id().equals(routeId));
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) return state;

        List<TradeRoute> updated = new ArrayList<>();
        for (TradeRoute r : state.tradeRoutes()) {
            if (r.id().equals(routeId)) {
                updated.add(new TradeRoute(
                        r.id(), r.name(), r.ownerEntityId(), r.originEntityId(), r.destinationEntityId(),
                        r.materialId(), r.transferAmountPerTurnKg(), r.minSourceInventoryThresholdKg(),
                        r.maxDestinationCapacityKg(), r.assignedFreighterIds(), r.totalVolumeMovedKg(), false
                ));
            } else {
                updated.add(r);
            }
        }

        return new GameState(
                state.turn(), state.status(), state.solarSystems(), state.empires(),
                state.corporations(), state.commercialHubs(), state.shadowSyndicates(),
                state.diplomaticRelations(), state.systemGovernors(), state.researchProjects(),
                state.technologyExchangeRoutes(), state.shipDesigns(), state.fleets(),
                state.geologicalDeposits(), state.powerGrids(), state.industrialFacilities(),
                state.expansionProjects(), state.orbitalStations(), state.spaceElevators(),
                state.constructionProjects(), state.sleeperAgents(), state.espionageOperations(),
                state.pirateBases(), state.terraformingProjects(), state.megastructures(),
                state.galacticCommunity(), updated, state.fogOfWarStates()
        );
    }
}
