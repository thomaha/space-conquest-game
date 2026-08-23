package com.spaceconquest.engine.ship;

import java.util.List;

/**
 * Operational grouping of starships maneuvering across solar networks.
 *
 * @param id              unique fleet identifier
 * @param name            display name of the fleet
 * @param ownerEntityId   empire or corporate owner identifier
 * @param currentSystemId current solar system location ID
 * @param targetSystemId  destination solar system ID (if in transit)
 * @param coordinateX     local system or galactic X coordinate
 * @param coordinateY     local system or galactic Y coordinate
 * @param transitProgress normalized movement progress (0.0 to 1.0)
 * @param isInWarp        true if fleet is traversing FTL spacetime warp bubble
 * @param fleetStance     operational stance (PASSIVE, AGGRESSIVE, PATROL, ESCORT)
 * @param ships           list of constituent ShipInstance platforms
 */
public record Fleet(
        String id,
        String name,
        String ownerEntityId,
        String currentSystemId,
        String targetSystemId,
        double coordinateX,
        double coordinateY,
        double transitProgress,
        boolean isInWarp,
        String fleetStance,
        List<ShipInstance> ships
) {
    public Fleet {
        if (ships == null) ships = List.of();
    }
}
