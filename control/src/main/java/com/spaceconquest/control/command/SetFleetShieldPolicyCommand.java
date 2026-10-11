package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.FleetShieldPolicy;

/** Changes optional recharge intent on the tick without changing stores or the funded itinerary. */
public record SetFleetShieldPolicyCommand(String ownerEntityId, String fleetId, FleetShieldPolicy.Mode policy) implements GameCommand {
    @Override public boolean validate(GameState state) {
        return state != null && ownerEntityId != null && fleetId != null && policy != null
                && state.fleets().stream().filter(fleet -> fleetId.equals(fleet.id())).count() == 1
                && state.fleets().stream().anyMatch(fleet -> fleetId.equals(fleet.id()) && ownerEntityId.equals(fleet.ownerEntityId()));
    }
    @Override public GameState apply(GameState state) {
        if (!validate(state)) return state;
        return state.withFleets(state.fleets().stream().map(fleet -> fleetId.equals(fleet.id()) ? fleet.withShieldPolicy(policy) : fleet).toList());
    }
}
