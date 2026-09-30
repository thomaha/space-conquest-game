package com.spaceconquest.engine.ship;

import java.util.Map;

/** Daily ship build order with paid, physically consumed material installments. */
public record ShipConstructionOrder(
        String id,
        String ownerEntityId,
        String designId,
        String systemId,
        String yardBodyId,
        double accumulatedWorkHours,
        double requiredWorkHours,
        Map<String, Double> requiredMaterialsKg,
        Map<String, Double> consumedMaterialsKg
) {
    public ShipConstructionOrder {
        requiredMaterialsKg = requiredMaterialsKg == null ? Map.of() : Map.copyOf(requiredMaterialsKg);
        consumedMaterialsKg = consumedMaterialsKg == null ? Map.of() : Map.copyOf(consumedMaterialsKg);
    }
}
