package com.spaceconquest.engine.economy;

/** Last day's filled jobs and persistent unemployment pressure for a household group. */
public record HouseholdEmployment(
        long workingAge,
        long publicWorkers,
        long industryWorkers,
        double unemploymentPressure
) {
    public HouseholdEmployment {
        if (workingAge < 0 || publicWorkers < 0 || industryWorkers < 0
                || publicWorkers + industryWorkers > workingAge
                || !Double.isFinite(unemploymentPressure)
                || unemploymentPressure < 0.0 || unemploymentPressure > 1.0) {
            throw new IllegalArgumentException("Invalid household employment");
        }
    }

    public static HouseholdEmployment none() {
        return new HouseholdEmployment(0, 0, 0, 0.0);
    }

    public HouseholdEmployment settle(long working, long publicHired, long industryHired) {
        long available = Math.max(0L, working);
        long publicCount = Math.clamp(publicHired, 0L, available);
        long industryCount = Math.clamp(industryHired, 0L, available - publicCount);
        double unemployedShare = available == 0L ? 0.0
                : (double) (available - publicCount - industryCount) / available;
        double pressure = unemploymentPressure * 0.95 + unemployedShare * 0.05;
        return new HouseholdEmployment(available, publicCount, industryCount, pressure);
    }

    public long unemployedWorkers() {
        return workingAge - publicWorkers - industryWorkers;
    }
}
