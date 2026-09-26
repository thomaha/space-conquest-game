package com.spaceconquest.engine.economy;

import java.util.Map;
import java.util.Objects;

/** Daily needs and persistent private credits for one body, race and profession group. */
public record HouseholdAccount(
        String bodyId,
        String systemId,
        String empireId,
        String raceId,
        String professionId,
        long headcount,
        double savingsCredits,
        double wageIncomeCredits,
        double welfareIncomeCredits,
        double incomeTaxPaidCredits,
        double marketSpendingCredits,
        Map<String, Double> unmetBasicKg,
        double secondaryNeedsMetFraction,
        double luxuryNeedsMetFraction,
        double electricitySpendingCredits,
        double unmetBasicElectricityKwh
) {
    public HouseholdAccount {
        Objects.requireNonNull(bodyId);
        Objects.requireNonNull(systemId);
        Objects.requireNonNull(empireId);
        Objects.requireNonNull(raceId);
        Objects.requireNonNull(professionId);
        if (headcount < 0 || !validCredits(savingsCredits) || !validCredits(wageIncomeCredits)
                || !validCredits(welfareIncomeCredits) || !validCredits(incomeTaxPaidCredits)
                || !validCredits(marketSpendingCredits)
                || !validCredits(electricitySpendingCredits)
                || !validCredits(unmetBasicElectricityKwh)
                || !validFraction(secondaryNeedsMetFraction) || !validFraction(luxuryNeedsMetFraction)) {
            throw new IllegalArgumentException("Invalid household counts, credits or need fulfillment");
        }
        unmetBasicKg = unmetBasicKg == null ? Map.of() : Map.copyOf(unmetBasicKg);
    }

    private static boolean validCredits(double credits) {
        return Double.isFinite(credits) && credits >= 0.0;
    }

    private static boolean validFraction(double fraction) {
        return Double.isFinite(fraction) && fraction >= 0.0 && fraction <= 1.0;
    }

    public String key() {
        return bodyId + "/" + raceId + "/" + professionId;
    }

    public HouseholdAccount withElectricitySettlement(double deliveredKwh, double spentCredits) {
        return new HouseholdAccount(bodyId, systemId, empireId, raceId, professionId, headcount,
                Math.max(0.0, savingsCredits - spentCredits), wageIncomeCredits, welfareIncomeCredits,
                incomeTaxPaidCredits, marketSpendingCredits, unmetBasicKg,
                secondaryNeedsMetFraction, luxuryNeedsMetFraction,
                electricitySpendingCredits + spentCredits,
                Math.max(0.0, unmetBasicElectricityKwh - deliveredKwh));
    }
}
