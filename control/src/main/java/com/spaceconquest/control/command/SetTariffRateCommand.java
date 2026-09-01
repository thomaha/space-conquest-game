package com.spaceconquest.control.command;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.economy.SystemEconomy;

import java.util.ArrayList;
import java.util.List;

/**
 * Command to adjust the transaction tariff rate of a commercial hub.
 */
public record SetTariffRateCommand(
        String empireId,
        String hubId,
        double newRate
) implements GameCommand {

    @Override
    public boolean validate(GameState state) {
        if (state == null || empireId == null || hubId == null) return false;
        if (newRate < 0.0 || newRate > 1.0) return false;

        boolean empireExists = state.empires().stream().anyMatch(e -> e.id().equals(empireId));
        boolean hubExists = state.commercialHubs().stream().anyMatch(h -> h.id().equals(hubId));
        return empireExists && hubExists;
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) return state;

        List<CommercialHub> updatedHubs = new ArrayList<>();
        for (CommercialHub hub : state.commercialHubs()) {
            if (hub.id().equals(hubId)) {
                updatedHubs.add(new CommercialHub(
                        hub.id(),
                        hub.entityId(),
                        newRate,
                        hub.storageCapacityKg(),
                        hub.currentStoredWeightKg(),
                        hub.logisticsRangeUnits(),
                        hub.activeOrders()
                ));
            } else {
                updatedHubs.add(hub);
            }
        }

        return new GameState(
                state.turn(),
                state.status(),
                state.solarSystems(),
                state.empires(),
                state.corporations(),
                updatedHubs,
                state.shadowSyndicates(),
                state.diplomaticRelations(),
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
                state.systemEconomies(),
                state.courierShips()
        );
    }
}
