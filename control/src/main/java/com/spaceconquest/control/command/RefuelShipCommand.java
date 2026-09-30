package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.ShipFueling;

/** Stages a paid transfer of local propellant into a ship's fuel tank. */
public record RefuelShipCommand(String shipId, String sourceBodyId,
                                double quantityKg, String reactorFuelId) implements GameCommand {
    public RefuelShipCommand(String shipId, String sourceBodyId, double quantityKg) {
        this(shipId, sourceBodyId, quantityKg, null);
    }

    @Override
    public boolean validate(GameState state) {
        return ShipFueling.canRefuel(state, shipId, sourceBodyId, quantityKg, reactorFuelId);
    }

    @Override
    public GameState apply(GameState state) {
        return ShipFueling.refuel(state, shipId, sourceBodyId, quantityKg, reactorFuelId);
    }
}
