package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;

/** Stellar compatibility facade; all planning uses the shared gravity-frame mechanism. */
public final class StellarRecovery {
    private StellarRecovery() {}
    public static boolean applies(Fleet fleet) {
        return OrbitalRecovery.applies(fleet) && OrbitalRecovery.frame(fleet).kind() == OrbitalFlight.CenterKind.STELLAR;
    }
    public static boolean recoverable(Fleet fleet) { return applies(fleet) && OrbitalRecovery.recoverable(fleet); }
    public static OrbitalRecovery.InitialState initialState(GameState state, Fleet fleet) {
        return applies(fleet) ? OrbitalRecovery.initialState(state, fleet) : null;
    }
    public static LocalSpaceTravel.Plan plan(GameState state, Fleet fleet, double radiusMeters, double phaseRadians) {
        return applies(fleet) ? OrbitalRecovery.plan(state, fleet, radiusMeters, phaseRadians) : null;
    }
    public static Fleet depart(Fleet fleet, LocalSpaceTravel.Plan plan) { return OrbitalRecovery.depart(fleet, plan); }
    public static Fleet cancel(Fleet fleet) { return OrbitalRecovery.cancel(fleet); }
}