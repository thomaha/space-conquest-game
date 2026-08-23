package com.spaceconquest.engine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SpaceConquestEngineTest {

    @Test
    public void testPopulationGrowthOverTurns() {
        SpaceConquestEngine engine = new SpaceConquestEngine();
        engine.start();
        
        GameState initialState = engine.getGameState();
        SolarSystem sol = initialState.solarSystems().stream().filter(ss -> ss.id().equals("sol")).findFirst().orElseThrow();
        Planet earth = sol.planets().stream().filter(p -> p.id().equals("earth")).findFirst().orElseThrow();
        Population initialPop = earth.populations().getFirst();
        long initialTotal = initialPop.ageGroups().values().stream().mapToLong(Long::longValue).sum();
        
        // Update for 10 turns (10 years)
        for (int i = 0; i < 10; i++) {
            engine.update();
        }
        
        GameState futureState = engine.getGameState();
        SolarSystem futureSol = futureState.solarSystems().stream().filter(ss -> ss.id().equals("sol")).findFirst().orElseThrow();
        Planet futureEarth = futureSol.planets().stream().filter(p -> p.id().equals("earth")).findFirst().orElseThrow();
        Population futurePop = futureEarth.populations().getFirst();
        long futureTotal = futurePop.ageGroups().values().stream().mapToLong(Long::longValue).sum();
        
        assertTrue(futureTotal > initialTotal, "Population should have grown over 10 years. Initial: " + initialTotal + ", Future: " + futureTotal);
        assertTrue(futurePop.ageGroups().containsKey(0), "Should have newborns");
        assertTrue(futurePop.ageGroups().containsKey(10), "Age 0 should have aged to 10 if there were any, but at least existing groups should have aged");
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

        GameState modifiedState = new GameState(
                initialState.turn(),
                initialState.status(),
                initialState.solarSystems(),
                initialState.empires(),
                initialState.corporations(),
                List.of(seededHub),
                initialState.shadowSyndicates(),
                initialState.diplomaticRelations(),
                initialState.systemGovernors()
        );
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
    public void testGovernanceAndDemocraticElectionSimulationTurns() {
        SpaceConquestEngine engine = new SpaceConquestEngine(GameStartScenario.BASIC_WARP);
        engine.start();

        GameState initialState = engine.getGameState();
        assertFalse(initialState.empires().isEmpty(), "Empires should exist");

        // Run 5 turns to trigger governance cycle and democratic election
        for (int i = 0; i < 5; i++) {
            engine.update();
        }

        GameState advancedState = engine.getGameState();
        assertEquals(5, advancedState.turn());
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
