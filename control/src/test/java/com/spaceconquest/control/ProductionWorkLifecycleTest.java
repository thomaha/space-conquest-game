package com.spaceconquest.control;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.market.MarketDemandProcessor;
import com.spaceconquest.engine.technology.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ProductionWorkLifecycleTest {
    @TempDir Path directory;

    @Test void researchUnlocksPaidEquipmentConstructionThenRealOreBecomesMarketSteelThroughReload() throws Exception {
        var owner = new Empire("owner", "Owner", "human", "Individualist", 1e7, 0,
                List.of("sol"), List.of(), Map.of(), List.of("industrial_production", "electricity"), List.of());
        var state = ResearchTestWorld.staffed(GameState.builder().empires(List.of(owner)).build());
        var build = new BuildFacilityCommand("research-world", "pyro_iron_smelting", "owner",
                IndustrialFacility.PUBLIC_STATE, 100, "industrial_worker");
        assertFalse(build.validate(state));
        var queue = new CommandQueue(); queue.submit(new StartResearchCommand("owner", "pyro_iron_smelting", true, 30));
        state = queue.drainAndExecute(state);
        var research = new ResearchProcessor(new Random() { @Override public double nextDouble() { return .01; } });
        for (int tick = 0; tick < 30 && !state.researchProjects().isEmpty(); tick++) {
            var result = new ResearchTickProcessor().process(state, List.of(), research);
            state = state.toBuilder().empires(result.empires()).researchProjects(result.projects()).applicationOptimizations(result.optimizations()).build();
        }
        assertTrue(state.empires().getFirst().unlockedTechIds().contains("pyro_iron_smelting"));
        state = new ResolveApplicationResearchCommand("owner", "pyro_iron_smelting", true).apply(state);
        assertFalse(new BuildFacilityCommand("research-world", "mining_outpost", "owner", IndustrialFacility.PUBLIC_STATE, 100, "miner").validate(state));
        assertFalse(new BuildFacilityCommand("research-world", "alloy_steel", "owner", IndustrialFacility.PUBLIC_STATE, 10, "industrial_worker").validate(state));
        state = discover(state, "mining_outpost", research);
        state = discover(state, "alloy_steel", research);
        queue.submit(new BuildFacilityCommand("research-world", "mining_outpost", "owner", IndustrialFacility.PUBLIC_STATE, 100, "miner"));
        queue.submit(build);
        queue.submit(new BuildFacilityCommand("research-world", "alloy_steel", "owner", IndustrialFacility.PUBLIC_STATE, 10, "industrial_worker"));
        state = queue.drainAndExecute(state);
        assertEquals(3, state.industrialFacilities().size());
        var stock = new HashMap<String, MarketOrder>();
        for (var id : List.of("refined_iron", "refined_aluminum", "refined_copper", "silicon", "carbon"))
            stock.put(id, new MarketOrder(id, 10000, 1e6, 1, 0));
        state = state.toBuilder().commercialHubs(List.of(new CommercialHub("hub", "research-world", 0, 1e8, 50000, 10, stock)))
                .marketAccounts(List.of(new MarketAccount("hub", 1e8))).build();
        var construction = new IndustryProcessor();
        for (int day = 0; day < 20 && !state.expansionProjects().isEmpty(); day++) state = construction.processIndustrialProduction(state);
        assertTrue(state.expansionProjects().isEmpty());
        assertTrue(state.industrialFacilities().stream().allMatch(f -> f.productionEquipment() != null && f.tier() == 1));
        assertTrue(state.commercialHubs().getFirst().activeOrders().get("refined_aluminum").supplyKg() < 10000);
        // Remove opening construction iron: every kg of steel must now come from the real ore chain.
        var orders = new HashMap<>(state.commercialHubs().getFirst().activeOrders());
        orders.put("refined_iron", new MarketOrder("refined_iron", 0, 1e6, 1, 0));
        double mass = orders.values().stream().mapToDouble(MarketOrder::supplyKg).sum();
        state = state.toBuilder().commercialHubs(List.of(new CommercialHub("hub", "research-world", 0, 1e8, mass, 10, orders)))
                .geologicalDeposits(List.of(new GeologicalDeposit("ore", "research-world", "iron_ore", 10000, 10000, 1, true, "owner"))).build();
        var saves = new SaveGameManager(directory);
        for (int day = 0; day < 4; day++) {
            var file = saves.save("chain", state, 1, GameClock.START_TIME.toString());
            state = saves.load(file.toFile()).toGameState(state.turn(), state.status());
            state = state.withCommercialHubs(new MarketDemandProcessor().refresh(state, DataModelLoader.loadRaces()));
            var workers = new HashMap<String, Integer>();
            state.industrialFacilities().forEach(f -> workers.put(f.id(), f.allocatedWorkers()));
            var budgets = new HashMap<String, ProductionWork.EnergyBudget>();
            state.industrialFacilities().forEach(f -> budgets.put(f.id(), new ProductionWork.EnergyBudget(24000, 0)));
            var result = new IndustryMarketProcessor().process(state, workers, Map.of(), workers, budgets);
            state = state.toBuilder().commercialHubs(result.hubs()).marketAccounts(result.marketAccounts())
                    .industryAccounts(result.industryAccounts()).geologicalDeposits(result.deposits())
                    .empires(result.empires()).corporations(result.corporations()).build();
        }
        assertTrue(state.commercialHubs().getFirst().activeOrders().get("steel").supplyKg() > 0);
        assertTrue(state.geologicalDeposits().getFirst().remainingVolumeKg() < 10000);
        assertTrue(state.commercialHubs().getFirst().activeOrders().get("carbon").supplyKg() < 10000);
    }

    private GameState discover(GameState state, String application, ResearchProcessor research) {
        var start = new StartResearchCommand("owner", application, true, 30);
        assertTrue(start.validate(state)); state = start.apply(state);
        for (int tick = 0; tick < 100 && !state.researchProjects().isEmpty(); tick++) {
            var result = new ResearchTickProcessor().process(state, List.of(), research);
            state = state.toBuilder().empires(result.empires()).researchProjects(result.projects()).applicationOptimizations(result.optimizations()).build();
        }
        assertTrue(state.empires().getFirst().unlockedTechIds().contains(application));
        return new ResolveApplicationResearchCommand("owner", application, true).apply(state);
    }

    @Test void recipeChangesCannotReplacePaidMachineryAndExpansionRetainsCapturedSkillAndAutomation() throws Exception {
        var equipment = new ProductionEquipment("saved_furnace", "SMELTING", 2000, 50, "industrial_worker", .25, .75);
        var smelter = new IndustrialFacility("smelter", "earth", "pyro_iron_smelting", "owner", IndustrialFacility.PUBLIC_STATE,
                1, 100, "industrial_worker", false, 0, equipment);
        var empire = new Empire("owner", "Owner", "human", "Individualist", 1e6, 0,
                List.of(), List.of(), Map.of(), List.of("industrial_production", "pyro_iron_smelting", "electricity", "solar_power"), List.of());
        var state = GameState.builder().empires(List.of(empire)).industrialFacilities(List.of(smelter)).build();
        assertFalse(new SetFacilityRecipeCommand("smelter", "alloy_steel").validate(state));
        assertFalse(new SetFacilityRecipeCommand("smelter", "solar_power").validate(state));
        assertTrue(new SetFacilityRecipeCommand("smelter", "pyro_iron_smelting").validate(state));
        assertEquals(equipment, new SetFacilityRecipeCommand("smelter", "pyro_iron_smelting").apply(state).industrialFacilities().getFirst().productionEquipment());
        var expanding = new ExpandFacilityCommand("smelter", 2, 1, 0).apply(state);
        assertEquals(FactoryDefinition.find("pyro_iron_smelting").expansionWorkPerTier(),
                expanding.expansionProjects().getFirst().requiredWorkHours(), 1e-8);
        assertEquals(equipment, expanding.industrialFacilities().getFirst().productionEquipment());
        var bill = expanding.expansionProjects().getFirst().requiredMaterialsKg();
        var orders = new HashMap<String, MarketOrder>(); bill.forEach((id, kg) -> orders.put(id, new MarketOrder(id, kg, 0, 1, 0)));
        expanding = expanding.withCommercialHubs(List.of(new CommercialHub("hub", "earth", 0, 1e7, 10000, 10, orders)));
        for (int tick = 0; tick < 3; tick++) expanding = new IndustryProcessor().processIndustrialProduction(expanding);
        assertEquals(2, expanding.industrialFacilities().getFirst().tier());
        assertEquals(equipment, expanding.industrialFacilities().getFirst().productionEquipment());
        assertFalse(new BuildFacilityCommand("earth", "mining_outpost", "owner", IndustrialFacility.PUBLIC_STATE, 10, "farmer").validate(state));
    }
}
