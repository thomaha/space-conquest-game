package com.spaceconquest.engine.ship;

/** Application-defined refit labor, independent of weapon families and named ship classes. */
public record ShipRefitTechnology(double removalWorkFraction, double installationWorkMultiplier,
        double structuralWorkMultiplier, double structuralGrowthExponent, double armorRemovalWorkFraction,
        double systemsReconnectionMultiplier) {
    public ShipRefitTechnology {
        for (double value : new double[]{removalWorkFraction, installationWorkMultiplier, structuralWorkMultiplier,
                structuralGrowthExponent, armorRemovalWorkFraction, systemsReconnectionMultiplier})
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid refit work coefficient");
        if (installationWorkMultiplier <= 0 || structuralWorkMultiplier < 1 || structuralGrowthExponent <= 0)
            throw new IllegalArgumentException("Invalid structural refit effort");
    }
}
