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

class ControlledCultivationLifecycleTest {
    @TempDir Path directory;
    @Test void hydroponicsResearchConstructionAndPaidDrySiteHarvestFeedTheFoodFactoryAcrossReload() throws Exception {
        chain("hydroponic_growth_arrays", false);
    }
    @Test void automatedBiospheresResearchTheirPrerequisiteAndPayForLargerClosedCycles() throws Exception {
        chain("automated_biosphere_macro_farms", false);
    }
    @Test void orbitalHydroponicsUsesTheSamePaidConstructionPowerAndMarketCycle() throws Exception {
        chain("hydroponic_growth_arrays", true);
    }

    private void chain(String application, boolean orbital) throws Exception {
        var owner = new Empire("owner", "Owner", "human", "Individualist", 1e8, 0, List.of("sol"), List.of(), Map.of(),
                List.of("industrial_production", "electricity", "computers", "robotics", "nuclear_fission", "nuclear_power_app", "space_stations"), List.of());
        var state = ResearchTestWorld.staffed(GameState.builder().empires(List.of(owner)).corporations(List.of(
                new Corporation("corp", "Grower", "owner", "research-world", "AGRICULTURE", 1e8, List.of(), List.of(), List.of()),
                new Corporation("grid", "Grid", "owner", "research-world", "POWER", 1e8, List.of(), List.of(), List.of()))).build());
        var old = state.solarSystems().getFirst(); var p = old.planets().getFirst();
        var dry = new Planet(p.id(), p.name(), "", p.mass(), p.gravity(), p.distance(), 0, p.diameter(), "barren", "none", false, 0, p.resources(), p.moons(), p.populations());
        state = state.withSolarSystems(List.of(new SolarSystem(old.id(), old.name(), "", 0, 0, 0, 1, 1, "Yellow", List.of(dry), List.of())));
        String site = orbital ? "grow-station" : p.id();
        if (orbital) state = state.toBuilder().orbitalStations(List.of(new com.spaceconquest.engine.macrostructure.OrbitalStation(site,
                "Growing station", "sol", p.id(), "owner", "PUBLIC_STATE", 12, List.of(), Map.of(), 0, 0, 0, 0, 100, 100, "steel", 1, true))).build();
        state = research(state, "industrial_soil_cultivation");
        assertFalse(new BuildFacilityCommand(site, "industrial_soil_cultivation", "corp", "PRIVATE_CORPORATE", 1, "farmer").validate(state));
        assertFalse(new BuildFacilityCommand(site, application, "corp", "PRIVATE_CORPORATE", 4, "farmer").validate(state));
        state = research(state, "hydroponic_growth_arrays");
        if (!application.equals("hydroponic_growth_arrays")) state = research(state, application);
        state = research(state, "food_processing");
        var queue = new CommandQueue();
        queue.submit(new BuildFacilityCommand(site, application, "corp", "PRIVATE_CORPORATE", 4, "farmer"));
        queue.submit(new BuildFacilityCommand(site, "food_processing", "corp", "PRIVATE_CORPORATE", 10, "industrial_worker"));
        state = queue.drainAndExecute(state); assertEquals(2, state.expansionProjects().size());
        var recipe = IndustryRecipeCatalog.find(application); var orders = new HashMap<String, MarketOrder>();
        var goods = new HashSet<>(recipe.infrastructure().constructionMaterialsKg().keySet()); goods.addAll(recipe.inputsKg().keySet());
        goods.addAll(ConstructionMaterialCatalog.facility("food_processing", 1).keySet()); goods.add("refined_uranium");
        goods.forEach(id -> orders.put(id, new MarketOrder(id, 1e6, 1e6, 1, 0)));
        recipe.outputsKg().keySet().forEach(id -> orders.putIfAbsent(id, new MarketOrder(id, 0, 1e6, 1, 0)));
        orders.put("food_matrix", new MarketOrder("food_matrix", 0, 1e6, 1, 0)); orders.put("harvested_crops", new MarketOrder("harvested_crops", 0, 1e6, 1, 0));
        state = state.toBuilder().commercialHubs(List.of(new CommercialHub("hub", site, 0, 1e9, orders.values().stream().mapToDouble(MarketOrder::supplyKg).sum(), 10, orders)))
                .marketAccounts(List.of(new MarketAccount("hub", 1e8))).build();
        for (int day = 0; day < 100 && !state.expansionProjects().isEmpty(); day++) state = new IndustryProcessor().processIndustrialProduction(state);
        assertTrue(state.expansionProjects().isEmpty());
        assertTrue(state.commercialHubs().getFirst().activeOrders().get("purified_water").supplyKg() < 1e6, "Enclosure commissioning consumes the initial water fill");
        var facilities = new ArrayList<>(state.industrialFacilities());
        facilities.add(new IndustrialFacility("generator", site, "nuclear_power_app", "grid", "PRIVATE_CORPORATE", 1, 100, "technician", false, 0));
        state = state.toBuilder().industrialFacilities(facilities).householdAccounts(List.of()).build();
        var farm = facilities.getFirst(); var factory = facilities.get(1); var saves = new SaveGameManager(directory);
        double inputCosts = 0; double powerCosts = 0;
        for (int day = 0; day < recipe.farming().growingDays(); day++) {
            state = step(state.withTurn(day)); var account = account(state, farm.id());
            inputCosts += account.inputCostsCredits(); powerCosts += account.powerCostsCredits();
            if (day < recipe.farming().growingDays() - 1) assertTrue(account.producedKg().isEmpty());
            if (day == 0) {
                var file = saves.save("growing", state, 1, GameClock.START_TIME.toString());
                var loaded = saves.load(file.toFile()).toGameState(state.turn(), state.status());
                assertEquals(state.industryAccounts(), loaded.industryAccounts()); state = loaded;
                assertBlockedInputs(state, farm, recipe);
            }
        }
        assertEquals(recipe.outputsKg().get("food_matrix"), account(state, farm.id()).soldKg().get("food_matrix"), 1e-6);
        assertTrue(powerCosts >= recipe.work().energyKwhPerBatch() * PowerBillingProcessor.PRICE_PER_KWH - 1e-5,
                "Actual generator costs may raise the metered tariff above its baseline floor");
        assertEquals(recipe.inputsKg().values().stream().mapToDouble(Double::doubleValue).sum(), inputCosts, 1e-5);
        state = step(state.withTurn(state.turn() + 1));
        assertTrue(account(state, factory.id()).producedKg().getOrDefault("food_matrix", 0.0) > 0,
                "The next tick funds factory power against the newly delivered crop stock");
        var file = saves.save("harvested", state, 1, GameClock.START_TIME.toString());
        assertEquals(state.industryAccounts(), saves.load(file.toFile()).toGameState(state.turn(), state.status()).industryAccounts());
    }

    private void assertBlockedInputs(GameState state, IndustrialFacility farm, IndustryRecipeCatalog.Recipe recipe) {
        var workers = Map.of(farm.id(), 4); var budget = Map.of(farm.id(), new ProductionWork.EnergyBudget(1e6, 1e6));
        for (String id : List.of("purified_water", "carbon_dioxide_gas", "nitrates")) {
            var hub = state.commercialHubs().getFirst(); var orders = new HashMap<>(hub.activeOrders());
            orders.put(id, new MarketOrder(id, 0, 1e6, 1, 0));
            var missing = state.withTurn(state.turn() + 1).withCommercialHubs(List.of(new CommercialHub(hub.id(), hub.entityId(), 0, 1e9, 1e7, 10, orders)));
            var result = new IndustryMarketProcessor().process(missing, workers, Map.of(), workers, budget);
            assertEquals(account(state, farm.id()).farmingState(), result.industryAccounts().stream().filter(a -> a.facilityId().equals(farm.id())).findFirst().orElseThrow().farmingState(), id);
        }
        var unpowered = new IndustryMarketProcessor().process(state.withTurn(state.turn() + 1), workers, Map.of(), workers, Map.of());
        assertEquals(account(state, farm.id()).farmingState(), unpowered.industryAccounts().stream().filter(a -> a.facilityId().equals(farm.id())).findFirst().orElseThrow().farmingState());
    }
    private GameState step(GameState state) {
        var workers = new HashMap<String, Integer>(); state.industrialFacilities().forEach(f -> workers.put(f.id(), f.allocatedWorkers()));
        double cash = money(state);
        var power = new PowerGenerationProcessor().process(state, workers, Map.of());
        state = state.toBuilder().empires(power.empires()).corporations(power.corporations()).commercialHubs(power.hubs()).marketAccounts(power.marketAccounts()).industryAccounts(power.industryAccounts()).build();
        var balance = new PowerProcessor().balanceDay(state, power.generationKw(), workers);
        var billed = new PowerBillingProcessor().process(state, balance, power.accounts(), workers);
        state = state.toBuilder().empires(billed.empires()).corporations(billed.corporations()).industryAccounts(billed.industryAccounts()).build();
        var result = new IndustryMarketProcessor().process(state, billed.poweredWorkers(), Map.of(), workers, billed.productionEnergy());
        assertTrue(result.productionPlantSales().getOrDefault("generator", 0.0) > 0);
        state = state.toBuilder().empires(result.empires()).corporations(result.corporations()).commercialHubs(result.hubs()).marketAccounts(result.marketAccounts()).industryAccounts(result.industryAccounts()).build();
        assertEquals(cash, money(state), 1e-5); return state;
    }
    private double money(GameState state) { return state.corporations().stream().mapToDouble(Corporation::liquidCapitalReserves).sum() + state.marketAccounts().stream().mapToDouble(MarketAccount::unsettledSalesCredits).sum(); }
    private IndustryAccount account(GameState state, String id) { return state.industryAccounts().stream().filter(a -> a.facilityId().equals(id)).findFirst().orElseThrow(); }
    private GameState research(GameState state, String id) {
        var start = new StartResearchCommand("owner", id, true, 30); assertTrue(start.validate(state)); state = start.apply(state);
        var processor = new ResearchProcessor(new Random() { @Override public double nextDouble() { return .01; } });
        for (int tick = 0; tick < 100 && !state.researchProjects().isEmpty(); tick++) {
            var result = new ResearchTickProcessor().process(state, List.of(), processor);
            state = state.toBuilder().empires(result.empires()).researchProjects(result.projects()).applicationOptimizations(result.optimizations()).build();
        }
        assertTrue(state.empires().getFirst().unlockedTechIds().contains(id));
        return new ResolveApplicationResearchCommand("owner", id, true).apply(state);
    }
}
