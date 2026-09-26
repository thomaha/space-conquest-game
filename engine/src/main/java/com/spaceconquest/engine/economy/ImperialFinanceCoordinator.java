package com.spaceconquest.engine.economy;

import com.spaceconquest.engine.Empire;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Collects actual treasury movements on the simulation thread and settles imperial debt. */
public class ImperialFinanceCoordinator {
    private final Map<String, Double> pendingIncome = new HashMap<>();
    private final Map<String, Double> pendingExpenses = new HashMap<>();
    private final Map<String, Double> income = new HashMap<>();
    private final Map<String, Double> expenses = new HashMap<>();
    private final Map<String, Double> newDebt = new HashMap<>();

    public void beginTurn() {
        income.clear();
        expenses.clear();
        income.putAll(pendingIncome);
        expenses.putAll(pendingExpenses);
        pendingIncome.clear();
        pendingExpenses.clear();
        newDebt.clear();
    }

    public void recordCommands(List<Empire> before, List<Empire> after) {
        recordChanges(before, after, pendingIncome, pendingExpenses);
    }

    public void recordTreasuryChanges(List<Empire> before, List<Empire> after) {
        recordChanges(before, after, income, expenses);
    }

    public void recordIndustryFlows(Map<String, Double> receipts, Map<String, Double> payments) {
        receipts.forEach((empireId, credits) -> income.merge(empireId, credits, Double::sum));
        payments.forEach((empireId, credits) -> expenses.merge(empireId, credits, Double::sum));
    }

    public void recordMunicipal(List<PlanetaryBalanceSheet> sheets, List<Empire> empires,
                                Map<String, Double> incurredDebt) {
        for (PlanetaryBalanceSheet sheet : sheets) {
            if (sheet.centralSubsidyReceivedCredits() > 0) {
                expenses.merge(sheet.empireId(), sheet.centralSubsidyReceivedCredits(), Double::sum);
            }
            if (sheet.empireTransferCredits() > 0 && empires.stream().anyMatch(emp ->
                    emp.id().equals(sheet.empireId()) && emp.unlockedTechIds() != null
                            && emp.unlockedTechIds().contains(PlanetaryMunicipalProcessor.TECH_SUBSPACE_BANKING))) {
                income.merge(sheet.empireId(), sheet.empireTransferCredits(), Double::sum);
            }
        }
        incurredDebt.forEach((id, amount) -> newDebt.merge(id, amount, Double::sum));
    }

    public Settlement settle(long turn, List<Empire> empires, List<ImperialBalanceSheet> previous) {
        Map<String, Double> oldDebt = new HashMap<>();
        for (ImperialBalanceSheet sheet : previous) oldDebt.put(sheet.empireId(), sheet.outstandingDebtCredits());
        List<Empire> updated = new ArrayList<>();
        List<ImperialBalanceSheet> sheets = new ArrayList<>();
        for (Empire empire : empires) {
            double debt = oldDebt.getOrDefault(empire.id(), 0.0) + newDebt.getOrDefault(empire.id(), 0.0);
            double repayment = Math.min(Math.max(0.0, empire.treasuryCredits()), debt);
            double treasury = empire.treasuryCredits() - repayment;
            updated.add(repayment == 0 ? empire : new Empire(
                    empire.id(), empire.name(), empire.raceId(), empire.societyStructure(), treasury,
                    empire.corporateTaxRate(), empire.controlledSystemIds(), empire.ministries(),
                    empire.systemGovernorAssignments(), empire.unlockedTechIds(), empire.activeShipDesignIds()));
            sheets.add(new ImperialBalanceSheet(empire.id(), turn,
                    income.getOrDefault(empire.id(), 0.0), expenses.getOrDefault(empire.id(), 0.0),
                    Math.max(0.0, debt - repayment), repayment));
        }
        return new Settlement(updated, sheets);
    }

    private void recordChanges(List<Empire> before, List<Empire> after,
                               Map<String, Double> receipts, Map<String, Double> payments) {
        Map<String, Double> original = new HashMap<>();
        for (Empire empire : before) original.put(empire.id(), empire.treasuryCredits());
        for (Empire empire : after) {
            if (!original.containsKey(empire.id())) continue;
            double delta = empire.treasuryCredits() - original.get(empire.id());
            if (delta > 0) receipts.merge(empire.id(), delta, Double::sum);
            if (delta < 0) payments.merge(empire.id(), -delta, Double::sum);
        }
    }

    public record Settlement(List<Empire> empires, List<ImperialBalanceSheet> balanceSheets) {}
}
