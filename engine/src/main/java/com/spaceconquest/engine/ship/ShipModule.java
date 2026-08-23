package com.spaceconquest.engine.ship;

import java.util.Map;

/**
 * Functional ship component equipped into a starframe slot.
 *
 * @param id                unique module identifier
 * @param name              display name
 * @param slotSize          size category (SMALL, MEDIUM, LARGE)
 * @param slotCost          number of internal slots consumed
 * @param dryMassKg         component mass in kilograms
 * @param powerDrawKw       continuous electrical demand in kilowatts
 * @param powerOutputKw     continuous electrical generation in kilowatts
 * @param thrustOutputN     propulsion thrust force in Newtons
 * @param complexityLevel   nanotechnology / production complexity rating
 * @param materialCostsKg   manufacturing material receipt in kilograms
 * @param operationalStats  specific role attributes (e.g., cargoCapacity, troopCapacity, weaponDamage)
 */
public record ShipModule(
        String id,
        String name,
        String slotSize,
        int slotCost,
        double dryMassKg,
        double powerDrawKw,
        double powerOutputKw,
        double thrustOutputN,
        int complexityLevel,
        Map<String, Double> materialCostsKg,
        Map<String, Double> operationalStats
) {
    public ShipModule {
        if (materialCostsKg == null) materialCostsKg = Map.of();
        if (operationalStats == null) operationalStats = Map.of();
    }
}
