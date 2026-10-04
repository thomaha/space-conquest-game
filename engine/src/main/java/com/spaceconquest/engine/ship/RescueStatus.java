package com.spaceconquest.engine.ship;

/** Last rescue request and its tick-owned outcome, shared by donor and receiver snapshots. */
public record RescueStatus(String rescuerFleetId, RescueOrder order, Phase phase, long turn, String explanation) {
    public enum Phase { APPROACHING, DELIVERED, MISSED_CONTACT, TRANSFER_REJECTED, POWER_FAILED }
    public RescueStatus {
        if (rescuerFleetId == null || order == null || phase == null || explanation == null)
            throw new IllegalArgumentException("Invalid rescue status");
    }
}
