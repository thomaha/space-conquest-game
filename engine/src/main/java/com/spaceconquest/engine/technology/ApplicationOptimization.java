package com.spaceconquest.engine.technology;

/** A selected optimization path and its calculated modifiers for one empire application. */
public record ApplicationOptimization(
        String empireId,
        String applicationId,
        String pathChoice,
        ResearchVarianceResult result,
        ResearchVarianceResult pendingOutcome,
        int completedRefinements
) {
    public ApplicationOptimization {
        if (result == null) result = new ResearchVarianceResult("BASELINE", 1.0, 1.0, 0);
        if (pathChoice == null) pathChoice = "NONE";
        if (completedRefinements < 0) throw new IllegalArgumentException("Negative refinement count");
    }

    public ApplicationOptimization(String empireId, String applicationId, String pathChoice,
                                   ResearchVarianceResult result) {
        this(empireId, applicationId, pathChoice, result, null, 0);
    }

    public boolean canChoosePath() {
        return pendingOutcome != null
                ? ResearchVarianceResult.OPTIMIZED_SUCCESS.equals(pendingOutcome.outcomeType())
                : completedRefinements == 0;
    }

    public ApplicationOptimization resolve(ResearchVarianceResult improvement, String path) {
        if (pendingOutcome == null) return new ApplicationOptimization(empireId, applicationId, path, improvement);
        ResearchVarianceResult combined = new ResearchVarianceResult(improvement.outcomeType(),
                result.effectMultiplier() * improvement.effectMultiplier(),
                result.costMultiplier() * improvement.costMultiplier(),
                result.complexityShift() + improvement.complexityShift());
        return new ApplicationOptimization(empireId, applicationId, path, combined, null, completedRefinements + 1);
    }
}
