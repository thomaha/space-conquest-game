package com.spaceconquest.frontend.empire;

import java.util.List;

public record EmpireEconomyReport(
        double treasuryCredits,
        double corporateTaxRate,
        long totalPopulation,
        int colonizedWorldCount,
        int controlledSystemCount,
        double colonialTaxIncome,
        double stateIndustryIncome,
        double corporateTariffIncome,
        double spaceElevatorIncome,
        double miningRoyaltiesIncome,
        double totalIncome,
        double governanceExpenses,
        double ministryBudgets,
        double infrastructureMaintenance,
        double orbitalStationMaintenance,
        double researchSubsidies,
        double terraformingSubsidies,
        double totalCosts,
        double netBudgetBalance,
        List<ColonyEconomyEntry> colonyEntries,
        List<CorporateEconomyEntry> corporateEntries
) {}
