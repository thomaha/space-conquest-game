package com.spaceconquest.control;

import com.spaceconquest.control.ai.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.*;
import com.spaceconquest.engine.industry.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MultiSystemEconomyTest {
    @TempDir Path directory;
    private static final class Decisions {
        final EmpireEconomicPlanner economy = new EmpireEconomicPlanner();
        final EmpirePowerPlanner power = new EmpirePowerPlanner();
        final EmpireOperatingCapitalPlanner cash = new EmpireOperatingCapitalPlanner();
        void submit(GameState state, CommandQueue queue) {
            var owner = state.empires().getFirst(); var recovery = cash.plan(state, owner); recovery.forEach(queue::submit);
            if (recovery.isEmpty() && !EmpireOperatingCapitalPlanner.needsSupport(state, owner)) power.plan(state, owner).forEach(queue::submit);
            economy.plan(state, owner).forEach(queue::submit);
        }
    }
    private IndustryAccount account(GameState state, String id) {
        return state.industryAccounts().stream().filter(value -> value.facilityId().equals(id)).findFirst().orElse(IndustryAccount.empty(id));
    }
    private SystemEconomy economy(GameState state, String id) { return state.systemEconomies().stream().filter(value -> value.systemId().equals(id)).findFirst().orElseThrow(); }
    private PlanetaryBalanceSheet sheet(GameState state, String id) { return state.planetaryBalanceSheets().stream().filter(value -> value.systemId().equals(id)).findFirst().orElseThrow(); }
    @Test void normalDailyEconomyCoordinatesTwoSystemsAcrossAThirtyDayCheckpoint() throws Exception {
        var initial = MultiSystemEconomyWorld.create(); var engine = new SpaceConquestEngine(42); engine.applyGameState(initial);
        var decisions = new Decisions(); var queue = new CommandQueue(); SpaceConquestEngine resumed = null; Decisions resumedDecisions = null;
        var report = new StringBuilder("# Multi-system economy probe\n\n60 normal engine days with economic, power and operating-capital planners. Opening cash and stock are finite. "
                + "A disk checkpoint after day 30 runs alongside the uninterrupted path.\n\n"
                + "| Day | Treasury | Rich net | Poor debt | Rich transfer | Poor transfer | Poor plant tier | Poor ammo kg | Rich tax | Poor tax |\n"
                + "| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        int productionDays = 0, contributions = 0, subsidies = 0, recoveries = 0, constructionDays = 0, surplusDays = 0;
        int taxReversals = 0, lastDirection = 0; double previousTax = economy(initial, "rich").taxRate();
        for (int day = 0; day < 60; day++) {
            var before = engine.getGameState(); decisions.submit(before, queue); queue.processCommands(engine); var ordered = engine.getGameState();
            double transfer = ordered.industryAccounts().stream().mapToDouble(value -> Math.max(0, value.operatingCashCredits() - account(before, value.facilityId()).operatingCashCredits())).sum();
            if (transfer > 0) { recoveries++; assertEquals(before.empires().getFirst().treasuryCredits() - transfer, ordered.empires().getFirst().treasuryCredits(), .001); }
            if (!ordered.expansionProjects().isEmpty()) constructionDays++;
            engine.stepTurn(); var state = engine.getGameState();
            if (account(state, "poor-ammo").producedKg().getOrDefault("cannon_round", 0.0) > 0) productionDays++;
            if (sheet(state, "rich").empireTransferCredits() > 0) contributions++;
            if (sheet(state, "poor").empireTransferCredits() < 0) subsidies++;
            if (sheet(state, "rich").netBalanceCredits() > 0) surplusDays++;
            double tax = economy(state, "rich").taxRate(); int direction = (int) Math.signum(tax - previousTax);
            if (direction != 0) { if (lastDirection != 0 && direction != lastDirection) taxReversals++; lastDirection = direction; } previousTax = tax;
            for (var budget : state.systemEconomies()) {
                assertEquals(1, budget.educationAllocation() + budget.lawAndOrderAllocation() + budget.healthAndWelfareAllocation()
                        + budget.infrastructureAllocation() + budget.planetaryMilitiasAllocation(), 1e-9);
                assertTrue(Double.isFinite(budget.totalBudgetCredits()));
            }
            assertTrue(state.empires().getFirst().treasuryCredits() >= EconomicPolicy.current().treasuryReserveCredits());
            assertTrue(state.imperialBalanceSheets().stream().allMatch(value -> value.outstandingDebtCredits() == 0));
            int tier = state.industrialFacilities().stream().filter(value -> value.id().equals("poor-solar")).findFirst().orElseThrow().tier();
            report.append(String.format(Locale.ROOT, "| %d | %.2f | %.2f | %.2f | %.2f | %.2f | %d | %.2f | %.3f | %.3f |%n", day + 1,
                    state.empires().getFirst().treasuryCredits(), sheet(state, "rich").netBalanceCredits(), sheet(state, "poor").outstandingDebtCredits(),
                    sheet(state, "rich").empireTransferCredits(), sheet(state, "poor").empireTransferCredits(), tier,
                    account(state, "poor-ammo").producedKg().getOrDefault("cannon_round", 0.0), tax, economy(state, "poor").taxRate()));
            if (resumed != null) {
                var otherQueue = new CommandQueue(); resumedDecisions.submit(resumed.getGameState(), otherQueue); otherQueue.processCommands(resumed); resumed.stepTurn();
                var other = resumed.getGameState(); assertEquals(state.empires().getFirst().treasuryCredits(), other.empires().getFirst().treasuryCredits(), .01);
                assertEquals(state.systemEconomies(), other.systemEconomies()); assertEquals(state.expansionProjects().size(), other.expansionProjects().size());
                assertEquals(account(state, "poor-ammo").producedKg(), account(other, "poor-ammo").producedKg());
            }
            if (day == 29) {
                var file = directory.resolve("economy.scsave").toFile(); var saves = new SaveGameManager(directory); saves.save(file, state, 1, GameClock.START_TIME.plusDays(state.turn()).toString());
                resumed = new SpaceConquestEngine(42); resumed.applyGameState(saves.load(file).toGameState(state.turn(), state.status())); resumedDecisions = new Decisions();
            }
        }
        var end = engine.getGameState(); report.append("\nProduction days: " + productionDays + "; surplus days: " + surplusDays + "; positive transfer days: " + contributions
                + "; subsidy days: " + subsidies + "; recovery days: " + recoveries + "; construction days: " + constructionDays + "; rich tax direction reversals: " + taxReversals + ".\n");
        report.append("\nFinal public factory readiness: " + FacilityOperatingReadiness.inspect(end, "owner", "poor-ammo").status() + ".\n");
        Files.createDirectories(Path.of("target")); Files.writeString(Path.of("target/multi-system-economy.md"), report);
        assertTrue(surplusDays > 10); assertTrue(contributions > 5); assertTrue(subsidies > 5); assertTrue(recoveries > 0);
        assertTrue(constructionDays > 0, "Persistent measured power shortages must reach funded construction");
        assertTrue(end.industrialFacilities().stream().anyMatch(value -> value.id().equals("poor-solar") && value.tier() >= 2));
        assertTrue(productionDays > 15, "A recoverable public factory must not remain stalled");
        assertEquals(initial.industrialFacilities().size(), end.industrialFacilities().size(), "Existing staffed plants should expand without duplicates");
        assertTrue(taxReversals <= 2, "Bounded fiscal correction should avoid repeated full-step tax overshoot");
    }
}
