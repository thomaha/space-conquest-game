package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.*;

/** Selects a circular stellar orbit and replans from the saved physical position and velocity. */
public record RecoverStellarTravelCommand(String ownerEntityId, String fleetId, double radiusMeters,
                                          double phaseRadians) implements GameCommand {
    public LocalSpaceTravel.Plan preview(GameState state) {
        if (state == null || ownerEntityId == null || fleetId == null) return null;
        var fleet = FleetOrganization.find(state, fleetId);
        if (fleet == null || !ownerEntityId.equals(fleet.ownerEntityId())) return null;
        var plan = StellarRecovery.plan(state, fleet, radiusMeters, phaseRadians);
        return plan != null && PassengerDepartureReadiness.ready(state, fleet, plan.days()) ? plan : null;
    }
    @Override public boolean validate(GameState state) { return preview(state) != null; }
    @Override public GameState apply(GameState state) {
        var plan = preview(state);
        if (plan == null) return state;
        return state.withFleets(state.fleets().stream().map(fleet -> fleet.id().equals(fleetId)
                ? StellarRecovery.depart(fleet, plan) : fleet).toList());
    }
}
