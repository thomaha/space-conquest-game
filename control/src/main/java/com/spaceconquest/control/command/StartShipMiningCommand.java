package com.spaceconquest.control.command;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.*;

/** Tick-revalidated finite mining at the ship's actual site. No travel or supplies are granted. */
public record StartShipMiningCommand(String ownerId, String shipId, String depositId) implements GameCommand {
    @Override public boolean validate(GameState state) {
        return state != null && ownerId != null && shipId != null && depositId != null && !ShipMiningProcessor.committed(state, shipId)
                && ShipMiningProcessor.problem(state, ownerId, shipId, depositId) == null;
    }
    @Override public GameState apply(GameState state) {
        if (!validate(state)) return state;
        var previous = state.shipMiningState().orders().get(shipId);
        return state.toBuilder().shipMiningState(state.shipMiningState().with(shipId, new ShipMiningOrder(ownerId, shipId, depositId,
                previous == null ? -1 : previous.lastWorkedTurn(), previous == null ? 0 : previous.extractedKg(), "Queued"))).build();
    }
}
