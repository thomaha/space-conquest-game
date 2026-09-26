package com.spaceconquest.engine.economy;

/** Hub trading cash: retail receipts fund purchases from identified facility producers. */
public record MarketAccount(String hubId, double unsettledSalesCredits) {
    public MarketAccount {
        if (hubId == null || hubId.isBlank() || !Double.isFinite(unsettledSalesCredits)
                || unsettledSalesCredits < 0.0) {
            throw new IllegalArgumentException("Market account requires a hub and nonnegative finite credits");
        }
    }
}
