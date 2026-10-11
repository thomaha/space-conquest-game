package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;

/** Escape recovery waits for live and recent wartime observations to clear at refuge and guard site. */
public final class FleetEscapeReturn {
    private FleetEscapeReturn() {}
    public static boolean clearToReturn(GameState state, Fleet fleet, FleetLocation.Site destination) {
        if (!fleet.interceptOrder().protection().escaped()) return true;
        if (FleetThreatAssessment.caution(state, fleet).action() != FleetThreatAssessment.Action.NO_CONTACT) return false;
        var atHome = fleet.withInterceptOrder(null).withLocation(FleetLocation.at(destination));
        return FleetThreatAssessment.caution(state, atHome).action() == FleetThreatAssessment.Action.NO_CONTACT;
    }
}
