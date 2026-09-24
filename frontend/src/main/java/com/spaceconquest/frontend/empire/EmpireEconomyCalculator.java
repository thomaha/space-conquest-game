package com.spaceconquest.frontend.empire;

import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.Moon;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.SystemGovernor;
import com.spaceconquest.engine.economy.PlanetaryBalanceSheet;
import com.spaceconquest.engine.economy.ImperialBalanceSheet;
import com.spaceconquest.engine.economy.SystemEconomy;
import com.spaceconquest.frontend.PlanetaryBodyEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EmpireEconomyCalculator {
    public static EmpireEconomyReport calculateEmpireEconomyReport(EmpireView view) {
        Empire playerEmpire = view.getPlayerEmpire();
        double treasury = playerEmpire != null ? playerEmpire.treasuryCredits() : 0.0;
        double corpTaxRate = playerEmpire != null ? playerEmpire.corporateTaxRate() : 0.0;
        List<String> controlledSystems = playerEmpire != null ? playerEmpire.controlledSystemIds() : List.of();

        List<PlanetaryBodyEntry> allBodies = view.getAllPlanetaryBodies();
        List<PlanetaryBodyEntry> playerColonies = allBodies.stream()
                .filter(PlanetaryBodyEntry::isColonized)
                .filter(b -> controlledSystems.isEmpty() || controlledSystems.contains(b.systemId()))
                .toList();

        long totalPop = playerColonies.stream().mapToLong(PlanetaryBodyEntry::totalPopulation).sum();
        int colonizedCount = playerColonies.size();
        int controlledSysCount = calculateControlledSystemCount(playerEmpire, playerColonies);

        List<ColonyEconomyEntry> colonyLedger = new ArrayList<>();
        calculateColonyLedger(view, playerColonies, colonyLedger);

        List<CorporateEconomyEntry> corporateLedger = new ArrayList<>();
        calculateCorporateLedger(view, corpTaxRate, corporateLedger);
        ImperialBalanceSheet actual = view.getImperialBalanceSheets().stream()
                .filter(sheet -> playerEmpire != null && playerEmpire.id().equals(sheet.empireId()))
                .findFirst().orElse(null);
        double totalIncome = actual != null ? actual.incomeCredits() : 0.0;
        double totalCosts = actual != null ? actual.expenditureCredits() : 0.0;
        double netBalance = totalIncome - totalCosts;

        return new EmpireEconomyReport(
                treasury, actual != null ? actual.outstandingDebtCredits() : 0.0,
                actual != null ? actual.debtRepaidCredits() : 0.0,
                corpTaxRate, totalPop, colonizedCount, controlledSysCount,
                totalIncome, totalCosts, netBalance,
                colonyLedger, corporateLedger
        );
    }

    private static int calculateControlledSystemCount(Empire empire, List<PlanetaryBodyEntry> colonies) {
        int count = (int) colonies.stream().map(PlanetaryBodyEntry::systemId).distinct().count();
        if (empire != null && !empire.controlledSystemIds().isEmpty()) {
            count = Math.max(count, empire.controlledSystemIds().size());
        }
        return count;
    }

    private static void calculateColonyLedger(EmpireView view, List<PlanetaryBodyEntry> colonies, List<ColonyEconomyEntry> ledger) {
        Map<String, PlanetaryBalanceSheet> sheetMap = new HashMap<>();
        if (view != null && view.getPlanetaryBalanceSheets() != null) {
            for (PlanetaryBalanceSheet sheet : view.getPlanetaryBalanceSheets()) {
                sheetMap.put(sheet.planetId().toLowerCase(), sheet);
            }
        }

        for (PlanetaryBodyEntry colony : colonies) {
            long pop = colony.totalPopulation();
            PlanetaryBalanceSheet sheet = sheetMap.get(colony.id().toLowerCase());
            double grossOutput;
            double taxCollected;
            double localGov;
            double net;
            double debt;

            if (sheet != null) {
                grossOutput = sheet.grossPlanetaryProduct();
                taxCollected = sheet.incomeTaxRevenue();
                localGov = sheet.totalExpenditureCredits();
                net = sheet.netBalanceCredits();
                debt = sheet.outstandingDebtCredits();
            } else {
                grossOutput = 0.0;
                taxCollected = 0.0;
                localGov = 0.0;
                net = 0.0;
                debt = 0.0;
            }

            ledger.add(new ColonyEconomyEntry(colony.id(), colony.name(), colony.systemName(),
                    colony.isMoon(), pop, grossOutput, taxCollected, localGov, net, debt));
        }
    }

    private static void calculateCorporateLedger(EmpireView view, double taxRate, List<CorporateEconomyEntry> ledger) {
        for (Corporation corp : view.getCorporationsForPlayerEmpire()) {
            double estimatedTariff = (corp.liquidCapitalReserves() * 0.001) + (corp.ownedFacilityIds().size() * 120.0 * taxRate);
            ledger.add(new CorporateEconomyEntry(corp.id(), corp.name(), corp.headquartersEntityId(), corp.marketOrientation(), corp.liquidCapitalReserves(), corp.ownedFacilityIds().size(), estimatedTariff));
        }
    }

    public static SystemEconomyReport calculateSystemEconomyReport(EmpireView view, String systemId) {

        SolarSystem system = view.getSystems().stream()
                .filter(s -> s.id().equals(systemId))
                .findFirst()
                .orElse(null);

        String systemName = system != null ? system.name() : (systemId != null ? systemId : "Unknown");
        Empire playerEmpire = view.getPlayerEmpire();
        String empireId = playerEmpire != null ? playerEmpire.id() : view.getPlayerEmpireId();

        long systemPop = 0;
        int colonizedCount = 0;
        if (system != null && system.planets() != null) {
            for (Planet p : system.planets()) {
                long pPop = 0;
                if (p.populations() != null) {
                    for (Population pop : p.populations()) pPop += pop.totalCount();
                }
                if (pPop > 0) {
                    systemPop += pPop;
                    colonizedCount++;
                }
                if (p.moons() != null) {
                    for (Moon m : p.moons()) {
                        long mPop = 0;
                        if (m.populations() != null) {
                            for (Population pop : m.populations()) mPop += pop.totalCount();
                        }
                        if (mPop > 0) {
                            systemPop += mPop;
                            colonizedCount++;
                        }
                    }
                }
            }
        }

        SystemEconomy economy = view.getSystemEconomies().stream()
                .filter(se -> se.systemId().equals(systemId))
                .findFirst()
                .orElse(null);

        if (economy == null) {
            economy = SystemEconomy.createDefault(systemId, empireId, systemPop);
        }

        double currentTaxRate = economy.taxRate();
        SystemEconomyReport settled = reportFromBalanceSheets(view, systemId, systemName, empireId,
                systemPop, colonizedCount, currentTaxRate, economy);
        if (settled != null) return settled;

        return new SystemEconomyReport(
                systemId, systemName, empireId, systemPop, colonizedCount,
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                currentTaxRate, economy, false
        );
    }

    private static SystemEconomyReport reportFromBalanceSheets(
            EmpireView view, String systemId, String systemName, String empireId,
            long population, int colonizedCount, double taxRate, SystemEconomy economy
    ) {
        List<PlanetaryBalanceSheet> sheets = view.getPlanetaryBalanceSheets().stream()
                .filter(sheet -> systemId.equals(sheet.systemId()))
                .toList();
        if (sheets.isEmpty()) return null;

        double gross = sheets.stream().mapToDouble(PlanetaryBalanceSheet::grossPlanetaryProduct).sum();
        double incomeTax = sheets.stream().mapToDouble(PlanetaryBalanceSheet::incomeTaxRevenue).sum();
        double corporateTariffs = sheets.stream().mapToDouble(PlanetaryBalanceSheet::corporateTariffRevenue).sum();
        double dockingFees = sheets.stream().mapToDouble(PlanetaryBalanceSheet::dockingFeeRevenue).sum();
        double revenue = sheets.stream().mapToDouble(PlanetaryBalanceSheet::totalRevenueCredits).sum();
        double publicFunding = sheets.stream().mapToDouble(PlanetaryBalanceSheet::publicSectorFundingCredits).sum();
        double salaries = sheets.stream().mapToDouble(PlanetaryBalanceSheet::workforceSalaries).sum();
        double localUpkeep = sheets.stream().mapToDouble(sheet -> sheet.facilityMaintenanceCosts()
                + sheet.publicWelfareExpenditures() + sheet.infrastructureUpkeepCosts()).sum();
        double expenses = sheets.stream().mapToDouble(PlanetaryBalanceSheet::totalExpenditureCredits).sum();
        double net = sheets.stream().mapToDouble(PlanetaryBalanceSheet::netBalanceCredits).sum();
        double debt = sheets.stream().mapToDouble(PlanetaryBalanceSheet::outstandingDebtCredits).sum();

        return new SystemEconomyReport(systemId, systemName, empireId, population, colonizedCount,
                gross, incomeTax, corporateTariffs, dockingFees, 0.0, 0.0, revenue,
                publicFunding, salaries, localUpkeep, expenses, net, debt,
                taxRate, economy, true);
    }

}
