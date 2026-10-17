package com.spaceconquest.engine.technology;

/** Explanation captured when a research action is chosen; it is not recomputed from later state. */
public record ResearchDecision(Actor actor, String reason, String goalApplicationId) {
    public enum Actor { AI, PLAYER, SIMULATION }
    public ResearchDecision {
        if (actor == null || reason == null || reason.isBlank()) throw new IllegalArgumentException("Research decision needs an actor and reason");
    }
}
