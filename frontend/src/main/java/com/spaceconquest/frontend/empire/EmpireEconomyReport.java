package com.spaceconquest.frontend.empire;

import java.util.List;

public record EmpireEconomyReport(
        double treasuryCredits,
        double outstandingDebtCredits,
        double debtRepaidCredits,
        double corporateTaxRate,
        long totalPopulation,
        int colonizedWorldCount,
        int controlledSystemCount,
        double totalIncome,
        double totalCosts,
        double netBudgetBalance,
        List<ColonyEconomyEntry> colonyEntries,
        List<CorporateEconomyEntry> corporateEntries
) {}
