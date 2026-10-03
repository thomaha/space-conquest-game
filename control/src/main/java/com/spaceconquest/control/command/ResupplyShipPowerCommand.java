package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.ShipPowerResupply;

/** Buys a selected generator mixture or electrical reactor feed into its dedicated compartment. */
public record ResupplyShipPowerCommand(String shipId, String sourceBodyId, String fuelId,
                                       double quantityKg) implements GameCommand {
    @Override public boolean validate(GameState state) {
        return ShipPowerResupply.buy(state, shipId, sourceBodyId, fuelId, quantityKg) != state;
    }
    @Override public GameState apply(GameState state) {
        return ShipPowerResupply.buy(state, shipId, sourceBodyId, fuelId, quantityKg);
    }
}
