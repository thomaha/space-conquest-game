package com.spaceconquest.control.command;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.MarketOrder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Command to distribute consumer goods to a colony to elevate standards of living.
 */
public record DistributeConsumerGoodsCommand(
        String planetId,
        double consumerGoodsAmountKg
) implements GameCommand {

    @Override
    public boolean validate(GameState state) {
        return state != null && planetId != null && consumerGoodsAmountKg > 0.0;
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        List<CommercialHub> updatedHubs = state.commercialHubs().stream().map(hub -> {
            if (hub.entityId().equals(planetId)) {
                Map<String, MarketOrder> orders = new HashMap<>(hub.activeOrders());
                MarketOrder existing = orders.get("consumer_goods");
                if (existing != null) {
                    orders.put("consumer_goods", new MarketOrder(
                            existing.resourceId(),
                            existing.supplyKg() + consumerGoodsAmountKg,
                            existing.demandKg(),
                            Math.max(1.0, existing.pricePerKg() * 0.90),
                            Math.max(0.0, existing.shortcomingScore() - 20.0)
                    ));
                } else {
                    orders.put("consumer_goods", new MarketOrder(
                            "consumer_goods", consumerGoodsAmountKg, consumerGoodsAmountKg, 10.0, 0.0
                    ));
                }
                return new CommercialHub(
                        hub.id(), hub.entityId(), hub.transactionTariffRate(),
                        hub.storageCapacityKg(), hub.currentStoredWeightKg() + consumerGoodsAmountKg,
                        hub.logisticsRangeUnits(), orders
                );
            }
            return hub;
        }).toList();

        return new GameState(
                state.turn(), state.status(), state.solarSystems(), state.empires(),
                state.corporations(), updatedHubs, state.shadowSyndicates(),
                state.diplomaticRelations(), state.systemGovernors(), state.researchProjects(),
                state.technologyExchangeRoutes(), state.shipDesigns(), state.fleets(),
                state.geologicalDeposits(), state.powerGrids(), state.industrialFacilities(),
                state.expansionProjects(), state.orbitalStations(), state.spaceElevators(),
                state.constructionProjects(), state.sleeperAgents(), state.espionageOperations(),
                state.pirateBases()
        );
    }
}
