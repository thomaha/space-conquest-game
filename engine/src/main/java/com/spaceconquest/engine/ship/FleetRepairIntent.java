package com.spaceconquest.engine.ship;

/** Previous ordinary assignment retained through automatic repair or weapon service recovery. */
public record FleetRepairIntent(String stance, FleetGuardOrder guard, FleetEscortOrder escort, FleetInterceptOrder intercept) {
    public FleetRepairIntent {
        stance = stance == null ? "PASSIVE" : stance;
        if (intercept != null && intercept.protection() != null)
            throw new IllegalArgumentException("Repair intent cannot contain another automatic recovery");
    }
    public static FleetRepairIntent capture(Fleet fleet) {
        return new FleetRepairIntent(fleet.fleetStance(), fleet.guardOrder(), fleet.escortOrder(), fleet.interceptOrder());
    }
    public Fleet restore(com.spaceconquest.engine.GameState state, Fleet fleet) {
        var restored = fleet.withInterceptOrder(null).withGuardOrder(null).withEscortOrder(null).withStance(stance);
        if (guard != null && guard.validTarget(state, restored)) return restored.withGuardOrder(guard);
        if (escort != null) return restored.withEscortOrder(escort);
        if (intercept != null && FleetInterceptProcessor.militaryAvailable(state, restored)
                && FleetInterceptProcessor.contact(state, restored, FleetOrganization.find(state, intercept.targetFleetId())))
            return restored.withInterceptOrder(intercept);
        return restored;
    }
}
