package com.spaceconquest.engine.ship;

/** Frozen blueprint production values and stasis seats. Null seats retain the legacy per-pod fallback. */
public record ShipManufacturingProfile(double costMultiplier, int requiredComplexity, Integer stasisCapacity) {
    public ShipManufacturingProfile {
        if (!Double.isFinite(costMultiplier) || costMultiplier <= 0.0 || requiredComplexity < 0
                || (stasisCapacity != null && stasisCapacity < 0))
            throw new IllegalArgumentException("Invalid ship manufacturing profile");
    }

    public ShipManufacturingProfile(double costMultiplier, int requiredComplexity) {
        this(costMultiplier, requiredComplexity, null);
    }

    public static ShipManufacturingProfile baseline() {
        return new ShipManufacturingProfile(1.0, 0);
    }
}
