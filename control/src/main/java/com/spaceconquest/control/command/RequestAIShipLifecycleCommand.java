package com.spaceconquest.control.command;

import com.spaceconquest.control.ai.AIShipDeploymentIntent;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.*;

/** Recomputes the best funded recovery choice at the command tick, including duplicate reservations. */
public record RequestAIShipLifecycleCommand(String ownerId, String shipId) implements GameCommand {
    @Override public boolean validate(GameState state) { return AIShipLifecycleQuote.inspect(state, ownerId, shipId) != null; }
    @Override public GameState apply(GameState state) {
        var quote = AIShipLifecycleQuote.inspect(state, ownerId, shipId); if (quote == null) return state;
        if (quote.action() == AIShipLifecycleQuote.Action.REPAIR) return FleetRepairOrders.advance(state, quote.fleetId());
        var registered = AIShipResearchRevision.register(state, quote.target());
        if (quote.action() == AIShipLifecycleQuote.Action.REFIT) return new QueueShipRefitCommand(ownerId, shipId, quote.target().id()).apply(registered);
        var fleet = FleetOrganization.find(state, quote.fleetId());
        var built = new QueueShipBuildCommand(ownerId, quote.target().id(), fleet.currentSystemId()).apply(registered);
        if (built.shipConstructionOrders().size() <= state.shipConstructionOrders().size()) return state;
        var order = built.shipConstructionOrders().getLast();
        built = built.withShipConstructionOrders(built.shipConstructionOrders().stream().map(item -> item.id().equals(order.id())
                ? item.withReplacement(new ShipReplacementPlan(shipId)) : item).toList());
        return AIShipDeploymentIntent.capture(built, ownerId);
    }
}
