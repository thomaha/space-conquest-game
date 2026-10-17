package com.spaceconquest.control.command;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.*;

public record CancelShipMiningCommand(String ownerId, String shipId) implements GameCommand {
    @Override public boolean validate(GameState state) {
        return state != null && ownerId != null && shipId != null && ShipMiningProcessor.committed(state, shipId)
                && ownerId.equals(state.shipMiningState().orders().get(shipId).ownerId());
    }
    @Override public GameState apply(GameState state) {
        if (!validate(state)) return state; var old = state.shipMiningState().orders().get(shipId);
        return state.toBuilder().shipMiningState(state.shipMiningState().with(shipId, new ShipMiningOrder(ownerId, shipId, old.depositId(), old.lastWorkedTurn(), old.extractedKg(), "Cancelled"))).build();
    }
}
