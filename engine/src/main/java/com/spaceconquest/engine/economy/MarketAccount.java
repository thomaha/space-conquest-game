package com.spaceconquest.engine.economy;

/** Hub trading cash: merchandise receipts and provisional launch-service fees fund stock purchases. */
public record MarketAccount(String hubId, double unsettledSalesCredits) {
    public MarketAccount {
        if (hubId == null || hubId.isBlank() || !Double.isFinite(unsettledSalesCredits)
                || unsettledSalesCredits < 0.0) {
            throw new IllegalArgumentException("Market account requires a hub and nonnegative finite credits");
        }
    }
}
