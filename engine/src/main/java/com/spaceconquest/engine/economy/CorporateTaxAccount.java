package com.spaceconquest.engine.economy;

/** Persistent corporate loss carryforward and unpaid profit-tax liability. */
public record CorporateTaxAccount(String corporationId, double lossCarryforwardCredits,
                                  double unpaidTaxCredits, double taxableProfitCredits,
                                  double assessedTaxCredits, double paidTaxCredits, double settledShipyardProfitCredits) {
    public CorporateTaxAccount(String corporationId, double lossCarryforwardCredits, double unpaidTaxCredits, double taxableProfitCredits,
                               double assessedTaxCredits, double paidTaxCredits) {
        this(corporationId, lossCarryforwardCredits, unpaidTaxCredits, taxableProfitCredits, assessedTaxCredits, paidTaxCredits, 0);
    }
    public CorporateTaxAccount {
        if (corporationId == null || corporationId.isBlank()
                || lossCarryforwardCredits < 0.0 || unpaidTaxCredits < 0.0
                || taxableProfitCredits < 0.0 || assessedTaxCredits < 0.0 || paidTaxCredits < 0.0 || !Double.isFinite(settledShipyardProfitCredits)) {
            throw new IllegalArgumentException("Invalid corporate tax account");
        }
    }
}
