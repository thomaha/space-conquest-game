package com.spaceconquest.engine.ship;

import java.util.List;

/** Capability frozen from the sensor hardware installed when a blueprint is registered. */
public record ShipSensorProfile(double rangeKm, double scanStrength) {
    public ShipSensorProfile {
        if (!Double.isFinite(rangeKm) || rangeKm < 0 || !Double.isFinite(scanStrength) || scanStrength < 0)
            throw new IllegalArgumentException("Invalid sensor profile");
    }
    public static ShipSensorProfile capture(List<ShipModule> modules) {
        return new ShipSensorProfile(modules.stream().mapToDouble(module -> module.operationalStats().getOrDefault("sensorRangeKm", 0.0)).max().orElse(0),
                modules.stream().mapToDouble(module -> module.operationalStats().getOrDefault("scanStrength", 0.0)).max().orElse(0));
    }
}
