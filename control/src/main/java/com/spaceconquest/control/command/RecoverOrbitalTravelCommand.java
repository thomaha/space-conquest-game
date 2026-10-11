package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.*;

/** Selects a circular orbit in the saved gravity frame and replans from the saved physical position and velocity. */
public record RecoverOrbitalTravelCommand(String ownerEntityId, String fleetId, double radiusMeters,
                                          double phaseRadians, OrbitalFlight.Frame expectedFrame) implements GameCommand {
    public RecoverOrbitalTravelCommand(String ownerEntityId, String fleetId, double radiusMeters, double phaseRadians) {
        this(ownerEntityId, fleetId, radiusMeters, phaseRadians, null);
    }
    public LocalSpaceTravel.Plan preview(GameState state) {
        if (state == null || ownerEntityId == null || fleetId == null) return null;
        var fleet = FleetOrganization.find(state, fleetId);
        if (fleet == null || !ownerEntityId.equals(fleet.ownerEntityId()) || !OrbitalRecovery.applies(fleet)
                || expectedFrame != null && !expectedFrame.equals(OrbitalRecovery.frame(fleet))) return null;
        var plan = OrbitalRecovery.plan(state, fleet, radiusMeters, phaseRadians);
        return plan != null && PassengerDepartureReadiness.ready(state, fleet, plan.days()) ? plan : null;
    }
    @Override public boolean validate(GameState state) { return preview(state) != null; }
    @Override public GameState apply(GameState state) {
        var plan = preview(state);
        if (plan == null) return state;
        return state.withFleets(state.fleets().stream().map(fleet -> fleet.id().equals(fleetId)
                ? OrbitalRecovery.depart(fleet, plan) : fleet).toList());
    }
}
