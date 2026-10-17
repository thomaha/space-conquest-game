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

class MetalProductionLifecycleTest {
    @TempDir Path directory;
    @Test void copperResearchConstructionAndFiniteOreBecomeFundedMarketProductsThroughReload() throws Exception {
        chain("copper_ore_mining", "copper_refining", "copper_ore", "refined_copper", 600);
    }
    @Test void aluminumResearchConstructionAndFiniteOreBecomeFundedMarketProductsThroughReload() throws Exception {
        chain("aluminum_ore_mining", "aluminum_refining", "aluminum_ore", "refined_aluminum", 500);
    }

    @Test void constructionUsesApplicationMinimumTierAndRejectsAnIncompatibleGravitySite() throws Exception {
        var knowledge = new ArrayList<String>();
        DataModelLoader.loadTechnologies().forEach(t -> { knowledge.add(t.id()); t.applications().forEach(a -> knowledge.add(a.id())); });
        var owner = new Empire("owner", "Owner", "human", "Individualist", 1e8, 0, List.of("sol"), List.of(), Map.of(), knowledge, List.of());
        var low = new Planet("low", "Low", "", 1e20, .1, 1e8, 0, 1000, "terrestrial", "vacuum", false, 0, List.of(), List.of(), List.of());
        var high = new Planet("high", "High", "", 1e24, 9.81, 1e8, 0, 10000, "terrestrial", "vacuum", false, 0, List.of(), List.of(), List.of());
        var system = new SolarSystem("sol", "Sol", "", 0, 0, 0, 1e30, 1e6, "Yellow", List.of(low, high), List.of());
        var state = GameState.builder().empires(List.of(owner)).solarSystems(List.of(system)).build();
        for (var recipe : IndustryRecipeCatalog.all()) if (recipe.factory() != null && recipe.minTier() > 1) {
            var equipment = ProductionWork.initialEquipment(recipe.applicationId());
            String site = recipe.infrastructure().permitsSite(state, "low") ? "low" : "high";
            var build = new BuildFacilityCommand(site, recipe.applicationId(), "owner", IndustrialFacility.PUBLIC_STATE,
                    equipment.referenceOperators(), equipment.operatorProfession());
            assertTrue(build.validate(state));
            assertEquals(recipe.minTier(), build.apply(state).expansionProjects().getFirst().targetTier(), recipe.applicationId());
        }
        assertFalse(new BuildFacilityCommand("high", "zero_g_platinum", "owner", IndustrialFacility.PUBLIC_STATE, 100, "industrial_worker").validate(state));
    }

    private void chain(String mine, String refinery, String ore, String metal, double outputPerBatch) throws Exception {
        var owner = new Empire("owner", "Owner", "human", "Individualist", 1e8, 0, List.of("sol"), List.of(), Map.of(), List.of("industrial_production"), List.of());
        var state = ResearchTestWorld.staffed(GameState.builder().empires(List.of(owner))
                .corporations(List.of(new Corporation("corp", "Corp", "owner", "research-world", "METALLURGY", 1e8, List.of(), List.of(), List.of()))).build());
        var research = new ResearchProcessor(new Random() { @Override public double nextDouble() { return .01; } });
        var queue = new CommandQueue();
        for (String application : List.of(mine, refinery)) {
            var equipment = ProductionWork.initialEquipment(application);
            var build = new BuildFacilityCommand("research-world", application, "corp", IndustrialFacility.PRIVATE_CORPORATE,
                    equipment.referenceOperators(), equipment.operatorProfession());
            assertFalse(build.validate(state));
            var start = new StartResearchCommand("owner", application, true, 30); assertTrue(start.validate(state));
            queue.submit(start); state = queue.drainAndExecute(state);
            for (int tick = 0; tick < 100 && !state.researchProjects().isEmpty(); tick++) {
                var result = new ResearchTickProcessor().process(state, List.of(), research);
                state = state.toBuilder().empires(result.empires()).researchProjects(result.projects()).applicationOptimizations(result.optimizations()).build();
            }
            assertTrue(state.empires().getFirst().unlockedTechIds().contains(application));
            state = new ResolveApplicationResearchCommand("owner", application, true).apply(state);
            assertTrue(build.validate(state)); queue.submit(build); state = queue.drainAndExecute(state);
        }
        var orders = new HashMap<String, MarketOrder>();
        for (String id : List.of("refined_iron", "refined_aluminum", "refined_copper", "silicon"))
            orders.put(id, new MarketOrder(id, 10000, 1e6, 1, 0));
        for (String id : IndustryRecipeCatalog.find(refinery).outputsKg().keySet()) orders.putIfAbsent(id, new MarketOrder(id, 0, 1e6, 1, 0));
        orders.put(ore, new MarketOrder(ore, 0, 1e6, 1, 0));
        state = state.toBuilder().commercialHubs(List.of(new CommercialHub("hub", "research-world", 0, 1e8, 40000, 10, orders)))
                .marketAccounts(List.of(new MarketAccount("hub", 1e8))).build();
        for (int tick = 0; tick < 20 && !state.expansionProjects().isEmpty(); tick++) state = new IndustryProcessor().processIndustrialProduction(state);
        assertTrue(state.expansionProjects().isEmpty());
        orders = new HashMap<>(state.commercialHubs().getFirst().activeOrders());
        orders.put(metal, new MarketOrder(metal, 0, 1e6, 1, 0));
        state = state.toBuilder().commercialHubs(List.of(new CommercialHub("hub", "research-world", 0, 1e8,
                        orders.values().stream().mapToDouble(MarketOrder::supplyKg).sum(), 10, orders)))
                .geologicalDeposits(List.of(new GeologicalDeposit("ore", "research-world", ore, 750, 750, 1, true, "corp"))).build();
        var saves = new SaveGameManager(directory); var file = saves.save("metals", state, 1, GameClock.START_TIME.toString());
        state = saves.load(file.toFile()).toGameState(state.turn(), state.status());
        var workers = new HashMap<String, Integer>(); var budgets = new HashMap<String, ProductionWork.EnergyBudget>();
        state.industrialFacilities().forEach(f -> { workers.put(f.id(), f.allocatedWorkers()); budgets.put(f.id(), new ProductionWork.EnergyBudget(10000, 0)); });
        double priorCash = state.corporations().getFirst().liquidCapitalReserves() + state.marketAccounts().getFirst().unsettledSalesCredits();
        var result = new IndustryMarketProcessor().process(state, workers, Map.of(), workers, budgets);
        assertEquals(priorCash, result.corporations().getFirst().liquidCapitalReserves() + result.marketAccounts().getFirst().unsettledSalesCredits(), 1e-6);
        assertEquals(0, result.deposits().getFirst().remainingVolumeKg(), 1e-8);
        assertEquals(outputPerBatch * .75, result.hubs().getFirst().activeOrders().get(metal).supplyKg(), 1e-8);
        assertTrue(result.industryAccounts().stream().anyMatch(a -> a.soldKg().containsKey(metal)));
        state = state.toBuilder().commercialHubs(result.hubs()).industryAccounts(result.industryAccounts()).marketAccounts(result.marketAccounts())
                .geologicalDeposits(result.deposits()).empires(result.empires()).corporations(result.corporations()).build();
        file = saves.save("completed", state, 1, GameClock.START_TIME.toString());
        var reloaded = saves.load(file.toFile()).toGameState(state.turn(), state.status());
        assertEquals(state.industryAccounts(), reloaded.industryAccounts());
        assertEquals(state.commercialHubs(), reloaded.commercialHubs());
    }
}
