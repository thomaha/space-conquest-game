package com.spaceconquest.engine.economy;

/** Daily need coverage and accumulated stress for one household group. */
public record HouseholdWellbeing(
        double materialCoverage,
        double electricityCoverage,
        double wellbeingIndex,
        double annualShortfallDays,
        int observedDays
) {
    private static final double DAILY_ADJUSTMENT = 0.05;

    public HouseholdWellbeing {
        if (!fraction(materialCoverage) || !fraction(electricityCoverage)
                || !fraction(wellbeingIndex) || !Double.isFinite(annualShortfallDays)
                || annualShortfallDays < 0.0 || observedDays < 0) {
            throw new IllegalArgumentException("Invalid household wellbeing");
        }
    }

    public static HouseholdWellbeing healthy() {
        return new HouseholdWellbeing(1.0, 1.0, 1.0, 0.0, 0);
    }

    public HouseholdWellbeing withMaterialCoverage(double coverage) {
        return new HouseholdWellbeing(clamp(coverage), electricityCoverage, wellbeingIndex,
                annualShortfallDays, observedDays);
    }

    public HouseholdWellbeing settle(double electricityMet, double secondaryMet, double luxuryMet,
                                    double unemploymentPressure) {
        double power = clamp(electricityMet);
        double daily = 0.70 * materialCoverage + 0.15 * power
                + 0.10 * clamp(secondaryMet) + 0.05 * clamp(luxuryMet)
                - 0.10 * clamp(unemploymentPressure);
        double stress = Math.clamp(1.0 - materialCoverage + 0.25 * (1.0 - power), 0.0, 1.0);
        return new HouseholdWellbeing(materialCoverage, power,
                clamp(wellbeingIndex * (1.0 - DAILY_ADJUSTMENT) + daily * DAILY_ADJUSTMENT),
                annualShortfallDays + stress, observedDays + 1);
    }

    public double annualAverageShortfall() {
        return observedDays == 0 ? 0.0
                : Math.clamp(annualShortfallDays / Math.max(365, observedDays), 0.0, 1.0);
    }

    public HouseholdWellbeing resetAnnualShortfall() {
        return new HouseholdWellbeing(materialCoverage, electricityCoverage, wellbeingIndex, 0.0, 0);
    }

    private static boolean fraction(double value) {
        return Double.isFinite(value) && value >= 0.0 && value <= 1.0;
    }

    private static double clamp(double value) {
        return Math.clamp(value, 0.0, 1.0);
    }
}
