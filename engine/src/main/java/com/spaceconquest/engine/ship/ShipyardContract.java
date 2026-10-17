package com.spaceconquest.engine.ship;

/** Frozen service provider and work rate. Only actual construction work earns a fee. */
public record ShipyardContract(String providerOwnerId, double feeCreditsPerWorkUnit, double paidFeesCredits) {
    public ShipyardContract {
        if (providerOwnerId == null || providerOwnerId.isBlank() || !Double.isFinite(feeCreditsPerWorkUnit) || feeCreditsPerWorkUnit < 0
                || !Double.isFinite(paidFeesCredits) || paidFeesCredits < 0) throw new IllegalArgumentException("Invalid shipyard contract");
    }
    public ShipyardContract withPayment(double fee) { return new ShipyardContract(providerOwnerId, feeCreditsPerWorkUnit, paidFeesCredits + fee); }
}
