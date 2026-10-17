package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Revalidates the current quote at the tick and reserves the existing ship for paid yard work. */
public record QueueShipRefitCommand(String ownerEntityId, String shipId, String targetDesignId) implements GameCommand {
    @Override public boolean validate(GameState state) { return ShipRefitQuote.inspect(state, ownerEntityId, shipId, targetDesignId).ready(); }
    @Override public GameState apply(GameState state) {
        var quote = ShipRefitQuote.inspect(state, ownerEntityId, shipId, targetDesignId);
        if (!quote.ready()) return state;
        var fleet = state.fleets().stream().filter(item -> item.ships().stream().anyMatch(ship -> shipId.equals(ship.id()))).findFirst().orElseThrow();
        String id = "refit_" + UUID.randomUUID();
        var receipt = quote.comparison().total();
        var contract = ownerEntityId.equals(quote.providerId()) ? null : new ShipyardContract(quote.providerId(), ShipyardAccessPolicy.current().feeCreditsPerWorkUnit(), 0);
        var order = new ShipConstructionOrder(id, ownerEntityId, targetDesignId, fleet.currentSystemId(), quote.yardId(), 0,
                receipt.workHours(), receipt.materialsKg(), Map.of(), contract).withRefit(new ShipRefitPlan(shipId, quote.sourceDesignId(), quote.comparison().retainedIdentities()));
        var orders = new ArrayList<>(state.shipConstructionOrders()); orders.add(order);
        return state.withShipConstructionOrders(orders);
    }
}
