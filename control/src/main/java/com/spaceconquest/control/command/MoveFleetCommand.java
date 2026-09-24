package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.Fleet;

import java.util.ArrayList;
import java.util.List;

/**
 * Command to order a fleet to maneuver or initiate interstellar warp transit.
 */
public record MoveFleetCommand(
        String fleetId,
        String targetSystemId,
        double targetX,
        double targetY
) implements GameCommand {

    public MoveFleetCommand(String fleetId, String targetSystemId) {
        this(fleetId, targetSystemId, 0.0, 0.0);
    }

    @Override
    public boolean validate(GameState state) {
        if (state == null || fleetId == null) {
            return false;
        }
        return state.fleets().stream().anyMatch(f -> f.id().equals(fleetId));
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        List<Fleet> updatedFleets = new ArrayList<>();
        for (Fleet fleet : state.fleets()) {
            if (fleet.id().equals(fleetId)) {
                boolean startWarp = targetSystemId != null && !targetSystemId.isEmpty() && !targetSystemId.equals(fleet.currentSystemId());
                updatedFleets.add(new Fleet(
                        fleet.id(),
                        fleet.name(),
                        fleet.ownerEntityId(),
                        fleet.currentSystemId(),
                        targetSystemId != null ? targetSystemId : fleet.targetSystemId(),
                        targetX,
                        targetY,
                        startWarp ? 0.0 : fleet.transitProgress(),
                        startWarp || fleet.isInWarp(),
                        fleet.fleetStance(),
                        fleet.ships()
                ));
            } else {
                updatedFleets.add(fleet);
            }
        }

        return state.toBuilder()
                .fleets(updatedFleets)
                .build();
    }
}
