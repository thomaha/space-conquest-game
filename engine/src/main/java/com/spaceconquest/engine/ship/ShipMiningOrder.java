package com.spaceconquest.engine.ship;

/** Saved extraction intent. Travel and sale remain separately paid commands. */
public record ShipMiningOrder(String ownerId, String shipId, String depositId, long lastWorkedTurn,
        double extractedKg, String status) {
    public ShipMiningOrder {
        if (ownerId == null || shipId == null || depositId == null || ownerId.isBlank() || shipId.isBlank() || depositId.isBlank()
                || !Double.isFinite(extractedKg) || extractedKg < 0) throw new IllegalArgumentException("Invalid mining order");
        status = status == null ? "" : status;
    }
}
