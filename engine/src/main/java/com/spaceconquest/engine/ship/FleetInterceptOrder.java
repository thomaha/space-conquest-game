package com.spaceconquest.engine.ship;

/** Explicit same-system pursuit; never grants sensor knowledge or an instantaneous rendezvous. */
public record FleetInterceptOrder(String ownerEntityId, String targetFleetId, String systemId, FleetProtectionAssignment protection) {
    public FleetInterceptOrder(String ownerEntityId, String targetFleetId, String systemId) {
        this(ownerEntityId, targetFleetId, systemId, null);
    }
    public FleetInterceptOrder withProtection(FleetProtectionAssignment value) {
        return new FleetInterceptOrder(ownerEntityId, targetFleetId, systemId, value);
    }
    public FleetInterceptOrder {
        if (ownerEntityId == null || targetFleetId == null || systemId == null
                || ownerEntityId.isBlank() || targetFleetId.isBlank() || systemId.isBlank())
            throw new IllegalArgumentException("Intercept needs an owner, target and system");
    }
}
