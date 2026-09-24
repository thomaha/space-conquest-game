package com.spaceconquest.engine;

import com.spaceconquest.engine.economy.ImperialFinanceCoordinator;
import com.spaceconquest.engine.economy.PlanetaryBalanceSheet;
import com.spaceconquest.engine.economy.PlanetaryMunicipalProcessor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImperialFinanceCoordinatorTest {
    private Empire empire(double treasury) {
        return new Empire("emp", "Empire", "race_human", "Democracy", treasury,
                0.1, List.of("sys"), List.of(), Map.of(), List.of(), List.of());
    }

    @Test
    void recordsRealMovementsAndRepaysDebtFromLaterReceipts() {
        ImperialFinanceCoordinator coordinator = new ImperialFinanceCoordinator();
        coordinator.recordCommands(List.of(empire(50)), List.of(empire(30)));
        coordinator.beginTurn();
        PlanetaryBalanceSheet subsidy = new PlanetaryBalanceSheet(
                "planet", "sys", "emp", 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 100, 0, -100, 0);
        coordinator.recordMunicipal(List.of(subsidy), List.of(empire(0)), Map.of("emp", 70.0));
        var first = coordinator.settle(1, List.of(empire(0)), List.of());
        assertEquals(120, first.balanceSheets().getFirst().expenditureCredits(), 0.001);
        assertEquals(70, first.balanceSheets().getFirst().outstandingDebtCredits(), 0.001);

        coordinator.beginTurn();
        coordinator.recordTreasuryChanges(List.of(empire(0)), List.of(empire(90)));
        var second = coordinator.settle(2, List.of(empire(90)), first.balanceSheets());
        assertEquals(90, second.balanceSheets().getFirst().incomeCredits(), 0.001);
        assertEquals(70, second.balanceSheets().getFirst().debtRepaidCredits(), 0.001);
        assertEquals(0, second.balanceSheets().getFirst().outstandingDebtCredits(), 0.001);
        assertEquals(20, second.empires().getFirst().treasuryCredits(), 0.001);
    }

    @Test
    void keepsElectronicReceiptsAndSubsidiesSeparateOnTheSameDay() {
        ImperialFinanceCoordinator coordinator = new ImperialFinanceCoordinator();
        coordinator.beginTurn();
        Empire bankingEmpire = new Empire("emp", "Empire", "race_human", "Democracy", 0,
                0.1, List.of("sys"), List.of(), Map.of(),
                List.of(PlanetaryMunicipalProcessor.TECH_SUBSPACE_BANKING), List.of());
        PlanetaryBalanceSheet contribution = new PlanetaryBalanceSheet(
                "a", "sys", "emp", 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 50, 0);
        PlanetaryBalanceSheet subsidy = new PlanetaryBalanceSheet(
                "b", "sys", "emp", 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 100, 0, -100, 0);
        coordinator.recordMunicipal(List.of(contribution, subsidy), List.of(bankingEmpire), Map.of());
        var result = coordinator.settle(1, List.of(bankingEmpire), List.of());
        assertEquals(50, result.balanceSheets().getFirst().incomeCredits(), 0.001);
        assertEquals(100, result.balanceSheets().getFirst().expenditureCredits(), 0.001);
    }
}
