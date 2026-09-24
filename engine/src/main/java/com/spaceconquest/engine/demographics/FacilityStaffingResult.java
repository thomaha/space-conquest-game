package com.spaceconquest.engine.demographics;

import java.util.Map;

/**
 * Detailed evaluation of facility or project multi-profession staffing requirements and bottlenecks.
 *
 * @param operationalEfficiency overall efficiency factor governed by the bottlenecked profession (0.0 to 1.0)
 * @param requiredHeadcounts    required personnel count per profession
 * @param assignedHeadcounts    available or assigned personnel count per profession
 * @param staffingRatios        staffing ratio per profession (available / required)
 * @param bottleneckProfession  the profession with the lowest staffing ratio
 * @param isFullyStaffed        true if all profession quotas are 100% satisfied
 */
public record FacilityStaffingResult(
        double operationalEfficiency,
        Map<String, Long> requiredHeadcounts,
        Map<String, Long> assignedHeadcounts,
        Map<String, Double> staffingRatios,
        String bottleneckProfession,
        boolean isFullyStaffed
) {}
