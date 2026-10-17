package com.spaceconquest.engine.industry;

/** Paid cultivation accumulated until harvest; saved independently of daily transaction receipts. */
public record FarmingState(String applicationId, long startedTurn, long lastWorkedTurn, double cultivatedBatches) {
    public FarmingState {
        if (applicationId == null || !Double.isFinite(cultivatedBatches) || cultivatedBatches < 0 || lastWorkedTurn < -1
                || cultivatedBatches > 0 && (applicationId.isBlank() || startedTurn < 0 || lastWorkedTurn < startedTurn)
                || cultivatedBatches == 0 && startedTurn != -1)
            throw new IllegalArgumentException("Invalid cultivation state");
    }
    public static FarmingState empty() { return new FarmingState("", -1, -1, 0); }
    public FarmingState cultivate(String application, long turn, double batches) {
        if (turn <= lastWorkedTurn || batches <= 0 || !Double.isFinite(batches)
                || cultivatedBatches > 0 && !applicationId.equals(application))
            throw new IllegalArgumentException("Invalid cultivation installment");
        return new FarmingState(application, cultivatedBatches == 0 ? turn : startedTurn, turn, cultivatedBatches + batches);
    }
    public boolean mature(long turn, FarmingDefinition definition) {
        return cultivatedBatches > 0 && turn - startedTurn + 1 >= definition.growingDays();
    }
    public FarmingState harvested() { return new FarmingState(applicationId, -1, lastWorkedTurn, 0); }
}
