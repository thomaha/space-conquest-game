package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.*;
import java.util.TreeMap;

/** Cancels existing-ship work without undoing spent inputs, earned fees or live ship damage. */
public record CancelShipYardWorkCommand(String ownerId, String orderId) implements GameCommand {
    private ShipConstructionOrder order(GameState state) {
        return state.shipConstructionOrders().stream().filter(order -> orderId.equals(order.id()) && ownerId.equals(order.ownerEntityId())
                && (order.refit() != null || order.retirement() != null)).findFirst().orElse(null);
    }
    @Override public boolean validate(GameState state) { return ownerId != null && orderId != null && order(state) != null; }
    @Override public GameState apply(GameState state) {
        if (!validate(state)) return state; var order = order(state);
        String details = "Cancelled existing-ship work; no refund. Spent work " + order.accumulatedWorkHours()
                + "; consumed materials kg " + new TreeMap<>(order.consumedMaterialsKg())
                + "; earned service fees " + (order.yardContract() == null ? 0 : order.yardContract().paidFeesCredits());
        return ShipyardAccounting.record(state.withShipConstructionOrders(state.shipConstructionOrders().stream()
                .filter(item -> !orderId.equals(item.id())).toList()), order, 0, 0, false, details);
    }
}
