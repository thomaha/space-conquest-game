package com.spaceconquest.control.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;

/** Decision limits are independent of application-defined yard capabilities. */
public record ShipyardInvestmentPolicy(int observationTurns, int maximumYardPreviews,
        double minimumQueuedDays, double minimumUtilization, double maximumInvestmentCredits,
        double empireReserveCredits, double corporateReserveCredits, double payrollReserveDays,
        double minimumProfitCredits, double maximumForecastDays, int maximumSurfaceTierIncrease) {
    public ShipyardInvestmentPolicy {
        if (observationTurns < 1 || maximumYardPreviews < 1 || maximumSurfaceTierIncrease < 1) throw new IllegalArgumentException("Invalid yard observation limit");
        for (double value : new double[]{minimumQueuedDays, minimumUtilization, maximumInvestmentCredits,
                empireReserveCredits, corporateReserveCredits, payrollReserveDays, minimumProfitCredits, maximumForecastDays})
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid yard investment limit");
        if (minimumQueuedDays <= 0 || minimumUtilization <= 0 || minimumUtilization > 1
                || maximumInvestmentCredits <= 0 || payrollReserveDays <= 0 || maximumForecastDays < minimumQueuedDays)
            throw new IllegalArgumentException("Invalid yard investment bounds");
    }
    private static final class Loaded {
        static final ShipyardInvestmentPolicy VALUE = read();
        static ShipyardInvestmentPolicy read() {
            try (var stream = ShipyardInvestmentPolicy.class.getResourceAsStream("/shipyard_investment_policy.json")) {
                if (stream == null) throw new IOException("Missing yard investment policy");
                return new ObjectMapper().readValue(stream, ShipyardInvestmentPolicy.class);
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
        }
    }
    public static ShipyardInvestmentPolicy current() { return Loaded.VALUE; }
}
