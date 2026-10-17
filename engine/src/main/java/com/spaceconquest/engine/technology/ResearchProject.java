package com.spaceconquest.engine.technology;

/**
 * Represents an active scientific research endeavor by an empire.
 *
 * @param id                  unique project identifier
 * @param empireId            sovereign empire funding the research
 * @param targetTechOrAppId   identifier of the technology or practical application being researched
 * @param isApplication       true if researching an application optimization, false for foundational technology
 * @param accumulatedPoints   total research points accumulated so far
 * @param requiredPoints      threshold of points required for completion
 * @param assignedScientists  headcount of scientists allocated to the project
 * @param speedModifier       aggregate speed multiplier from accords, ministers and intelligence
 */
public record ResearchProject(
        String id,
        String empireId,
        String targetTechOrAppId,
        boolean isApplication,
        double accumulatedPoints,
        double requiredPoints,
        int assignedScientists,
        double speedModifier,
        ResearchDecision decision
) {
    public ResearchProject(String id, String empireId, String targetTechOrAppId, boolean isApplication,
                           double accumulatedPoints, double requiredPoints, int assignedScientists, double speedModifier) {
        this(id, empireId, targetTechOrAppId, isApplication, accumulatedPoints, requiredPoints, assignedScientists, speedModifier, null);
    }
    public boolean isComplete() {
        return accumulatedPoints >= requiredPoints;
    }

    public double getProgressPercentage() {
        if (requiredPoints <= 0.0) return 100.0;
        return Math.clamp((accumulatedPoints / requiredPoints) * 100.0, 0.0, 100.0);
    }
}
