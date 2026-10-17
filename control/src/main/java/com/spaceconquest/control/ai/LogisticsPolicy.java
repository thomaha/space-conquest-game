package com.spaceconquest.control.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;

/** Resource-defined thresholds and bounded preview work for paid facility replenishment. */
public record LogisticsPolicy(int shortageObservationTurns, double reorderDays, double targetDays, double sourceReserveDays,
        double minimumDeliveryDays, double maximumDepartureCostCredits, double treasuryReserveCredits, int maximumShipmentPreviews, int maximumRepositionDays) {
    public LogisticsPolicy {
        for (double value : new double[]{reorderDays, targetDays, sourceReserveDays, minimumDeliveryDays, maximumDepartureCostCredits, treasuryReserveCredits})
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid logistics policy");
        if (shortageObservationTurns < 1 || reorderDays <= 0 || targetDays < reorderDays || minimumDeliveryDays <= 0
                || minimumDeliveryDays > targetDays || maximumDepartureCostCredits <= 0 || maximumShipmentPreviews < 1 || maximumRepositionDays < 1)
            throw new IllegalArgumentException("Invalid logistics policy bounds");
    }
    private static final class Loaded {
        private static final LogisticsPolicy VALUE = load();
        private static LogisticsPolicy load() {
            try (var stream = LogisticsPolicy.class.getResourceAsStream("/logistics_policy.json")) {
                if (stream == null) throw new IOException("Missing logistics_policy.json");
                return new ObjectMapper().readValue(stream, LogisticsPolicy.class);
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
        }
    }
    public static LogisticsPolicy current() { return Loaded.VALUE; }
}
