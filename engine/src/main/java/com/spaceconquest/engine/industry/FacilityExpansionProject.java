package com.spaceconquest.engine.industry;

import java.util.Map;

/**
 * Tracks an active industrial scaling expansion project for a facility.
 *
 * @param projectId              unique expansion project identifier
 * @param facilityId             referenced IndustrialFacility ID
 * @param targetTier             tier being upgraded to
 * @param accumulatedWorkHours   work hours completed
 * @param requiredWorkHours      total work hours needed
 * @param costCredits            monetary capital cost
 * @param requiredMaterialsKg     physical construction bill
 * @param consumedMaterialsKg     materials already incorporated into the project
 */
public record FacilityExpansionProject(
        String projectId,
        String facilityId,
        int targetTier,
        double accumulatedWorkHours,
        double requiredWorkHours,
        double costCredits,
        Map<String, Double> requiredMaterialsKg,
        Map<String, Double> consumedMaterialsKg
) {
    public FacilityExpansionProject {
        requiredMaterialsKg = requiredMaterialsKg == null ? Map.of() : Map.copyOf(requiredMaterialsKg);
        consumedMaterialsKg = consumedMaterialsKg == null ? Map.of() : Map.copyOf(consumedMaterialsKg);
    }

    public FacilityExpansionProject(String projectId, String facilityId, int targetTier,
                                    double accumulatedWorkHours, double requiredWorkHours,
                                    double costCredits) {
        this(projectId, facilityId, targetTier, accumulatedWorkHours, requiredWorkHours,
                costCredits, Map.of(), Map.of());
    }

    public boolean isComplete() {
        return accumulatedWorkHours >= requiredWorkHours && requiredMaterialsKg.entrySet().stream()
                .allMatch(entry -> consumedMaterialsKg.getOrDefault(entry.getKey(), 0.0)
                        + 0.000001 >= entry.getValue());
    }

    public double getProgressPercentage() {
        if (requiredWorkHours <= 0.0) return 100.0;
        return Math.min(100.0, (accumulatedWorkHours / requiredWorkHours) * 100.0);
    }
}
