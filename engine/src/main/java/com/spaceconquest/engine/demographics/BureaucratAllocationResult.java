package com.spaceconquest.engine.demographics;

import java.util.Map;

/**
 * Breakdown of administrative bureaucrat demand, assignments and efficiency ratios across public sectors.
 *
 * @param availableBureaucrats       total available bureaucrat personnel in the colony
 * @param totalDemand                total bureaucrat demand across all public sectors
 * @param demandBySector             demand headcount per sector
 * @param assignedBySector           assigned bureaucrat headcount per sector
 * @param administrativeRatios       administrative efficiency ratio per sector (0.0 to 1.0)
 * @param effectiveEfficiencies      effective sector efficiency factoring in red tape bottlenecks
 * @param isBottlenecked             true if available bureaucrats fall short of total demand
 */
public record BureaucratAllocationResult(
        long availableBureaucrats,
        long totalDemand,
        Map<String, Long> demandBySector,
        Map<String, Long> assignedBySector,
        Map<String, Double> administrativeRatios,
        Map<String, Double> effectiveEfficiencies,
        boolean isBottlenecked
) {}
