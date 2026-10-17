package com.spaceconquest.engine.ship;

/** Actual paid work or a saved operational blocker; client materials are not provider sales. */
public record ShipyardReceipt(long turn, String orderId, String customerId, String providerId, String systemId, String yardId,
        double workUnits, double feeCredits, double wageCostsCredits, boolean completed, String status) {
    public ShipyardReceipt {
        if (turn < 0 || orderId == null || customerId == null || providerId == null || providerId.isBlank() || systemId == null || yardId == null || status == null)
            throw new IllegalArgumentException("Invalid shipyard receipt identity");
        for (double value : new double[]{workUnits, feeCredits, wageCostsCredits})
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid shipyard receipt amount");
    }
}
