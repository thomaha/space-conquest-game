package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FlightRecovery;
import com.spaceconquest.engine.ship.FlightRecoveryReadiness;
import com.spaceconquest.engine.ship.PausedTravelRecovery;

/** Stages physical sublight replanning or resumption of a prepaid local or warp itinerary. */
public record RecoverFleetTravelCommand(String fleetId) implements GameCommand {
    public FlightRecovery.Plan preview(GameState state) {
        if (state == null || fleetId == null) return null;
        Fleet fleet = state.fleets().stream().filter(item -> fleetId.equals(item.id())).findFirst().orElse(null);
        var readiness = FlightRecoveryReadiness.check(state, fleet);
        return readiness.ready() ? readiness.plan() : null;
    }
    public PausedTravelRecovery.Preview pausedPreview(GameState state) {
        if (state == null || fleetId == null) return null;
        return PausedTravelRecovery.preview(state, state.fleets().stream()
                .filter(item -> fleetId.equals(item.id())).findFirst().orElse(null));
    }
    @Override public boolean validate(GameState state) {
        var paused = pausedPreview(state);
        return paused != null ? paused.ready() : preview(state) != null;
    }
    @Override public GameState apply(GameState state) {
        var paused = pausedPreview(state);
        if (paused != null) return !paused.ready() ? state : state.withFleets(state.fleets().stream()
                .map(fleet -> fleet.id().equals(fleetId) ? PausedTravelRecovery.resume(fleet, paused) : fleet).toList());
        var plan = preview(state);
        if (plan == null) return state;
        return state.withFleets(state.fleets().stream().map(fleet -> fleet.id().equals(fleetId)
                ? FlightRecovery.depart(fleet, plan) : fleet).toList());
    }
}
