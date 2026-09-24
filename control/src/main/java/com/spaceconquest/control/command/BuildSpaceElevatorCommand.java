package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.macrostructure.SpaceElevator;

import java.util.ArrayList;
import java.util.List;

/**
 * Command to construct a planetary space elevator megastructure.
 */
public record BuildSpaceElevatorCommand(
        String planetId,
        String ownerEntityId,
        double throughputCapacityKgPerTurn
) implements GameCommand {

    @Override
    public boolean validate(GameState state) {
        if (state == null || planetId == null || ownerEntityId == null) {
            return false;
        }
        // Cannot build duplicate space elevator on same planet
        return state.spaceElevators().stream().noneMatch(e -> e.planetId().equals(planetId));
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        SpaceElevator elevator = new SpaceElevator(
                "elevator_" + planetId,
                planetId,
                ownerEntityId,
                throughputCapacityKgPerTurn > 0.0 ? throughputCapacityKgPerTurn : 100000.0,
                0.95,
                100.0,
                true
        );

        List<SpaceElevator> updated = new ArrayList<>(state.spaceElevators());
        updated.add(elevator);

        return state.toBuilder()
                .spaceElevators(updated)
                .build();
    }
}
