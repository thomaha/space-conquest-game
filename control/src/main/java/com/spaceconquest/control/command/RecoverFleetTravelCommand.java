package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FlightRecovery;
import com.spaceconquest.engine.ship.FlightRecoveryReadiness;
import com.spaceconquest.engine.ship.PausedTravelRecovery;

/** Stages physical local/sublight replanning or resumption of older prepaid local and warp travel. */
public record RecoverFleetTravelCommand(String fleetId, boolean emergencyOverride) implements GameCommand {
    public RecoverFleetTravelCommand(String fleetId) { this(fleetId, false); }
    private Fleet planningFleet(Fleet fleet) {
        return fleet != null && emergencyOverride ? fleet.withFuelPolicy(new com.spaceconquest.engine.ship.FleetFuelPolicy(0, 0, 0)) : fleet;
    }
    private Fleet commitOverride(Fleet fleet) {
        if (!emergencyOverride) return fleet;
        var propulsion = new java.util.HashMap<>(fleet.journeyPropulsion());
        propulsion.replaceAll((id, saved) -> saved.withProtectedPropellant(0));
        return fleet.withJourneyPropulsion(propulsion);
    }
    public FlightRecovery.Plan preview(GameState state) {
        if (state == null || fleetId == null) return null;
        Fleet fleet = state.fleets().stream().filter(item -> fleetId.equals(item.id())).findFirst().orElse(null);
        var readiness = FlightRecoveryReadiness.check(state, planningFleet(fleet));
        return readiness.ready() && PassengerDepartureReadiness.ready(state, fleet,
                readiness.plan().trajectory().totalSeconds() / 86400) ? readiness.plan() : null;
    }
    public PausedTravelRecovery.Preview pausedPreview(GameState state) {
        if (state == null || fleetId == null) return null;
        Fleet fleet = state.fleets().stream().filter(item -> fleetId.equals(item.id())).findFirst().orElse(null);
        return PausedTravelRecovery.preview(state, planningFleet(fleet));
    }
    public boolean passengersReady(GameState state, double remainingDays) {
        if (state == null || fleetId == null) return false;
        Fleet fleet = state.fleets().stream().filter(item -> fleetId.equals(item.id())).findFirst().orElse(null);
        return PassengerDepartureReadiness.ready(state, fleet, remainingDays);
    }
    @Override public boolean validate(GameState state) {
        var paused = pausedPreview(state);
        return paused != null ? paused.ready() && passengersReady(state, paused.remainingDays()) : preview(state) != null;
    }
    @Override public GameState apply(GameState state) {
        var paused = pausedPreview(state);
        if (paused != null) return !paused.ready() || !passengersReady(state, paused.remainingDays()) ? state : state.withFleets(state.fleets().stream()
                .map(fleet -> fleet.id().equals(fleetId) ? commitOverride(PausedTravelRecovery.resume(fleet, paused)) : fleet).toList());
        var plan = preview(state);
        if (plan == null) return state;
        return state.withFleets(state.fleets().stream().map(fleet -> fleet.id().equals(fleetId)
                ? commitOverride(FlightRecovery.depart(fleet, plan)) : fleet).toList());
    }
}
