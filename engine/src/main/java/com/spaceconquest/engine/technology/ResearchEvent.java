package com.spaceconquest.engine.technology;

/** Immutable receipt emitted only by successful commands or actual completion. */
public record ResearchEvent(long turn, String empireId, String projectId, String targetId, Kind kind,
                            ResearchDecision decision, ResearchVarianceResult outcome) {
    public enum Kind { STARTED, REASSIGNED, PAUSED, RESUMED, CANCELLED, COMPLETED, ACCEPTED, DISCARDED, OPTIMIZED }
    public ResearchEvent {
        if (empireId == null || targetId == null || kind == null || decision == null) throw new IllegalArgumentException("Invalid research receipt");
    }
}
