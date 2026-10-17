package com.spaceconquest.engine.ship;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;

/** Configurable domestic corporate yard access and queue admission limits. */
public record ShipyardAccessPolicy(boolean publicYardsOpen, boolean corporateYardsOpen, double feeCreditsPerWorkUnit, double maximumQueuedWorkDays, int maximumHistoryEntries) {
    public ShipyardAccessPolicy {
        if (!Double.isFinite(feeCreditsPerWorkUnit) || feeCreditsPerWorkUnit < 0 || !Double.isFinite(maximumQueuedWorkDays) || maximumQueuedWorkDays <= 0 || maximumHistoryEntries < 1)
            throw new IllegalArgumentException("Invalid shipyard access policy");
    }
    public ShipyardAccessPolicy(boolean publicYardsOpen, boolean corporateYardsOpen, double feeCreditsPerWorkUnit, double maximumQueuedWorkDays) {
        this(publicYardsOpen, corporateYardsOpen, feeCreditsPerWorkUnit, maximumQueuedWorkDays, current().maximumHistoryEntries());
    }
    private static final class Loaded {
        static final ShipyardAccessPolicy VALUE = read();
        private static ShipyardAccessPolicy read() {
            try (var stream = ShipyardAccessPolicy.class.getResourceAsStream("/shipyard_access_policy.json")) {
                if (stream == null) throw new IOException("Missing shipyard access policy");
                return new ObjectMapper().readValue(stream, ShipyardAccessPolicy.class);
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
        }
    }
    public static ShipyardAccessPolicy current() { return Loaded.VALUE; }
}
