package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.FlightRecovery;
import com.spaceconquest.engine.ship.RescueOrder;
import com.spaceconquest.engine.ship.RescueRendezvous;
import com.spaceconquest.engine.ship.RescueStatus;

/** Launches a physical intercept and a single revalidated supply transfer at velocity-matched contact. */
public record RescueFleetCommand(String fleetId, RescueOrder order) implements GameCommand {
    public FlightRecovery.Plan preview(GameState state) { return RescueRendezvous.plan(state, fleetId, order); }
    @Override public boolean validate(GameState state) { return preview(state) != null; }
    @Override public GameState apply(GameState state) {
        var plan = preview(state);
        if (plan == null) return state;
        var rescuer = RescueRendezvous.find(state, fleetId);
        var target = RescueRendezvous.find(state, order.targetFleetId());
        var departed = state.withFleets(state.fleets().stream().map(fleet -> fleet.id().equals(fleetId)
                ? RescueRendezvous.depart(rescuer, target, plan) : fleet).toList());
        return RescueRendezvous.recordStatus(departed, fleetId, order,
                RescueStatus.Phase.APPROACHING,
                "Rescue approaching; supplies remain aboard the donor until velocity-matched contact.");
    }
}
