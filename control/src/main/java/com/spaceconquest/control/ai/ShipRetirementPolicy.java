package com.spaceconquest.control.ai;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
/** Decision preference; dismantling and recovery capabilities belong to hull applications. */
public record ShipRetirementPolicy(double minimumReplacementHullFraction) {
    public ShipRetirementPolicy { if (!Double.isFinite(minimumReplacementHullFraction) || minimumReplacementHullFraction <= 0 || minimumReplacementHullFraction > 1) throw new IllegalArgumentException("Invalid retirement condition threshold"); }
    private static final class Loaded {
        static final ShipRetirementPolicy VALUE = read();
        private static ShipRetirementPolicy read() {
            try (var stream = ShipRetirementPolicy.class.getResourceAsStream("/ship_retirement_policy.json")) {
                if (stream == null) throw new IOException("Missing retirement policy");
                return new ObjectMapper().readValue(stream, ShipRetirementPolicy.class);
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
        }
    }
    public static ShipRetirementPolicy current() { return Loaded.VALUE; }
}
