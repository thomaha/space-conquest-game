package com.spaceconquest.control.ai;

import com.spaceconquest.control.command.SetSystemEconomyBudgetCommand;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.*;
import java.util.*;

/** Snapshot-only sector, taxation and signed-transfer proposal from settled local finances. */
public record AISystemEconomyPlan(SetSystemEconomyBudgetCommand expected, SetSystemEconomyBudgetCommand proposed) {
    private record Context(List<PlanetaryBalanceSheet> sheets, double net, double localCash, double debt, double hardship) {}
    public static SetSystemEconomyBudgetCommand settings(SystemEconomy economy) {
        return economy == null ? null : new SetSystemEconomyBudgetCommand(economy.empireId(), economy.systemId(), economy.educationAllocation(),
                economy.lawAndOrderAllocation(), economy.healthAndWelfareAllocation(), economy.infrastructureAllocation(), economy.planetaryMilitiasAllocation(),
                economy.totalBudgetCredits(), economy.taxRate(), economy.empireContributionRate());
    }
    public static AISystemEconomyPlan quote(GameState state, String empireId, String systemId, EconomicPolicy policy) {
        var empire = state.empires().stream().filter(value -> empireId.equals(value.id())).findFirst().orElse(null);
        var system = state.solarSystems().stream().filter(value -> systemId.equals(value.id())).findFirst().orElse(null);
        if (empire == null || system == null || !empire.controlledSystemIds().contains(systemId)
                || (empire.societyStructure() != null && empire.societyStructure().toLowerCase(Locale.ROOT).contains("hive"))) return null;
        long population = population(system); if (population <= 0) return null;
        var existing = state.systemEconomies().stream().filter(value -> systemId.equals(value.systemId())).findFirst().orElse(null);
        var current = existing == null ? SystemEconomy.createDefault(systemId, empireId, population) : existing;
        var context = context(state, empireId, systemId);
        var weights = allocations(state, empire, system, current, context, policy);
        double budget = budget(current, population, context, policy);
        double taxTarget = taxTarget(current, budget, context, policy);
        double tax = Math.clamp(step(current.taxRate(), taxTarget, policy.taxStep()), policy.minimumTaxRate(), policy.maximumTaxRate());
        double transfer = transfer(state, empire, system, current, budget, context, policy);
        var command = new SetSystemEconomyBudgetCommand(empireId, systemId, weights[0], weights[1], weights[2], weights[3], weights[4], budget, tax, transfer);
        var expected = settings(existing);
        return command.equals(expected) ? null : new AISystemEconomyPlan(expected, command);
    }
    private static Context context(GameState state, String empireId, String systemId) {
        var sheets = state.planetaryBalanceSheets().stream().filter(sheet -> empireId.equals(sheet.empireId()) && systemId.equals(sheet.systemId())).toList();
        var households = state.householdAccounts().stream().filter(account -> empireId.equals(account.empireId()) && systemId.equals(account.systemId())).toList();
        double people = households.stream().mapToDouble(HouseholdAccount::headcount).sum();
        double hardship = people == 0 ? 1 : households.stream().mapToDouble(account -> account.headcount() * account.wellbeing().materialCoverage()).sum() / people;
        return new Context(sheets, sheets.stream().mapToDouble(PlanetaryBalanceSheet::netBalanceCredits).sum(),
                sheets.stream().mapToDouble(PlanetaryBalanceSheet::uncollectedLocalCredits).sum(), sheets.stream().mapToDouble(PlanetaryBalanceSheet::outstandingDebtCredits).sum(), hardship);
    }
    private static double[] allocations(GameState state, Empire empire, SolarSystem system, SystemEconomy current, Context context, EconomicPolicy policy) {
        boolean war = state.diplomaticRelations().stream().anyMatch(relation -> "TOTAL_WAR".equals(relation.tier())
                && (empire.id().equals(relation.empireAId()) || empire.id().equals(relation.empireBId())));
        var bodies = new HashSet<String>(); system.planets().forEach(planet -> { bodies.add(planet.id()); planet.moons().forEach(moon -> bodies.add(moon.id())); });
        boolean powerShortage = state.powerGrids().stream().anyMatch(grid -> bodies.contains(grid.entityId()) && (grid.isDeficitBrownoutActive() || grid.netBalanceKw() < 0));
        boolean research = state.researchProjects().stream().anyMatch(project -> empire.id().equals(project.empireId()) && project.assignedScientists() > 0);
        double unemployed = state.householdAccounts().stream().filter(account -> system.id().equals(account.systemId()) && empire.id().equals(account.empireId()))
                .mapToDouble(account -> account.employment().unemploymentPressure() * account.headcount()).sum();
        double people = state.householdAccounts().stream().filter(account -> system.id().equals(account.systemId()) && empire.id().equals(account.empireId())).mapToDouble(HouseholdAccount::headcount).sum();
        double[] needs = {Math.max(0, 1 - current.educationLevel()) + (research ? 1 : 0), Math.max(0, 1 - current.lawAndOrderLevel()),
                Math.max(0, 1 - current.healthAndWelfareLevel()) + 1 - context.hardship() + (people == 0 ? 0 : unemployed / people),
                Math.max(0, 1 - current.infrastructureLevel()) + (powerShortage ? 1 : 0), Math.max(0, 1 - current.planetaryMilitiaLevel()) + (war ? 1 : 0)};
        var sectors = List.of("education", "law", "health", "infrastructure", "militia");
        double[] target = new double[5]; double total = 0;
        for (int i = 0; i < 5; i++) { target[i] = policy.baseWeights().get(sectors.get(i)) + policy.needWeights().get(sectors.get(i)) * needs[i]; total += target[i]; }
        double[] source = {current.educationAllocation(), current.lawAndOrderAllocation(), current.healthAndWelfareAllocation(), current.infrastructureAllocation(), current.planetaryMilitiasAllocation()};
        double maxDifference = 0;
        for (int i = 0; i < 5; i++) { target[i] /= total; maxDifference = Math.max(maxDifference, Math.abs(target[i] - source[i])); }
        double fraction = maxDifference == 0 ? 0 : Math.min(1, policy.allocationStep() / maxDifference);
        for (int i = 0; i < 5; i++) target[i] = source[i] + (target[i] - source[i]) * fraction;
        return target;
    }
    private static double budget(SystemEconomy current, long population, Context context, EconomicPolicy policy) {
        double baseline = Math.clamp(population * policy.budgetPerResident(), policy.minimumBudgetCredits(), policy.maximumBudgetCredits());
        if (!context.sheets().isEmpty() && Math.abs(context.net()) <= current.totalBudgetCredits() * policy.fiscalDeadbandFraction()) return current.totalBudgetCredits();
        double target = context.sheets().isEmpty() ? baseline : context.net() < 0 ? current.totalBudgetCredits() * (1 - policy.budgetStepFraction())
                : context.net() > 0 ? Math.max(baseline, Math.min(current.totalBudgetCredits() + context.net(), current.totalBudgetCredits() * (1 + policy.budgetStepFraction()))) : baseline;
        return Math.clamp(step(current.totalBudgetCredits(), target, Math.max(policy.minimumBudgetCredits() * policy.budgetStepFraction(),
                current.totalBudgetCredits() * policy.budgetStepFraction())), policy.minimumBudgetCredits(), policy.maximumBudgetCredits());
    }
    private static double taxTarget(SystemEconomy current, double budget, Context context, EconomicPolicy policy) {
        if (context.hardship() < policy.hardshipCoverageThreshold()) return policy.minimumTaxRate();
        if (context.sheets().isEmpty()) return policy.baselineTaxRate();
        if (Math.abs(context.net()) <= current.totalBudgetCredits() * policy.fiscalDeadbandFraction()) return current.taxRate();
        double incomeTax = context.sheets().stream().mapToDouble(PlanetaryBalanceSheet::incomeTaxRevenue).sum();
        double taxableIncome = current.taxRate() > 0 ? incomeTax / current.taxRate() : 0;
        if (taxableIncome <= 0) return context.net() < 0 ? policy.maximumTaxRate() : policy.minimumTaxRate();
        return Math.clamp(current.taxRate() + (budget - current.totalBudgetCredits() - context.net()) / taxableIncome,
                policy.minimumTaxRate(), policy.maximumTaxRate());
    }
    private static double transfer(GameState state, Empire empire, SolarSystem system, SystemEconomy current, double budget, Context context, EconomicPolicy policy) {
        long ownedPopulation = state.solarSystems().stream().filter(value -> empire.controlledSystemIds().contains(value.id())).mapToLong(AISystemEconomyPlan::population).sum();
        double share = ownedPopulation <= 0 ? 0 : (double) population(system) / ownedPopulation;
        double affordableRate = Math.min(policy.maximumTransferRate(), Math.max(0, empire.treasuryCredits() - policy.treasuryReserveCredits())
                * share / (policy.subsidyCashHorizonDays() * Math.max(1, budget)));
        double target = 0;
        if (!context.sheets().isEmpty() && (context.net() < 0 || context.debt() > 0))
            target = -Math.min(affordableRate, (Math.max(0, -context.net()) + context.debt() / policy.localReserveDays()) / Math.max(1, budget));
        else if (!context.sheets().isEmpty() && context.localCash() > budget * policy.localReserveDays())
            target = Math.min(policy.maximumTransferRate(), (context.localCash() - budget * policy.localReserveDays()) / (Math.max(1, budget) * policy.localReserveDays()));
        return Math.clamp(step(current.empireContributionRate(), target, policy.transferStep()), Math.min(0, target), Math.max(0, target));
    }
    private static double step(double source, double target, double amount) { return source + Math.clamp(target - source, -amount, amount); }
    private static long population(SolarSystem system) {
        return system.planets().stream().mapToLong(planet -> planet.populations().stream().mapToLong(Population::totalCount).sum()
                + planet.moons().stream().mapToLong(moon -> moon.populations().stream().mapToLong(Population::totalCount).sum()).sum()).sum();
    }
}
