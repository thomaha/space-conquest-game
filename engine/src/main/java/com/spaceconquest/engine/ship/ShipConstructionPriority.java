package com.spaceconquest.engine.ship;

/** Saved AI queue decision and ready-wait credit. Actual work clears starvation protection. */
public record ShipConstructionPriority(String empireId, long queuedTurn, long rankedTurn,
        int waitingTurns, double score, boolean ready, boolean protectedTurn, String reason) {
    public ShipConstructionPriority {
        if (empireId == null || reason == null || queuedTurn < 0 || rankedTurn < queuedTurn || waitingTurns < 0
                || !Double.isFinite(score) || score < 0 || protectedTurn && !ready)
            throw new IllegalArgumentException("Invalid ship construction priority");
    }
    public ShipConstructionPriority worked() {
        return new ShipConstructionPriority(empireId, queuedTurn, rankedTurn, 0, score, ready, false, reason);
    }
}
