package com.spaceconquest.control.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.util.*;

/** Configurable, bounded system-economy decision rules rather than weapon or application balance. */
public record EconomicPolicy(int reviewIntervalTurns, double allocationStep, double taxStep, double budgetStepFraction,
        double minimumBudgetCredits, double maximumBudgetCredits, double budgetPerResident,
        double minimumTaxRate, double maximumTaxRate, double baselineTaxRate,
        double maximumTransferRate, double transferStep, double localReserveDays,
        double treasuryReserveCredits, double subsidyCashHorizonDays, double hardshipCoverageThreshold, double fiscalDeadbandFraction,
        Map<String, Double> baseWeights, Map<String, Double> needWeights) {
    public EconomicPolicy {
        baseWeights = Map.copyOf(baseWeights); needWeights = Map.copyOf(needWeights);
        for (double value : new double[]{allocationStep, taxStep, budgetStepFraction, minimumBudgetCredits, maximumBudgetCredits,
                budgetPerResident, minimumTaxRate, maximumTaxRate, baselineTaxRate, maximumTransferRate, transferStep,
                localReserveDays, treasuryReserveCredits, subsidyCashHorizonDays, hardshipCoverageThreshold, fiscalDeadbandFraction})
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid economic policy");
        if (reviewIntervalTurns < 1 || allocationStep <= 0 || allocationStep > 1 || taxStep <= 0 || taxStep > .5
                || budgetStepFraction <= 0 || budgetStepFraction > 1 || maximumBudgetCredits < minimumBudgetCredits
                || minimumTaxRate > baselineTaxRate || baselineTaxRate > maximumTaxRate || maximumTaxRate > .5
                || maximumTransferRate > 1 || transferStep <= 0 || transferStep > 1 || localReserveDays < 1
                || subsidyCashHorizonDays < 1 || hardshipCoverageThreshold > 1 || fiscalDeadbandFraction > 1) throw new IllegalArgumentException("Invalid economic policy bounds");
        for (String sector : List.of("education", "law", "health", "infrastructure", "militia"))
            if (!baseWeights.containsKey(sector) || !needWeights.containsKey(sector)
                    || !Double.isFinite(baseWeights.get(sector)) || baseWeights.get(sector) <= 0
                    || !Double.isFinite(needWeights.get(sector)) || needWeights.get(sector) < 0)
                throw new IllegalArgumentException("Invalid economic sector weight");
    }
    private static final class Loaded {
        private static final EconomicPolicy VALUE = load();
        private static EconomicPolicy load() {
            try (var stream = EconomicPolicy.class.getResourceAsStream("/economic_policy.json")) {
                if (stream == null) throw new IOException("Missing economic_policy.json");
                return new ObjectMapper().readValue(stream, EconomicPolicy.class);
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
        }
    }
    public static EconomicPolicy current() { return Loaded.VALUE; }
}
