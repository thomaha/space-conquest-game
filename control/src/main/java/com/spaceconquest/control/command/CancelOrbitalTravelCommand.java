package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.FleetOrganization;
import com.spaceconquest.engine.ship.OrbitalFlightProcessor;

/** Cancels maneuver intent while preserving the actual orbital motion or parking site. */
public record CancelOrbitalTravelCommand(String ownerEntityId, String fleetId) implements GameCommand {
    @Override public boolean validate(GameState state) {
        if (state == null || ownerEntityId == null || fleetId == null) return false;
        var fleet = FleetOrganization.find(state, fleetId);
        return fleet != null && ownerEntityId.equals(fleet.ownerEntityId()) && fleet.location().orbitalFlight() != null;
    }
    @Override public GameState apply(GameState state) {
        if (!validate(state)) return state;
        return state.withFleets(state.fleets().stream().map(fleet -> fleet.id().equals(fleetId)
                ? OrbitalFlightProcessor.cancel(fleet) : fleet).toList());
    }
}
