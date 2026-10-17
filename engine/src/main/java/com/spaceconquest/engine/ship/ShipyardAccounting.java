package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;

/** Records actual progress and blockers in the tick snapshot. */
public final class ShipyardAccounting {
    private ShipyardAccounting() {}
    public static GameState record(GameState state, ShipConstructionOrder order, double work, double fee, boolean complete, String status) {
        return state.toBuilder().shipyardLedger(state.shipyardLedger().append(ShipyardLedger.work(state, order, work, fee, complete, status))).build();
    }
}
