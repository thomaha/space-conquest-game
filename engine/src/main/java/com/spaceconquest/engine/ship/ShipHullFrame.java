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
        double baseStructuralStrength
) {}
