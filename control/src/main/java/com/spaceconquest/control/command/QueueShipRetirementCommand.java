package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Revalidates and reserves an actual owned ship for ordinary paid shared-yard dismantling. */
public record QueueShipRetirementCommand(String ownerId, String shipId) implements GameCommand {
    @Override public boolean validate(GameState state) { return ShipRetirementQuote.inspect(state, ownerId, shipId).ready(); }
    @Override public GameState apply(GameState state) {
        var quote = ShipRetirementQuote.inspect(state, ownerId, shipId); if (!quote.ready()) return state;
        var fleet = ShipRetirementPlan.fleet(state, shipId);
        var contract = ownerId.equals(quote.providerId()) ? null : new ShipyardContract(quote.providerId(), ShipyardAccessPolicy.current().feeCreditsPerWorkUnit(), 0);
        var order = new ShipConstructionOrder("retire_" + UUID.randomUUID(), ownerId, quote.sourceDesignId(), fleet.currentSystemId(), quote.yardId(),
                0, quote.workHours(), Map.of(), Map.of(), contract).withRetirement(new ShipRetirementPlan(shipId, quote.sourceDesignId(), quote.rules()));
        var orders = new ArrayList<>(state.shipConstructionOrders()); orders.add(order); return state.withShipConstructionOrders(orders);
    }
}
