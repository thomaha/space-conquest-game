package com.spaceconquest.engine.ship;

/** Application-defined dismantling effort and condition-sensitive material recovery. */
public record ShipRetirementTechnology(double dismantlingWorkFraction, double frameRecoveryFraction,
        double armorRecoveryFraction, double componentRecoveryFraction) {
    public ShipRetirementTechnology {
        if (!Double.isFinite(dismantlingWorkFraction) || dismantlingWorkFraction <= 0)
            throw new IllegalArgumentException("Invalid dismantling work");
        for (double value : new double[]{frameRecoveryFraction, armorRecoveryFraction, componentRecoveryFraction})
            if (!Double.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException("Invalid salvage recovery");
    }
}
