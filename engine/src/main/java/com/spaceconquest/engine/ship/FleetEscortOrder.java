package com.spaceconquest.engine.ship;

import java.util.List;

/** Escort membership within a single physical convoy and identity to restore on detachment. */
public record FleetEscortOrder(String ownerEntityId, String escortFleetId, String escortFleetName,
                               String previousStance, List<String> shipIds) {
    public FleetEscortOrder { shipIds = shipIds == null ? List.of() : List.copyOf(shipIds); }

    public FleetEscortOrder retain(List<ShipInstance> ships) {
        var remaining = shipIds.stream().filter(id -> ships.stream().anyMatch(ship -> id.equals(ship.id()))).toList();
        return remaining.isEmpty() || remaining.size() == ships.size() ? null
                : new FleetEscortOrder(ownerEntityId, escortFleetId, escortFleetName, previousStance, remaining);
    }
}
