package com.spaceconquest.engine.ship;

/** Absolute voyage clock keeps pending delivery times stable across replacement trajectories. */
public record FleetSupplyTimeline(double elapsedHours, double coastingStartHours) {
    public FleetSupplyTimeline {
        if (!Double.isFinite(elapsedHours) || elapsedHours < 0 || !Double.isFinite(coastingStartHours) || coastingStartHours < 0)
            throw new IllegalArgumentException("Invalid supply timeline");
    }
}
