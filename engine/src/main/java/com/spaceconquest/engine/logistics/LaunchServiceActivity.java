package com.spaceconquest.engine.logistics;

/** One launch's realized fee and reserved electricity, cleared after the daily settlement. */
public record LaunchServiceActivity(String providerId, String providerOwnerId,
                                    String bodyId, double feeCredits, double powerKwh,
                                    double powerCostCredits) {
    public LaunchServiceActivity withPowerCost(double actualCredits) {
        return new LaunchServiceActivity(providerId, providerOwnerId, bodyId,
                feeCredits, powerKwh, actualCredits);
    }
}
