package com.spaceconquest.engine.combat;

import com.spaceconquest.engine.ship.ShipModule;
import java.util.Map;

/** Frozen build receipt; incomplete orders reserve their eventual carrier craft or surface slots. */
public record AuxiliaryConstructionOrder(String id, String ownerId, String systemId, String bodyId,
        String carrierShipId, String chassisApplicationId, String weaponApplicationId, int units,
        AuxiliaryHardware hardware, ShipModule weapon, int complexity, double requiredHours,
        double completedHours, Map<String, Double> requiredKg, Map<String, Double> consumedKg) {
    public AuxiliaryConstructionOrder {
        requiredKg = Map.copyOf(requiredKg); consumedKg = Map.copyOf(consumedKg);
        if (units <= 0 || !Double.isFinite(requiredHours) || requiredHours <= 0 || !Double.isFinite(completedHours)
                || completedHours < 0 || completedHours > requiredHours) throw new IllegalArgumentException("Invalid auxiliary order");
    }
    public AuxiliaryConstructionOrder progress(double hours, Map<String, Double> materials) {
        return new AuxiliaryConstructionOrder(id, ownerId, systemId, bodyId, carrierShipId, chassisApplicationId,
                weaponApplicationId, units, hardware, weapon, complexity, requiredHours, hours, requiredKg, materials);
    }
}
