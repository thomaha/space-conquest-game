package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.FleetOrganization;
import com.spaceconquest.engine.ship.FleetProtectionAssignment;

/** Cancels onward pursuit while retaining the already committed physical journey. */
public record CancelInterceptFleetCommand(String ownerEntityId, String fleetId) implements GameCommand {
    @Override public boolean validate(GameState state) {
        if (state == null || ownerEntityId == null || fleetId == null) return false;
        var fleet = FleetOrganization.find(state, fleetId);
        if (fleet == null || !ownerEntityId.equals(fleet.ownerEntityId()) || fleet.interceptOrder() == null) return false;
        var assignment = fleet.interceptOrder().protection();
        return assignment == null || assignment.repairIntent() == null || assignment.repairIntent().intercept() != null;
    }
    @Override public GameState apply(GameState state) {
        if (!validate(state)) return state;
        return state.withFleets(state.fleets().stream().map(fleet -> fleetId.equals(fleet.id())
                ? cancel(fleet) : fleet).toList());
    }
    private com.spaceconquest.engine.ship.Fleet cancel(com.spaceconquest.engine.ship.Fleet fleet) {
        var assignment = fleet.interceptOrder().protection();
        if (assignment == null) return fleet.withInterceptOrder(null);
        var intent = assignment.repairIntent();
        if (intent != null) {
            if (intent.intercept() == null) return fleet.withInterceptOrder(null);
            var canceled = new com.spaceconquest.engine.ship.FleetRepairIntent(intent.stance(), intent.guard(), intent.escort(), null);
            return fleet.withInterceptOrder(fleet.interceptOrder().withProtection(assignment.withRepairIntent(canceled)));
        }
        return fleet.withInterceptOrder(fleet.interceptOrder().withProtection(assignment.at(FleetProtectionAssignment.Stage.RETURNING, "")));
    }
}
