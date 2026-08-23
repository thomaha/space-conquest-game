package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.logistics.TradeRoute;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Command to establish an automated cargo trade route between commercial hubs.
 */
public record CreateTradeRouteCommand(
        String ownerEntityId,
        String name,
        String originEntityId,
        String destinationEntityId,
        String materialId,
        double transferAmountPerTurnKg,
        double minSourceThresholdKg,
        double maxDestinationCapacityKg,
        List<String> assignedFreighterIds
) implements GameCommand {

    @Override
    public boolean validate(GameState state) {
        if (ownerEntityId == null || originEntityId == null || destinationEntityId == null || materialId == null) {
            return false;
        }
        if (originEntityId.equals(destinationEntityId)) {
            return false;
        }
        return transferAmountPerTurnKg > 0;
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) return state;

        String routeId = "route_" + UUID.randomUUID().toString().substring(0, 8);
        String routeName = name != null && !name.isEmpty() ? name : "Automated " + materialId + " Route";

        TradeRoute newRoute = new TradeRoute(
                routeId, routeName, ownerEntityId, originEntityId, destinationEntityId,
                materialId, transferAmountPerTurnKg, minSourceThresholdKg,
                maxDestinationCapacityKg > 0 ? maxDestinationCapacityKg : 50000.0,
                assignedFreighterIds != null ? assignedFreighterIds : List.of(),
                0.0, true
        );

        List<TradeRoute> updated = new ArrayList<>(state.tradeRoutes());
        updated.add(newRoute);

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
