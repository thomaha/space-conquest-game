package com.spaceconquest.control;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.technology.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class FarmingLifecycleTest {
    @TempDir Path directory;

    @Test void fundedResearchConstructionCultivationAndProcessingReachTheMarketAcrossReload() throws Exception {
        var state = researchWorld();
        var queue = new CommandQueue();
        for (String application : List.of("industrial_soil_cultivation", "food_processing")) {
            var equipment = ProductionWork.initialEquipment(application);
            var build = new BuildFacilityCommand("research-world", application, "corp", IndustrialFacility.PRIVATE_CORPORATE,
                    equipment.referenceOperators(), equipment.operatorProfession());
            assertFalse(build.validate(state));
            state = research(state, application);
            assertTrue(build.validate(state)); queue.submit(build); state = queue.drainAndExecute(state);
        }
        var orders = new HashMap<String, MarketOrder>();
        for (String id : List.of("refined_iron", "refined_aluminum", "refined_copper", "silicon", "nitrates", "phosphates", "potash"))
            orders.put(id, new MarketOrder(id, 100000, 1e6, 1, 0));
        for (String id : List.of("food_matrix", "harvested_crops", "agricultural_biomass"))
            orders.put(id, new MarketOrder(id, 0, 1e6, 1, 0));
        state = state.toBuilder().commercialHubs(List.of(new CommercialHub("hub", "research-world", 0, 1e8, 700000, 10, orders)))
                .marketAccounts(List.of(new MarketAccount("hub", 1e8))).build();
        for (int tick = 0; tick < 20 && !state.expansionProjects().isEmpty(); tick++) state = new IndustryProcessor().processIndustrialProduction(state);
        assertTrue(state.expansionProjects().isEmpty());
        var farm = state.industrialFacilities().getFirst();
        var factory = state.industrialFacilities().get(1);
        var workers = Map.of(farm.id(), farm.allocatedWorkers(), factory.id(), factory.allocatedWorkers());
        var budgets = Map.of(farm.id(), new ProductionWork.EnergyBudget(1e6, 0), factory.id(), new ProductionWork.EnergyBudget(1e6, 0));
        long firstTurn = state.turn();
        state = step(state, workers, budgets);
        var growth = account(state, farm.id()).farmingState();
        assertTrue(growth.cultivatedBatches() > 0);
        assertFalse(new ExpandFacilityCommand(farm.id(), 2, 0).validate(state));
        assertTrue(account(state, farm.id()).producedKg().isEmpty());
        assertTrue(account(state, factory.id()).producedKg().isEmpty());
        var sameTurn = step(state, workers, budgets);
        assertEquals(growth, account(sameTurn, farm.id()).farmingState());
        var saved = new SaveGameManager(directory); var file = saved.save("growing", state, 1, GameClock.START_TIME.toString());
        state = saved.load(file.toFile()).toGameState(state.turn(), state.status());
        assertEquals(growth, account(state, farm.id()).farmingState());
        var noPower = step(state.withTurn(firstTurn + 1), workers, Map.of());
        assertEquals(growth, account(noPower, farm.id()).farmingState());
        var shortOrders = new HashMap<>(state.commercialHubs().getFirst().activeOrders());
        shortOrders.put("nitrates", new MarketOrder("nitrates", 0, 1e6, 1, 0));
        var shortState = state.withTurn(firstTurn + 1).withCommercialHubs(List.of(new CommercialHub("hub", "research-world", 0, 1e8, 600000, 10, shortOrders)));
        assertEquals(growth, account(step(shortState, workers, budgets), farm.id()).farmingState());
        assertEquals(growth, account(step(state.withTurn(firstTurn + 1), Map.of(), budgets), farm.id()).farmingState());
        for (int day = 1; day < 120; day++) state = step(state.withTurn(firstTurn + day), workers, budgets);
        assertEquals(14400, account(state, farm.id()).producedKg().get("food_matrix"), 1e-6);
        assertEquals(14400, account(state, farm.id()).soldKg().get("harvested_crops"), 1e-6);
        assertEquals(960, account(state, factory.id()).producedKg().get("food_matrix"), 1e-6);
        assertEquals(15360, state.commercialHubs().getFirst().activeOrders().get("food_matrix").supplyKg(), 1e-6);
        assertEquals(0, account(state, farm.id()).farmingState().cultivatedBatches());
        file = saved.save("harvested", state, 1, GameClock.START_TIME.toString());
        assertEquals(state.industryAccounts(), saved.load(file.toFile()).toGameState(state.turn(), state.status()).industryAccounts());
    }

    @Test void drySitesAndExhaustedLandRejectConstructionIncludingPendingProjectsAndExpansion() throws Exception {
        var state = research(researchWorld(), "industrial_soil_cultivation");
        var farm = new BuildFacilityCommand("research-world", "industrial_soil_cultivation", "corp", IndustrialFacility.PRIVATE_CORPORATE, 1, "farmer");
        var original = state.solarSystems().getFirst().planets().getFirst();
        var tiny = new Planet(original.id(), original.name(), "", 1, 9.81, 1, 0, .33, "terrestrial", "nitrogen_oxygen", true, 0, List.of(), List.of(), original.populations());
        var system = state.solarSystems().getFirst();
        state = state.withSolarSystems(List.of(new SolarSystem(system.id(), system.name(), "", 0, 0, 0, 1, 1, "Yellow", List.of(tiny), List.of())));
        assertTrue(farm.validate(state));
        state = farm.apply(state);
        assertFalse(farm.validate(state));
        var built = state.industrialFacilities().getFirst();
        assertFalse(FarmingDefinition.canInstall(state, built.applicationId(), built.planetId(), built.id(), 2, built.productionEquipment()));
        assertFalse(new PlaceFacilityOnTileCommand("research-world", 1, built.applicationId(), "corp", IndustrialFacility.PRIVATE_CORPORATE, 1, "farmer").validate(state));
        assertFalse(new BuildFacilityCommand("missing", built.applicationId(), "corp", IndustrialFacility.PRIVATE_CORPORATE, 1, "farmer").validate(state));
    }

    private GameState researchWorld() {
        var owner = new Empire("owner", "Owner", "human", "Individualist", 1e8, 0, List.of("sol"), List.of(), Map.of(), List.of("industrial_production"), List.of());
        var state = ResearchTestWorld.staffed(GameState.builder().empires(List.of(owner))
                .corporations(List.of(new Corporation("corp", "Corp", "owner", "research-world", "AGRICULTURE", 1e8, List.of(), List.of(), List.of()))).build());
        var old = state.solarSystems().getFirst(); var p = old.planets().getFirst();
        var planet = new Planet(p.id(), p.name(), "", p.mass(), p.gravity(), p.distance(), p.inclination(), p.diameter(), p.type(), "nitrogen_oxygen", true, .7, p.resources(), p.moons(), p.populations());
        return state.withSolarSystems(List.of(new SolarSystem(old.id(), old.name(), "", 0, 0, 0, 1, 1, "Yellow", List.of(planet), List.of())));
    }
    private GameState research(GameState state, String application) {
        var research = new ResearchProcessor(new Random() { @Override public double nextDouble() { return .01; } });
        var start = new StartResearchCommand("owner", application, true, 30); assertTrue(start.validate(state));
        state = start.apply(state);
        for (int tick = 0; tick < 100 && !state.researchProjects().isEmpty(); tick++) {
            var result = new ResearchTickProcessor().process(state, List.of(), research);
            state = state.toBuilder().empires(result.empires()).researchProjects(result.projects()).applicationOptimizations(result.optimizations()).build();
        }
        assertTrue(state.empires().getFirst().unlockedTechIds().contains(application));
        return new ResolveApplicationResearchCommand("owner", application, true).apply(state);
    }
    private GameState step(GameState state, Map<String, Integer> workers, Map<String, ProductionWork.EnergyBudget> budgets) {
        double before = state.corporations().getFirst().liquidCapitalReserves() + state.marketAccounts().getFirst().unsettledSalesCredits();
        var result = new IndustryMarketProcessor().process(state, workers, Map.of(), workers, budgets);
        assertEquals(before, result.corporations().getFirst().liquidCapitalReserves() + result.marketAccounts().getFirst().unsettledSalesCredits(), 1e-5);
        return state.toBuilder().empires(result.empires()).corporations(result.corporations()).commercialHubs(result.hubs())
                .industryAccounts(result.industryAccounts()).marketAccounts(result.marketAccounts()).build();
    }
    private IndustryAccount account(GameState state, String id) { return state.industryAccounts().stream().filter(a -> a.facilityId().equals(id)).findFirst().orElseThrow(); }
}
