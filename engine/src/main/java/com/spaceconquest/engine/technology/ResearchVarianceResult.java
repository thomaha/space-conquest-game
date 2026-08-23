package com.spaceconquest.engine.technology;

/**
 * Encapsulates the stochastic outcome rolled upon research completion.
 *
 * @param outcomeType       one of CRITICAL_BREAKTHROUGH, OPTIMIZED_SUCCESS, INCREMENTAL_GAIN, FLAWED_SETBACK
 * @param effectMultiplier  multiplier applied to the output effect
 * @param costMultiplier    multiplier applied to material and work costs
 * @param complexityShift   shift applied to manufacturing complexity
 */
public record ResearchVarianceResult(
        String outcomeType,
        double effectMultiplier,
        double costMultiplier,
        int complexityShift
) {
    public static final String CRITICAL_BREAKTHROUGH = "CRITICAL_BREAKTHROUGH";
    public static final String OPTIMIZED_SUCCESS = "OPTIMIZED_SUCCESS";
    public static final String INCREMENTAL_GAIN = "INCREMENTAL_GAIN";
    public static final String FLAWED_SETBACK = "FLAWED_SETBACK";
}
