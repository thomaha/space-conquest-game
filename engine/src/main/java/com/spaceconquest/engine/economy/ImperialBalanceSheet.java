package com.spaceconquest.engine.economy;

/** Actual central treasury activity for one completed day and its outstanding imperial debt. */
public record ImperialBalanceSheet(
        String empireId,
        long turn,
        double incomeCredits,
        double expenditureCredits,
        double outstandingDebtCredits,
        double debtRepaidCredits
) {
    public ImperialBalanceSheet {
        if (incomeCredits < 0 || expenditureCredits < 0 || outstandingDebtCredits < 0 || debtRepaidCredits < 0) {
            throw new IllegalArgumentException("Imperial finance amounts cannot be negative");
        }
    }

    public double netBalanceCredits() {
        return incomeCredits - expenditureCredits;
    }
}
