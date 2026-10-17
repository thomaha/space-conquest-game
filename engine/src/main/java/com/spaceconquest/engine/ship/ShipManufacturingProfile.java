package com.spaceconquest.engine.ship;

/** Frozen blueprint production values and stasis seats. Null seats retain the legacy per-pod fallback. */
public record ShipManufacturingProfile(double costMultiplier, int requiredComplexity, Integer stasisCapacity,
        java.util.Map<String, com.spaceconquest.engine.technology.ResearchVarianceResult> applicationModifiers) {
    public ShipManufacturingProfile {
        if (!Double.isFinite(costMultiplier) || costMultiplier <= 0.0 || requiredComplexity < 0
                || (stasisCapacity != null && stasisCapacity < 0))
            throw new IllegalArgumentException("Invalid ship manufacturing profile");
        applicationModifiers = applicationModifiers == null ? java.util.Map.of() : java.util.Map.copyOf(applicationModifiers);
    }

    public ShipManufacturingProfile(double costMultiplier, int requiredComplexity, Integer stasisCapacity) {
        this(costMultiplier, requiredComplexity, stasisCapacity, java.util.Map.of());
    }

    public ShipManufacturingProfile(double costMultiplier, int requiredComplexity) {
        this(costMultiplier, requiredComplexity, null);
    }

    public static ShipManufacturingProfile baseline() {
        return new ShipManufacturingProfile(1.0, 0);
    }
}
