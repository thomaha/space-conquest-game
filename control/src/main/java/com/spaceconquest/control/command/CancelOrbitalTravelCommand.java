package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.FleetOrganization;
import com.spaceconquest.engine.ship.OrbitalFlightProcessor;
import com.spaceconquest.engine.ship.OrbitalRecovery;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetProtectionAssignment;

/** Cancels maneuver intent while preserving the actual orbital motion or parking site. */
public record CancelOrbitalTravelCommand(String ownerEntityId, String fleetId) implements GameCommand {
    @Override public boolean validate(GameState state) {
        if (state == null || ownerEntityId == null || fleetId == null) return false;
        var fleet = FleetOrganization.find(state, fleetId);
        return fleet != null && ownerEntityId.equals(fleet.ownerEntityId()) && (fleet.location().orbitalFlight() != null
                || fleet.location().localFlight() != null && OrbitalRecovery.applies(fleet) && !fleet.location().localFlight().interrupted());
    }
    @Override public GameState apply(GameState state) {
        if (!validate(state)) return state;
        return state.withFleets(state.fleets().stream().map(fleet -> fleet.id().equals(fleetId)
                ? cancel(fleet)
                : fleet).toList());
    }
    private Fleet cancel(Fleet fleet) {
        var order = fleet.interceptOrder();
        var canceled = fleet.location().localFlight() != null ? OrbitalRecovery.cancel(fleet.withInterceptOrder(null))
                : OrbitalFlightProcessor.cancel(fleet.withInterceptOrder(null));
        return order != null && order.protection() != null ? canceled.withInterceptOrder(order.withProtection(
                order.protection().at(FleetProtectionAssignment.Stage.RETURNING, ""))) : canceled;
    }
}
