package com.spaceconquest.control.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.util.Map;

/** Planning preferences only. Hardware, material recipes and work remain technological application data. */
public record ShipLifecyclePolicy(int maximumShipPreviews, int maximumModelPreviews, double treasuryReserveCredits,
        double minimumGainFraction, double minimumRefitHullFraction, double maximumRefitBuildCostFraction,
        double maximumDowntimeDays, double downtimeCreditsPerDay, double utilityFloor, Map<String, Double> capabilityWeights) {
    public ShipLifecyclePolicy {
        capabilityWeights = Map.copyOf(capabilityWeights);
        if (maximumShipPreviews < 1 || maximumModelPreviews < 1 || utilityFloor <= 0 || maximumDowntimeDays <= 0
                || minimumRefitHullFraction > 1 || maximumRefitBuildCostFraction > 1 || capabilityWeights.isEmpty())
            throw new IllegalArgumentException("Invalid ship lifecycle policy");
        for (double value : new double[]{treasuryReserveCredits, minimumGainFraction, minimumRefitHullFraction,
                maximumRefitBuildCostFraction, maximumDowntimeDays, downtimeCreditsPerDay, utilityFloor})
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid ship lifecycle threshold");
        if (capabilityWeights.values().stream().anyMatch(value -> !Double.isFinite(value) || value < 0)
                || capabilityWeights.values().stream().mapToDouble(Double::doubleValue).sum() <= 0) throw new IllegalArgumentException("Invalid capability weight");
    }
    private static final class Loaded {
        static final ShipLifecyclePolicy VALUE = read();
        private static ShipLifecyclePolicy read() {
            try (var stream = ShipLifecyclePolicy.class.getResourceAsStream("/ship_lifecycle_policy.json")) {
                if (stream == null) throw new IOException("Missing ship lifecycle policy");
                return new ObjectMapper().readValue(stream, ShipLifecyclePolicy.class);
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
        }
    }
    public static ShipLifecyclePolicy current() { return Loaded.VALUE; }
}
