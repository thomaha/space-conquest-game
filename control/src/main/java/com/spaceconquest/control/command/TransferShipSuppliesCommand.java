package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.ShipSupplyTransfer;

/** Transfers physical supplies atomically after validating both ships against the current tick state. */
public record TransferShipSuppliesCommand(String donorShipId, String receiverShipId,
                                          ShipSupplyTransfer.Source source,
                                          ShipSupplyTransfer.Destination destination,
                                          String fuelId, double quantityKg) implements GameCommand {
    @Override public boolean validate(GameState state) {
        return apply(state) != state;
    }
    @Override public GameState apply(GameState state) {
        return ShipSupplyTransfer.transfer(state, donorShipId, receiverShipId, source, destination, fuelId, quantityKg);
    }
}
