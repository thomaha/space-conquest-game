package com.spaceconquest.control.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;

/** Configurable planning weights, not weapon capabilities. */
public record ConstructionPriorityPolicy(double wartimeCombat, double peacetimeCombat, double essentialSupply,
        double replacement, double completion, double waiting, double suppression, double electronicWeight,
        double pointDefenseWeight, double contractWeight, double maximumContractScore, double maximumScore,
        double replacementHullFraction, int starvationWaitTurns, int maximumOrdersPerYard, double peacetimeSuppressionFraction) {
    public ConstructionPriorityPolicy {
        for (double value : new double[]{wartimeCombat, peacetimeCombat, essentialSupply, replacement, completion, waiting,
                suppression, electronicWeight, pointDefenseWeight, contractWeight, maximumContractScore, maximumScore, replacementHullFraction, peacetimeSuppressionFraction})
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid construction priority weight");
        if (maximumScore <= 0 || replacementHullFraction > 1 || starvationWaitTurns < 1 || maximumOrdersPerYard < 1)
            throw new IllegalArgumentException("Invalid construction priority bounds");
    }
    private static final class Loaded {
        static final ConstructionPriorityPolicy VALUE = read();
        static ConstructionPriorityPolicy read() {
            try (var stream = ConstructionPriorityPolicy.class.getResourceAsStream("/construction_priority_policy.json")) {
                if (stream == null) throw new IOException("Missing construction priority policy");
                return new ObjectMapper().readValue(stream, ConstructionPriorityPolicy.class);
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
        }
    }
    public static ConstructionPriorityPolicy current() { return Loaded.VALUE; }
}
