package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.ShipBatteryCharging;

/** Withdraws paid electricity from a surface or docked station's actual grid storage. */
public record ChargeShipBatteryCommand(String shipId, String sourceId, double inputKwh) implements GameCommand {
    @Override public boolean validate(GameState state) {
        return ShipBatteryCharging.charge(state, shipId, sourceId, inputKwh) != state;
    }
    @Override public GameState apply(GameState state) {
        return ShipBatteryCharging.charge(state, shipId, sourceId, inputKwh);
    }
}
