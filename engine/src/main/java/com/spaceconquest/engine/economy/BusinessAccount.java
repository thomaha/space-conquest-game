package com.spaceconquest.engine.economy;

/** Owner-level cash reserves and cumulative realized results. Reserves remain assets, not tax deductions. */
public record BusinessAccount(double replacementReserveCredits, double cumulativeOperatingResultCredits,
                              double cumulativeTaxPaidCredits, double cumulativeServicesPaidCredits,
                              double cumulativeReserveContributionsCredits, double externalServicesPaidToday,
                              double operatingResultToday, double reserveContributionToday, double settledShipyardProfitCredits,
                              double replacementFundingTargetCredits) {
    public BusinessAccount {
        for (double value : new double[]{replacementReserveCredits, cumulativeTaxPaidCredits, cumulativeServicesPaidCredits,
                cumulativeReserveContributionsCredits, externalServicesPaidToday, reserveContributionToday, replacementFundingTargetCredits})
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid business balance");
        if (!Double.isFinite(cumulativeOperatingResultCredits) || !Double.isFinite(operatingResultToday) || !Double.isFinite(settledShipyardProfitCredits))
            throw new IllegalArgumentException("Invalid business result");
    }
    public static BusinessAccount empty() { return new BusinessAccount(0, 0, 0, 0, 0, 0, 0, 0, 0, 0); }
    public BusinessAccount withReserve(double value) {
        return new BusinessAccount(value, cumulativeOperatingResultCredits, cumulativeTaxPaidCredits, cumulativeServicesPaidCredits,
                cumulativeReserveContributionsCredits, externalServicesPaidToday, operatingResultToday, reserveContributionToday, settledShipyardProfitCredits, replacementFundingTargetCredits);
    }
    public BusinessAccount withTransportPayment(double value) {
        return new BusinessAccount(replacementReserveCredits, cumulativeOperatingResultCredits, cumulativeTaxPaidCredits,
                cumulativeServicesPaidCredits, cumulativeReserveContributionsCredits, value, operatingResultToday, reserveContributionToday, settledShipyardProfitCredits, replacementFundingTargetCredits);
    }
    public BusinessAccount close(double result, double tax, double services, double reserve, double yardProfit, double fundingTarget) {
        return new BusinessAccount(replacementReserveCredits + reserve, cumulativeOperatingResultCredits + result,
                cumulativeTaxPaidCredits + tax, cumulativeServicesPaidCredits + services,
                cumulativeReserveContributionsCredits + reserve, externalServicesPaidToday, result, reserve, yardProfit, fundingTarget);
    }
}
