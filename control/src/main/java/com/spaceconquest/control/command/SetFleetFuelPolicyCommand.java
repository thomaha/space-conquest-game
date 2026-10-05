package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.FleetFuelPolicy;

/** Saves normal contingency targets without changing an already funded itinerary. */
public record SetFleetFuelPolicyCommand(String ownerEntityId, String fleetId, FleetFuelPolicy policy) implements GameCommand {
    @Override public boolean validate(GameState state) {
        return state != null && ownerEntityId != null && fleetId != null && policy != null
                && state.fleets().stream().anyMatch(fleet -> fleetId.equals(fleet.id()) && ownerEntityId.equals(fleet.ownerEntityId()));
    }
    @Override public GameState apply(GameState state) {
        if (!validate(state)) return state;
        return state.withFleets(state.fleets().stream().map(fleet -> fleetId.equals(fleet.id()) ? fleet.withFuelPolicy(policy) : fleet).toList());
    }
}
