package com.spaceconquest.engine.ship;

/** Saved purpose for an AI build and its paid commissioning handoff. */
public record ShipDeploymentIntent(String ownerId, Kind kind, String targetId, String materialId,
        long createdTurn, String status, String transitSystemId, boolean transitComplete) {
    public enum Kind { DEFENSE, REINFORCEMENT, ESCORT, SUPPLY, TRADE }
    public ShipDeploymentIntent {
        transitSystemId = transitSystemId == null ? "" : transitSystemId;
        if (ownerId == null || kind == null || targetId == null || materialId == null || createdTurn < 0 || status == null)
            throw new IllegalArgumentException("Invalid ship deployment intent");
    }
    public ShipDeploymentIntent(String ownerId, Kind kind, String targetId, String materialId, long createdTurn, String status) {
        this(ownerId, kind, targetId, materialId, createdTurn, status, "", false);
    }
    public ShipDeploymentIntent status(String value) { return new ShipDeploymentIntent(ownerId, kind, targetId, materialId, createdTurn, value, transitSystemId, transitComplete); }
    public ShipDeploymentIntent transit(String system, String value) { return new ShipDeploymentIntent(ownerId, kind, targetId, materialId, createdTurn, value, system, false); }
    public ShipDeploymentIntent arrived() { return new ShipDeploymentIntent(ownerId, kind, targetId, materialId, createdTurn, "Arrived; revalidating deployment target", transitSystemId, true); }
}
