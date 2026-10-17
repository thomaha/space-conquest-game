package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.*;

/** Trade forecasts and execution share a funded approach that tries economical orbital phasing first. */
final class TradeLocalPlan {
    private TradeLocalPlan() {}
    static LocalTravel.Plan plan(GameState state, Fleet fleet, FleetLocation.Site destination) {
        if (state == null || fleet == null || destination == null) return null;
        return OrbitalTravel.applies(state, fleet, destination)
                ? OrbitalTravel.preview(state, fleet, destination, true).plan() : LocalTravel.plan(state, fleet, destination);
    }
}
