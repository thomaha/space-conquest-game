package com.spaceconquest.engine.ship;

import java.util.Map;

/** Cumulative equivalent unpowered hours for each cargo pool and physical losses on the last tick. */
public record CargoPreservationState(Map<String, Double> exposureHours, Map<String, Double> lostKgToday) {
    public CargoPreservationState {
        exposureHours = exposureHours == null ? Map.of() : Map.copyOf(exposureHours);
        lostKgToday = lostKgToday == null ? Map.of() : Map.copyOf(lostKgToday);
        if (exposureHours.values().stream().anyMatch(value -> !Double.isFinite(value) || value < 0)
                || lostKgToday.values().stream().anyMatch(value -> !Double.isFinite(value) || value < 0))
            throw new IllegalArgumentException("Invalid cargo preservation state");
    }
    public static CargoPreservationState empty() { return new CargoPreservationState(Map.of(), Map.of()); }
}
