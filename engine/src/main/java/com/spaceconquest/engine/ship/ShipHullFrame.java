package com.spaceconquest.engine.ship;

/**
 * Structural chassis anchor for a spaceship.
 *
 * @param id                    unique frame identifier
 * @param name                  display name of the hull frame
 * @param totalSlots            maximum internal module capacity slots
 * @param structuralMaterialId  material used to forge the chassis skeleton
 * @param frameMassKg           base dry mass of the unequipped chassis
 * @param baseStructuralStrength baseline structural strength coefficient
 */
public record ShipHullFrame(
        String id,
        String name,
        int totalSlots,
        String structuralMaterialId,
        double frameMassKg,
        double baseStructuralStrength,
        String applicationId,
        int researchedMaximumSlots,
        double strengthMultiplier
) {
    public ShipHullFrame {
        if (totalSlots < 1 || researchedMaximumSlots < totalSlots || !Double.isFinite(frameMassKg) || frameMassKg <= 0
                || !Double.isFinite(strengthMultiplier) || strengthMultiplier <= 0) throw new IllegalArgumentException("Invalid captured hull definition");
    }
    public ShipHullFrame(String id, String name, int totalSlots, String structuralMaterialId, double frameMassKg, double baseStructuralStrength) {
        this(id, name, totalSlots, structuralMaterialId, frameMassKg, baseStructuralStrength, "", totalSlots, 1);
    }
}
