package com.spaceconquest.engine.logistics;

import java.util.List;

/**
 * Represents an automated cargo logistics route transporting materials between planets or commercial hubs.
 */
public record TradeRoute(
        String id,
        String name,
        String ownerEntityId,
        String originEntityId,
        String destinationEntityId,
        String materialId,
        double transferAmountPerTurnKg,
        double minSourceInventoryThresholdKg,
        double maxDestinationCapacityKg,
        List<String> assignedFreighterIds,
        double totalVolumeMovedKg,
        boolean isActive
) {
    public TradeRoute {
        if (assignedFreighterIds == null) assignedFreighterIds = List.of();
    }
}
