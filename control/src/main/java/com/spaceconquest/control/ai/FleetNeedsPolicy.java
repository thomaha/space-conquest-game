package com.spaceconquest.control.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;

/** Planning targets only; hardware capabilities remain application-defined. */
public record FleetNeedsPolicy(int defenseShipsPerSystem, int escortsPerConvoy, int wartimeReserveShips,
        int shipsPerDetectedEnemy, int maximumShipsPerSystem, int maximumBuildPreviews,
        double treasuryReserveCredits) {
    public FleetNeedsPolicy {
        if (defenseShipsPerSystem < 0 || escortsPerConvoy < 0 || wartimeReserveShips < 0 || shipsPerDetectedEnemy < 0
                || maximumShipsPerSystem < 1 || maximumBuildPreviews < 1 || !Double.isFinite(treasuryReserveCredits) || treasuryReserveCredits < 0)
            throw new IllegalArgumentException("Invalid fleet needs policy");
    }
    private static final class Loaded {
        static final FleetNeedsPolicy VALUE = read();
        static FleetNeedsPolicy read() {
            try (var stream = FleetNeedsPolicy.class.getResourceAsStream("/fleet_needs_policy.json")) {
                if (stream == null) throw new IOException("Missing fleet needs policy");
                return new ObjectMapper().readValue(stream, FleetNeedsPolicy.class);
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
        }
    }
    public static FleetNeedsPolicy current() { return Loaded.VALUE; }
}
