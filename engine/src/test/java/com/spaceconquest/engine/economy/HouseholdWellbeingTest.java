package com.spaceconquest.engine.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HouseholdWellbeingTest {
    @Test
    void prolongedShortageAccumulatesStressAndRecoveryTakesTime() {
        HouseholdWellbeing state = HouseholdWellbeing.healthy();
        for (int day = 0; day < 30; day++) {
            state = state.withMaterialCoverage(0.0).settle(0.0, 0.0, 0.0, 0.0);
        }
        assertEquals(30.0 / 365.0, state.annualAverageShortfall(), 0.000001);
        assertTrue(state.wellbeingIndex() < 0.3);

        HouseholdWellbeing recovered = state.withMaterialCoverage(1.0).settle(1.0, 1.0, 1.0, 0.0);
        assertTrue(recovered.wellbeingIndex() > state.wellbeingIndex());
        assertTrue(recovered.wellbeingIndex() < 1.0);
        assertEquals(30.0 / 365.0, recovered.annualAverageShortfall(), 0.000001);
        assertEquals(0.0, recovered.resetAnnualShortfall().annualAverageShortfall(), 0.000001);
    }

    @Test
    void luxuryShortageReducesWellbeingWithoutCausingMortalityStress() {
        HouseholdWellbeing result = HouseholdWellbeing.healthy()
                .withMaterialCoverage(1.0).settle(1.0, 1.0, 0.0, 0.0);
        assertTrue(result.wellbeingIndex() < 1.0);
        assertEquals(0.0, result.annualAverageShortfall(), 0.000001);
    }
}
