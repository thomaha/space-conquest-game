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
        return ownerEntityId != null && state.tradeRoutes().stream().anyMatch(r ->
                r.id().equals(routeId) && ownerEntityId.equals(r.ownerEntityId()));
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) return state;

        List<TradeRoute> updated = new ArrayList<>();
        for (TradeRoute r : state.tradeRoutes()) {
            if (r.id().equals(routeId)) {
                updated.add(r.withActive(false));
            } else {
                updated.add(r);
            }
        }

        return state.toBuilder()
                .tradeRoutes(updated)
                .build();
    }
}
