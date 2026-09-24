package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.Fleet;

import java.util.ArrayList;
import java.util.List;

/**
 * Command to set the tactical operational stance of a fleet.
 */
public record SetFleetStanceCommand(
        String fleetId,
        String newStance
) implements GameCommand {

    @Override
    public boolean validate(GameState state) {
        if (state == null || fleetId == null || newStance == null) {
            return false;
        }
        boolean stanceValid = "PASSIVE".equalsIgnoreCase(newStance)
                || "AGGRESSIVE".equalsIgnoreCase(newStance)
                || "PATROL".equalsIgnoreCase(newStance)
                || "ESCORT".equalsIgnoreCase(newStance);
        return stanceValid && state.fleets().stream().anyMatch(f -> f.id().equals(fleetId));
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        List<Fleet> updatedFleets = new ArrayList<>();
        for (Fleet fleet : state.fleets()) {
            if (fleet.id().equals(fleetId)) {
                updatedFleets.add(new Fleet(
                        fleet.id(),
                        fleet.name(),
                        fleet.ownerEntityId(),
                        fleet.currentSystemId(),
                        fleet.targetSystemId(),
                        fleet.coordinateX(),
                        fleet.coordinateY(),
                        fleet.transitProgress(),
                        fleet.isInWarp(),
                        newStance.toUpperCase(),
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
