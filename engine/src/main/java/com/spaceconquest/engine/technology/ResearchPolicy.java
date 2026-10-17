package com.spaceconquest.engine.technology;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spaceconquest.engine.*;
import java.io.*;
import java.util.*;

/** Resource-defined research rules; projects retain their original quoted cost. */
public record ResearchPolicy(double foundationalPointsPerComplexity, double applicationPoints, double refinementPoints,
                             double exchangeProgressFraction, double exchangeSpeedBonus, List<Outcome> outcomes,
                             ResearchVarianceResult performance, ResearchVarianceResult miniaturization) {
    public record Outcome(double weight, ResearchVarianceResult result) {}
    public ResearchPolicy {
        outcomes = List.copyOf(outcomes);
        for (double cost : new double[]{foundationalPointsPerComplexity, applicationPoints, refinementPoints})
            if (!Double.isFinite(cost) || cost <= 0) throw new IllegalArgumentException("Invalid research cost");
        if (!Double.isFinite(exchangeProgressFraction) || exchangeProgressFraction < 0 || exchangeProgressFraction >= 1
                || !Double.isFinite(exchangeSpeedBonus) || exchangeSpeedBonus < 0 || outcomes.isEmpty())
            throw new IllegalArgumentException("Invalid research exchange policy");
        double total = 0;
        for (var outcome : outcomes) {
            if (!Double.isFinite(outcome.weight()) || outcome.weight() <= 0) throw new IllegalArgumentException("Invalid outcome weight");
            valid(outcome.result()); total += outcome.weight();
        }
        if (Math.abs(total - 1) > 1e-9) throw new IllegalArgumentException("Outcome weights must total one");
        valid(performance); valid(miniaturization);
    }
    private static void valid(ResearchVarianceResult result) {
        if (result == null || !Double.isFinite(result.effectMultiplier()) || result.effectMultiplier() <= 0
                || !Double.isFinite(result.costMultiplier()) || result.costMultiplier() <= 0
                || result.outcomeType() == null
                || !Set.of("CRITICAL_BREAKTHROUGH", "OPTIMIZED_SUCCESS", "INCREMENTAL_GAIN", "FLAWED_SETBACK").contains(result.outcomeType()))
            throw new IllegalArgumentException("Invalid research outcome");
    }
    private static final class Loaded {
        static final ResearchPolicy VALUE = load();
        private static ResearchPolicy load() {
            try (var stream = ResearchPolicy.class.getResourceAsStream("/research_policy.json")) {
                if (stream == null) throw new IOException("Missing research_policy.json");
                return new ObjectMapper().readValue(stream, ResearchPolicy.class);
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
        }
    }
    public static ResearchPolicy current() { return Loaded.VALUE; }
    public double cost(Empire empire, String target, boolean application, List<Technology> catalog) {
        if (!application) {
            var technology = catalog.stream().filter(value -> target.equals(value.id())).findFirst().orElseThrow();
            return technology.researchPoints() != null ? technology.researchPoints()
                    : foundationalPointsPerComplexity * Math.max(1, technology.complexity());
        }
        var app = catalog.stream().flatMap(value -> value.applications().stream())
                .filter(value -> target.equals(value.id())).findFirst().orElseThrow();
        boolean refinement = empire != null && empire.unlockedTechIds().contains(target);
        Double quoted = refinement ? app.refinementResearchPoints() : app.researchPoints();
        return quoted != null ? quoted : refinement ? refinementPoints : applicationPoints;
    }
    public ResearchVarianceResult roll(double value) {
        if (!Double.isFinite(value) || value < 0 || value >= 1) throw new IllegalArgumentException("Invalid research roll");
        double threshold = 0;
        for (var outcome : outcomes) { threshold += outcome.weight(); if (value < threshold) return outcome.result(); }
        return outcomes.getLast().result();
    }
}
