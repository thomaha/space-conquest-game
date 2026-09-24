package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.ShipInstance;

import java.util.ArrayList;
import java.util.List;

/**
 * Command to embark passengers/troops onto a spaceship.
 */
public record LoadPassengersCommand(
        String fleetId,
        String shipId,
        String passengerRaceId,
        int passengerCount,
        String transitMode
) implements GameCommand {

    @Override
    public boolean validate(GameState state) {
        if (state == null || fleetId == null || shipId == null || passengerCount <= 0) {
            return false;
        }

        return state.fleets().stream()
                .filter(f -> f.id().equals(fleetId))
                .flatMap(f -> f.ships().stream())
                .anyMatch(s -> s.id().equals(shipId));
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        String mode = (transitMode != null && !transitMode.isEmpty())
                ? transitMode.toUpperCase()
                : ShipInstance.MODE_CRYOGENIC_STASIS;

        List<Fleet> updatedFleets = new ArrayList<>();
        for (Fleet fleet : state.fleets()) {
            if (fleet.id().equals(fleetId)) {
                List<ShipInstance> updatedShips = new ArrayList<>();
                for (ShipInstance ship : fleet.ships()) {
                    if (ship.id().equals(shipId)) {
                        updatedShips.add(new ShipInstance(
                                ship.id(), ship.designId(), ship.ownerEntityId(),
                                ship.currentHullHealth(), ship.currentShieldHealth(), ship.currentFuelKg(),
                                ship.storedCargoKg(), passengerCount, passengerRaceId != null ? passengerRaceId : "human", mode
                        ));
                    } else {
                        updatedShips.add(ship);
                    }
                }
                updatedFleets.add(new Fleet(
                        fleet.id(), fleet.name(), fleet.ownerEntityId(),
                        fleet.currentSystemId(), fleet.targetSystemId(),
                        fleet.coordinateX(), fleet.coordinateY(),
                        fleet.transitProgress(), fleet.isInWarp(), fleet.fleetStance(),
                        updatedShips
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
