package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Command to construct a starship from a blueprint and commission it into a fleet.
 */
public record QueueShipBuildCommand(
        String ownerEntityId,
        String designId,
        String systemId
) implements GameCommand {

    @Override
    public boolean validate(GameState state) {
        if (state == null || ownerEntityId == null || designId == null || systemId == null) {
            return false;
        }
        return state.shipDesigns().stream().anyMatch(d -> d.id().equals(designId));
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        ShipDesign design = state.shipDesigns().stream()
                .filter(d -> d.id().equals(designId))
                .findFirst()
                .orElse(null);
        if (design == null) return state;

        String shipId = "ship_" + UUID.randomUUID().toString().substring(0, 8);
        ShipInstance newShip = new ShipInstance(
                shipId,
                designId,
                ownerEntityId,
                1000.0,
                500.0,
                100.0,
                Map.of()
        );

        List<Fleet> updatedFleets = new ArrayList<>(state.fleets());
        Fleet existingFleet = updatedFleets.stream()
                .filter(f -> f.ownerEntityId().equals(ownerEntityId) && f.currentSystemId().equals(systemId))
                .findFirst()
                .orElse(null);

        if (existingFleet != null) {
            List<ShipInstance> fleetShips = new ArrayList<>(existingFleet.ships());
            fleetShips.add(newShip);
            updatedFleets.remove(existingFleet);
            updatedFleets.add(new Fleet(
                    existingFleet.id(),
                    existingFleet.name(),
                    existingFleet.ownerEntityId(),
                    existingFleet.currentSystemId(),
                    existingFleet.targetSystemId(),
                    existingFleet.coordinateX(),
                    existingFleet.coordinateY(),
                    existingFleet.transitProgress(),
                    existingFleet.isInWarp(),
                    existingFleet.fleetStance(),
                    fleetShips
            ));
        } else {
            String fleetId = "fleet_" + UUID.randomUUID().toString().substring(0, 8);
            updatedFleets.add(new Fleet(
                    fleetId,
                    "Task Force " + systemId.toUpperCase(),
                    ownerEntityId,
                    systemId,
                    "",
                    0.0,
                    0.0,
                    0.0,
                    false,
                    "PASSIVE",
                    List.of(newShip)
            ));
        }

        return state.toBuilder()
                .fleets(updatedFleets)
                .build();
    }
}
