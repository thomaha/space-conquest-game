package com.spaceconquest.engine;

import com.spaceconquest.engine.economy.PlanetaryBalanceSheet;
import com.spaceconquest.engine.economy.SystemEconomy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SpaceConquestEngineTest {
    @Test
    public void testClockSchedulesDailyTurnsWithoutRunningEarly() {
        SpaceConquestEngine engine = new SpaceConquestEngine();
        GameClock clock = engine.getGameClock();
        clock.setSpeed(GameClock.ClockSpeed.SPEED_6_HOURS);
        for (int i = 0; i < 3; i++) {
            assertEquals(0, clock.update(1.0));
            assertEquals(0, engine.getGameState().turn());
        }
        assertEquals(1, clock.update(1.0));
        engine.processScheduledTurn();
        assertEquals(1, engine.getGameState().turn());
        assertEquals(clock.getCurrentTurn(), engine.getGameState().turn());
    }


    @Test
    public void testPopulationAgesOnYearBoundaryRatherThanEveryDailyTurn() {
        SpaceConquestEngine engine = new SpaceConquestEngine();
        engine.start();
        
        GameState initialState = engine.getGameState();
        SolarSystem sol = initialState.solarSystems().stream().filter(ss -> ss.id().equals("sol")).findFirst().orElseThrow();
        Planet earth = sol.planets().stream().filter(p -> p.id().equals("earth")).findFirst().orElseThrow();
        Population initialPop = earth.populations().getFirst();
        long initialTotal = initialPop.ageGroups().values().stream().mapToLong(Long::longValue).sum();
        
        // Jump to the end of 2200, then cross the annual boundary one day at a time.
        engine.reset(initialState.withTurn(363));
        engine.update();
        Population beforeNewYear = engine.getGameState().solarSystems().stream()
                .filter(ss -> ss.id().equals("sol")).findFirst().orElseThrow()
                .planets().stream().filter(p -> p.id().equals("earth")).findFirst().orElseThrow()
                .populations().getFirst();
        assertEquals(initialPop.ageGroups(), beforeNewYear.ageGroups());

        engine.update();
        
        GameState futureState = engine.getGameState();
        SolarSystem futureSol = futureState.solarSystems().stream().filter(ss -> ss.id().equals("sol")).findFirst().orElseThrow();
        Planet futureEarth = futureSol.planets().stream().filter(p -> p.id().equals("earth")).findFirst().orElseThrow();
        Population futurePop = futureEarth.populations().getFirst();
        long futureTotal = futurePop.ageGroups().values().stream().mapToLong(Long::longValue).sum();
        
        assertTrue(futureTotal > initialTotal, "Population should have grown after one year. Initial: " + initialTotal + ", Future: " + futureTotal);
        assertTrue(futurePop.ageGroups().containsKey(0), "Should have newborns");
        assertTrue(futurePop.ageGroups().containsKey(1), "Existing newborns should have aged by one year");
    }

    @Test
    public void testEconomicAndMarketSimulationTurns() {
        SpaceConquestEngine engine = new SpaceConquestEngine(GameStartScenario.BASIC_WARP);
        engine.start();

        GameState initialState = engine.getGameState();
        assertFalse(initialState.commercialHubs().isEmpty(), "Commercial hubs should exist in BASIC_WARP scenario");
        assertFalse(initialState.corporations().isEmpty(), "Corporations should exist in BASIC_WARP scenario");

        // Seed an active order with shortage in one hub
        CommercialHub firstHub = initialState.commercialHubs().getFirst();
        MarketOrder order = new MarketOrder("refined_silicon", 5.0, 500.0, 10.0, 0.0);
        CommercialHub seededHub = new CommercialHub(
                firstHub.id(),
                firstHub.entityId(),
                firstHub.transactionTariffRate(),
                firstHub.storageCapacityKg(),
                firstHub.currentStoredWeightKg(),
                firstHub.logisticsRangeUnits(),
                Map.of("refined_silicon", order)
        );

        GameState modifiedState = initialState.toBuilder()
                .commercialHubs(List.of(seededHub))
                .build();
        engine.reset(modifiedState);

        // Run 5 turns
        for (int i = 0; i < 5; i++) {
            engine.update();
        }

        GameState advancedState = engine.getGameState();
        assertEquals(5, advancedState.turn());
        CommercialHub updatedHub = advancedState.commercialHubs().getFirst();
        MarketOrder updatedOrder = updatedHub.activeOrders().get("refined_silicon");
        assertNotNull(updatedOrder);
        assertTrue(updatedOrder.pricePerKg() > 10.0, "Shortage should have driven price up");
        assertTrue(updatedOrder.shortcomingScore() > 0.0, "Shortcoming score should be positive");
    }

    @Test
    public void testDailyEconomySettlesSystemContributionFromLocalCredits() {
        SpaceConquestEngine engine = new SpaceConquestEngine();
        GameState initial = engine.getGameState();
        SystemEconomy solEconomy = initial.systemEconomies().stream()
                .filter(economy -> "sol".equals(economy.systemId())).findFirst().orElseThrow();
        List<SystemEconomy> economies = initial.systemEconomies().stream()
                .map(economy -> "sol".equals(economy.systemId())
                        ? economy.withEmpireContributionRate(1.0) : economy)
                .toList();
        engine.reset(initial.withSystemEconomies(economies));

        engine.stepTurn();

        GameState settled = engine.getGameState();
        double contribution = settled.planetaryBalanceSheets().stream()
                .filter(sheet -> "sol".equals(sheet.systemId()))
                .mapToDouble(PlanetaryBalanceSheet::empireTransferCredits).sum();
        double funding = settled.planetaryBalanceSheets().stream()
                .filter(sheet -> "sol".equals(sheet.systemId()))
                .mapToDouble(PlanetaryBalanceSheet::publicSectorFundingCredits).sum();
        assertEquals(solEconomy.totalBudgetCredits(), funding, 0.001);
        assertTrue(contribution > 0.0);
        assertTrue(contribution <= solEconomy.totalBudgetCredits());
        assertTrue(settled.courierShips().stream().anyMatch(courier -> "sol".equals(courier.originSystemId())));
        for (Empire empire : settled.empires()) {
            Empire opening = initial.empires().stream()
                    .filter(candidate -> candidate.id().equals(empire.id())).findFirst().orElseThrow();
            var balance = settled.imperialBalanceSheets().stream()
                    .filter(sheet -> sheet.empireId().equals(empire.id())).findFirst().orElseThrow();
            assertEquals(opening.treasuryCredits() + balance.incomeCredits()
                    - balance.expenditureCredits() - balance.debtRepaidCredits(),
                    empire.treasuryCredits(), 0.001);
        }
    }

    @Test
    public void testGovernanceAndDemocraticElectionSimulationTurns() {
        SpaceConquestEngine engine = new SpaceConquestEngine(GameStartScenario.BASIC_WARP);
        engine.start();

        GameState initialState = engine.getGameState();
        assertFalse(initialState.empires().isEmpty(), "Empires should exist");

        // The fifth annual election falls at the start of 2205, after 1826 daily turns.
        engine.reset(initialState.withTurn(1825));
        engine.update();

        GameState advancedState = engine.getGameState();
        assertEquals(1826, advancedState.turn());
        assertFalse(advancedState.empires().isEmpty());

        Empire terran = advancedState.empires().stream()
                .filter(e -> "Individualist".equalsIgnoreCase(e.societyStructure()))
                .findFirst()
                .orElse(null);

        if (terran != null) {
            assertFalse(terran.ministries().isEmpty(), "Individualist empire should have populated cabinet post-election");
            for (MinistryAssignment assignment : terran.ministries()) {
                assertTrue(assignment.calculatedEfficiencyModifier() >= 1.0, "Efficiency modifier should be computed");
            }
        }
    }

    @Test
    public void testDefaultScenarioEarthAndVulcanBelongToDifferentEmpires() {
        SpaceConquestEngine engine = new SpaceConquestEngine();
        GameState state = engine.getGameState();

        // Locate Earth and Vulcan systems
        SolarSystem sol = state.solarSystems().stream()
                .filter(ss -> ss.planets().stream().anyMatch(p -> p.id().equals("earth")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Earth system not found"));

        SolarSystem vulcanSystem = state.solarSystems().stream()
                .filter(ss -> ss.planets().stream().anyMatch(p -> p.id().equals("vulcan")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Vulcan system not found"));

        // Find controlling empires
        Empire earthEmpire = state.empires().stream()
                .filter(e -> e.controlledSystemIds().contains(sol.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Controlling empire for Earth system not found"));

        Empire vulcanEmpire = state.empires().stream()
                .filter(e -> e.controlledSystemIds().contains(vulcanSystem.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Controlling empire for Vulcan system not found"));

        assertNotEquals(earthEmpire.id(), vulcanEmpire.id(), "Earth and Vulcan must belong to different empires in the default starting scenario");
        assertEquals("terran_confederation", earthEmpire.id());
        assertEquals("vulkan_forge", vulcanEmpire.id());
        assertEquals("human", earthEmpire.raceId());
        assertEquals("vulkan", vulcanEmpire.raceId());
    }
}
