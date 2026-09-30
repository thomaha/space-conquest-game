package com.spaceconquest.engine.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HouseholdEmploymentTest {
    @Test
    void unemploymentPressurePersistsAndFallsWhenJobsReturn() {
        HouseholdEmployment first = HouseholdEmployment.none().settle(100, 10, 20);
        assertEquals(70, first.unemployedWorkers());
        assertEquals(0.035, first.unemploymentPressure(), 0.000001);

        HouseholdEmployment second = first.settle(100, 50, 50);
        assertEquals(0, second.unemployedWorkers());
        assertTrue(second.unemploymentPressure() > 0.0);
        assertTrue(second.unemploymentPressure() < first.unemploymentPressure());
    }
}
