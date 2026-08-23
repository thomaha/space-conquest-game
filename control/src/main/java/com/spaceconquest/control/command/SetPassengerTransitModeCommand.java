package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.ShipInstance;

import java.util.ArrayList;
import java.util.List;

/**
 * Command to toggle passenger transit mode between CONSCIOUS and CRYOGENIC_STASIS on a ship.
 */
public record SetPassengerTransitModeCommand(
        String fleetId,
        String shipId,
        String transitMode
) implements GameCommand {

    @Override
    public boolean validate(GameState state) {
        if (state == null || fleetId == null || shipId == null || transitMode == null) {
            return false;
        }
        boolean validMode = ShipInstance.MODE_CONSCIOUS.equalsIgnoreCase(transitMode)
                || ShipInstance.MODE_CRYOGENIC_STASIS.equalsIgnoreCase(transitMode);
        if (!validMode) return false;

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

        List<Fleet> updatedFleets = new ArrayList<>();
        for (Fleet fleet : state.fleets()) {
            if (fleet.id().equals(fleetId)) {
                List<ShipInstance> updatedShips = new ArrayList<>();
                for (ShipInstance ship : fleet.ships()) {
                    if (ship.id().equals(shipId)) {
                        updatedShips.add(new ShipInstance(
                                ship.id(), ship.designId(), ship.ownerEntityId(),
                                ship.currentHullHealth(), ship.currentShieldHealth(), ship.currentFuelKg(),
                                ship.storedCargoKg(), ship.passengerCount(), ship.passengerRaceId(), transitMode.toUpperCase()
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

        return new GameState(
                state.turn(),
                state.status(),
                state.solarSystems(),
                state.empires(),
                state.corporations(),
                state.commercialHubs(),
                state.shadowSyndicates(),
                state.diplomaticRelations(),
                state.systemGovernors(),
                state.researchProjects(),
                state.technologyExchangeRoutes(),
                state.shipDesigns(),
                updatedFleets,
                state.geologicalDeposits(),
                state.powerGrids(),
                state.industrialFacilities(),
                state.expansionProjects()
        );
    }
}
