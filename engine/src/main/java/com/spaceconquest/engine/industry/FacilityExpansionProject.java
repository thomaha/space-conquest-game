package com.spaceconquest.engine.industry;

/**
 * Tracks an active industrial scaling expansion project for a facility.
 *
 * @param projectId              unique expansion project identifier
 * @param facilityId             referenced IndustrialFacility ID
 * @param targetTier             tier being upgraded to
 * @param accumulatedWorkHours   work hours completed
 * @param requiredWorkHours      total work hours needed
 * @param costCredits            monetary capital cost
 */
public record FacilityExpansionProject(
        String projectId,
        String facilityId,
        int targetTier,
        double accumulatedWorkHours,
        double requiredWorkHours,
        double costCredits
) {
    public boolean isComplete() {
        return accumulatedWorkHours >= requiredWorkHours;
    }

    public double getProgressPercentage() {
        if (requiredWorkHours <= 0.0) return 100.0;
        return Math.min(100.0, (accumulatedWorkHours / requiredWorkHours) * 100.0);
    }
}
