package com.spaceconquest.control.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;

/** Independent transport investment limits; production and ship capability values remain application-defined. */
public record TransportConstructionPolicy(int observationTurns, int maximumCarriersPerEmpire, int maximumBuildPreviews,
        double maximumBuildCostCredits, double empireReserveCredits, double corporateReserveCredits,
        double minimumOperatingProfitCredits, double maximumPaybackShipments) {
    public TransportConstructionPolicy {
        if (observationTurns < 1 || maximumCarriersPerEmpire < 1 || maximumBuildPreviews < 1) throw new IllegalArgumentException("Invalid transport policy");
        for (double value : new double[]{maximumBuildCostCredits, empireReserveCredits, corporateReserveCredits, minimumOperatingProfitCredits, maximumPaybackShipments})
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid transport limit");
        if (maximumBuildCostCredits == 0 || maximumPaybackShipments == 0) throw new IllegalArgumentException("Invalid transport bounds");
    }
    private static final class Loaded {
        static final TransportConstructionPolicy VALUE = read();
        private static TransportConstructionPolicy read() {
            try (var stream = TransportConstructionPolicy.class.getResourceAsStream("/transport_construction_policy.json")) {
                if (stream == null) throw new IOException("Missing transport construction policy");
                return new ObjectMapper().readValue(stream, TransportConstructionPolicy.class);
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
        }
    }
    public static TransportConstructionPolicy current() { return Loaded.VALUE; }
}
