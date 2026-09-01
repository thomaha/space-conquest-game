package com.spaceconquest.frontend.empire;

import com.spaceconquest.engine.economy.SystemEconomy;
import java.util.List;

public record SystemEconomyReport(
        String systemId,
        String systemName,
        String empireId,
        long systemPopulation,
        int colonizedBodiesCount,
        double grossSystemOutput,
        double colonialTaxes,
        double corporateTariffs,
        double spaceElevatorFees,
        double miningRoyalties,
        double stateIndustryIncome,
        double totalRevenues,
        double publicSectorFunding,
        double governorAdministration,
        double stationMaintenance,
        double totalExpenditures,
        double netSystemBalance,
        double taxRate,
        SystemEconomy economy
) {}
