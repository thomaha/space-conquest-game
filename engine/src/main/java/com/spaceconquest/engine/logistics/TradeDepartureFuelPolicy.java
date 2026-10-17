package com.spaceconquest.engine.logistics;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.util.List;

/** Bounded planning preferences for protecting onward fuel, independent of hardware capabilities. */
public record TradeDepartureFuelPolicy(List<Double> reserveFractions) {
    public TradeDepartureFuelPolicy {
        reserveFractions = List.copyOf(reserveFractions);
        if (reserveFractions.isEmpty() || reserveFractions.size() > 16 || reserveFractions.stream().anyMatch(value -> !Double.isFinite(value) || value < 0 || value >= 1))
            throw new IllegalArgumentException("Invalid trade departure reserve candidates");
    }
    private static final class Loaded {
        static final TradeDepartureFuelPolicy VALUE = read();
        static TradeDepartureFuelPolicy read() {
            try (var stream = TradeDepartureFuelPolicy.class.getResourceAsStream("/trade_departure_fuel_policy.json")) {
                if (stream == null) throw new IOException("Missing trade departure fuel policy");
                return new ObjectMapper().readValue(stream, TradeDepartureFuelPolicy.class);
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
        }
    }
    public static TradeDepartureFuelPolicy current() { return Loaded.VALUE; }
}
