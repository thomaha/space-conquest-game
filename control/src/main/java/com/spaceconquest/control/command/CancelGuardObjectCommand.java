package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;

public record CancelGuardObjectCommand(String ownerEntityId, String fleetId) implements GameCommand {
    @Override public boolean validate(GameState state) {
        return state != null && ownerEntityId != null && fleetId != null && state.fleets().stream()
                .anyMatch(fleet -> fleetId.equals(fleet.id()) && ownerEntityId.equals(fleet.ownerEntityId())
                        && (fleet.guardOrder() != null || fleet.interceptOrder() != null && fleet.interceptOrder().protection() != null
                            && fleet.interceptOrder().protection().repairIntent() != null
                            && fleet.interceptOrder().protection().repairIntent().guard() != null));
    }

    @Override public GameState apply(GameState state) {
        return !validate(state) ? state : state.withFleets(state.fleets().stream().map(fleet ->
                fleetId.equals(fleet.id()) ? cancel(fleet) : fleet).toList());
    }
    private com.spaceconquest.engine.ship.Fleet cancel(com.spaceconquest.engine.ship.Fleet fleet) {
        if (fleet.guardOrder() != null) return fleet.withGuardOrder(null);
        var assignment = fleet.interceptOrder().protection(); var intent = assignment.repairIntent();
        var canceled = new com.spaceconquest.engine.ship.FleetRepairIntent("PASSIVE", null, intent.escort(), intent.intercept());
        return fleet.withInterceptOrder(fleet.interceptOrder().withProtection(assignment.withRepairIntent(canceled)));
    }
}
