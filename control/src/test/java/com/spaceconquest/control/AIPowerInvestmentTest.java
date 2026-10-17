package com.spaceconquest.control;

import com.spaceconquest.control.ai.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.HouseholdEconomyProcessor;
import com.spaceconquest.engine.industry.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AIPowerInvestmentTest {
    @TempDir Path directory;
    GameState world(String app, String atmosphere, boolean water) {
        var empire = new Empire("owner", "Owner", "human", "Individualist", 1_000_000, 0, List.of("sol"), List.of(), Map.of(),
                List.of("electricity", "industrial_production", "pyro_iron_smelting", "alloy_steel", app), List.of());
        var planet = new Planet("world", "World", "", 1, 9.81, SolarRadiation.AU_KM, 0, 12742, "terrestrial", atmosphere, water, 1,
                List.of(), List.of(), List.of(new Population("human", Map.of(25, 20000L))));
        var stock = new HashMap<String, MarketOrder>();
        for (String material : List.of("refined_iron", "refined_aluminum", "refined_copper", "silicon", "hydrocarbons"))
            stock.put(material, new MarketOrder(material, 10000, 0, 2, 0));
        return GameState.builder().empires(List.of(empire))
                .solarSystems(List.of(new SolarSystem("sol", "Sol", "", 0, 0, 0, SolarRadiation.SOLAR_MASS_KG, 1392700, "Yellow", List.of(planet), List.of())))
                .commercialHubs(List.of(new CommercialHub("hub", "world", 0, 1000000, 50000, 10, stock)))
                .powerGrids(List.of(new PowerGridState("world", 0, 1000, -1000, 0, 0, true))).build();
    }
    private GameState payroll(GameState state) throws Exception {
        var paid = new HouseholdEconomyProcessor().process(state, DataModelLoader.loadRaces());
        return state.toBuilder().empires(paid.empires()).corporations(paid.corporations()).householdAccounts(paid.householdAccounts())
                .commercialHubs(paid.commercialHubs()).industryAccounts(paid.industryAccounts().stream()
                        .map(account -> account.withPaidWorkers(paid.paidWorkersByFacility().getOrDefault(account.facilityId(), 0))).toList())
                .marketAccounts(paid.marketAccounts()).build();
    }
    private RequestAIPowerInvestmentCommand request(GameState state) {
        var planner = new EmpirePowerPlanner(); var empire = state.empires().getFirst();
        assertTrue(planner.plan(state, empire).isEmpty()); assertTrue(planner.plan(state, empire).isEmpty());
        return (RequestAIPowerInvestmentCommand) planner.plan(state.withTurn(state.turn() + 1), empire).getFirst();
    }
    @Test void persistentShortageBuildsPaidGenerationAndCheckpointedConstructionRelievesTheGrid() throws Exception {
        var initial = world("solar_power", "breathable", true); var command = request(initial); var state = initial.withTurn(1);
        var queue = new CommandQueue(); queue.submit(command); queue.submit(command); state = queue.drainAndExecute(state);
        assertEquals(1, state.industrialFacilities().size()); assertEquals(1, state.expansionProjects().size());
        var facility = state.industrialFacilities().getFirst(); var plant = PowerPlantCatalog.find(facility.applicationId());
        assertEquals(plant.requiredWorkers(), facility.allocatedWorkers()); assertEquals(plant.workerProfessionId(), facility.workerProfessionId());
        assertEquals(initial.empires().getFirst().treasuryCredits() - plant.operatingCapitalCredits(), state.empires().getFirst().treasuryCredits());
        assertEquals(plant.constructionMaterialsKg(), state.expansionProjects().getFirst().requiredMaterialsKg());
        var generator = new PowerGenerationProcessor(); assertEquals(0, generator.process(state, Map.of(), Map.of()).generationKw().get("world"));
        state = new IndustryProcessor().processIndustrialProduction(state); assertFalse(state.expansionProjects().isEmpty());
        var file = directory.resolve("power.scsave").toFile(); var saves = new SaveGameManager(directory); saves.save(file, state, 1, GameClock.START_TIME.toString());
        state = saves.load(file).toGameState(state.turn(), state.status());
        for (int day = 0; day < 12 && !state.expansionProjects().isEmpty(); day++) state = new IndustryProcessor().processIndustrialProduction(state);
        assertTrue(state.expansionProjects().isEmpty()); assertTrue(state.empires().getFirst().treasuryCredits() < initial.empires().getFirst().treasuryCredits() - plant.operatingCapitalCredits());
        var paid = new HouseholdEconomyProcessor().process(state, DataModelLoader.loadRaces());
        assertEquals(plant.requiredWorkers(), paid.paidWorkersByFacility().get(facility.id())); assertTrue(paid.wagesByFacility().get(facility.id()) > 0);
        state = payroll(state); var generation = generator.process(state, paid.paidWorkersByFacility(), paid.wagesByFacility(), false);
        assertEquals(plant.requiredWorkers(), generation.accounts().getFirst().paidWorkers());
        assertEquals(plant.requiredWorkers(), generation.industryAccounts().getFirst().paidWorkers());
        assertEquals(plant.kwPerTier() * plant.atmosphericTransmission(), generation.generationKw().get("world"), .001);
        var power = new PowerProcessor().balanceDay(state, generation.generationKw(), paid.paidWorkersByFacility());
        assertFalse(power.grids().getFirst().isDeficitBrownoutActive()); assertTrue(power.grids().getFirst().netBalanceKw() >= 0);
        assertTrue(new EmpirePowerPlanner().plan(state.toBuilder().powerGrids(power.grids()).build(), state.empires().getFirst()).isEmpty());
    }
    @Test void staffedPlantExpandsWithoutCreatingAnotherFacilityAndUsesTheIncrementalQuote() throws Exception {
        var state = world("solar_power", "breathable", true);
        var plant = PowerPlantCatalog.find("solar_power");
        state = state.toBuilder().industrialFacilities(List.of(new IndustrialFacility("plant", "world", "solar_power", "owner",
                IndustrialFacility.PUBLIC_STATE, 1, plant.requiredWorkers(), plant.workerProfessionId(), false, 0)))
                .industryAccounts(List.of(IndustryAccount.empty("plant").withOperatingCash(plant.operatingCapitalCredits()))).build();
        state = payroll(state); var command = request(state); assertEquals("plant", command.facilityId());
        state = command.apply(state.withTurn(1)); assertEquals(1, state.industrialFacilities().size()); assertEquals(2, state.expansionProjects().getFirst().targetTier());
        assertEquals(plant.constructionMaterialsKg(), state.expansionProjects().getFirst().requiredMaterialsKg());
        assertEquals(plant.expansionWorkHoursPerTier(), state.expansionProjects().getFirst().requiredWorkHours());
        for (int day = 0; day < 12 && !state.expansionProjects().isEmpty(); day++) state = new IndustryProcessor().processIndustrialProduction(state);
        var paid = new HouseholdEconomyProcessor().process(state, DataModelLoader.loadRaces()); state = payroll(state);
        assertEquals(2 * plant.kwPerTier() * plant.atmosphericTransmission(),
                new PowerGenerationProcessor().process(state, paid.paidWorkersByFacility(), paid.wagesByFacility(), false).generationKw().get("world"), .001);
    }
    @Test void staleFundingStockResearchControlAndResolvedShortagesRejectTheQueuedProject() {
        var base = world("solar_power", "breathable", true); var command = request(base); var state = base.withTurn(1);
        var owner = state.empires().getFirst();
        for (var changed : List.of(state.withCommercialHubs(List.of()), state.withPowerGrids(List.of()),
                state.withEmpires(List.of(new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), 0, 0, owner.controlledSystemIds(), List.of(), Map.of(), owner.unlockedTechIds(), List.of()))),
                state.withEmpires(List.of(new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), owner.treasuryCredits(), 0, List.of(), List.of(), Map.of(), owner.unlockedTechIds(), List.of()))),
                state.withEmpires(List.of(new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), owner.treasuryCredits(), 0, owner.controlledSystemIds(), List.of(), Map.of(), List.of("electricity"), List.of()))))) {
            assertFalse(command.validate(changed)); assertSame(changed, command.apply(changed));
        }
    }
    @Test void environmentalFuelAndStaffingLimitsPreventUselessProjects() {
        for (String app : List.of("wind_power", "hydropower")) {
            var state = world(app, "none", false).withTurn(1);
            assertTrue(PowerInvestmentQuote.candidates(state, state.empires().getFirst(), "world").isEmpty());
        }
        var state = world("combustion_power", "none", false);
        assertFalse(PowerInvestmentQuote.candidates(state, state.empires().getFirst(), "world").isEmpty());
        var hub = state.commercialHubs().getFirst(); var orders = new HashMap<>(hub.activeOrders()); orders.remove("hydrocarbons");
        var emptyFuel = state.withCommercialHubs(List.of(hub.withInventory(0, hub.currentStoredWeightKg(), orders)));
        assertTrue(PowerInvestmentQuote.candidates(emptyFuel, emptyFuel.empires().getFirst(), "world").isEmpty());
        var occupied = state.withIndustrialFacilities(List.of(new IndustrialFacility("jobs", "world", "smelter", "owner", IndustrialFacility.PUBLIC_STATE, 1, 20000, "industrial_worker", false, 0)));
        assertTrue(PowerInvestmentQuote.candidates(occupied, occupied.empires().getFirst(), "world").isEmpty());
    }
    @Test void commissionedCombustionPlantRequiresPaidStaffAndBuysItsApplicationDefinedFuel() throws Exception {
        var initial = world("combustion_power", "none", false); var command = request(initial);
        var state = command.apply(initial.withTurn(1)); var facility = state.industrialFacilities().getFirst();
        for (int day = 0; day < 12 && !state.expansionProjects().isEmpty(); day++) state = new IndustryProcessor().processIndustrialProduction(state);
        var generator = new PowerGenerationProcessor(); assertEquals(0, generator.process(state, Map.of(), Map.of()).generationKw().get("world"));
        var paid = new HouseholdEconomyProcessor().process(state, DataModelLoader.loadRaces()); state = payroll(state);
        var plant = PowerPlantCatalog.find(facility.applicationId());
        double stock = state.commercialHubs().getFirst().activeOrders().get(plant.fuelMaterialId()).supplyKg();
        var generated = generator.process(state, paid.paidWorkersByFacility(), paid.wagesByFacility(), false);
        assertEquals(plant.kwPerTier(), generated.generationKw().get("world"), .001);
        assertEquals(stock - plant.fuelKgPerTierDay(), generated.hubs().getFirst().activeOrders().get(plant.fuelMaterialId()).supplyKg(), .001);
        assertEquals(plant.fuelKgPerTierDay() * 2, generated.accounts().getFirst().inputCostsCredits(), .001);
        assertTrue(generated.accounts().getFirst().wageCostsCredits() > 0);
    }
    @Test void transientOrSkippedObservationsDoNotTriggerAndControllerQueuesThePowerCommand() {
        var state = world("solar_power", "breathable", true); var planner = new EmpirePowerPlanner(); var empire = state.empires().getFirst();
        assertTrue(planner.plan(state, empire).isEmpty()); assertTrue(planner.plan(state.withTurn(3), empire).isEmpty());
        assertTrue(planner.plan(state.withTurn(4).withPowerGrids(List.of()), empire).isEmpty());
        assertTrue(planner.plan(state.withTurn(5), empire).isEmpty()); assertEquals(1, planner.plan(state.withTurn(6), empire).size());
        assertTrue(planner.plan(state, empire).isEmpty());
        var queue = new CommandQueue(); var controller = new EmpireAIController("owner", queue); controller.onGameStateUpdate(state);
        var first = queue.drainAndExecute(state); assertTrue(first.expansionProjects().isEmpty());
        controller.onGameStateUpdate(first.withTurn(1)); var next = queue.drainAndExecute(first.withTurn(1));
        assertEquals(1, next.expansionProjects().size()); assertEquals("solar_power", next.industrialFacilities().getFirst().applicationId());
    }
    @Test void deliveredGridBalanceDoesNotHideBrownoutDemandFromActualPaidWorkers() {
        var state = world("solar_power", "breathable", true).toBuilder()
                .industrialFacilities(List.of(new IndustrialFacility("refinery", "world", "alloy_steel", "owner", IndustrialFacility.PUBLIC_STATE, 1, 10, "industrial_worker", false, 0)))
                .industryAccounts(List.of(IndustryAccount.empty("refinery").withPaidWorkers(10)))
                .powerGrids(List.of(new PowerGridState("world", 20, 20, 0, 0, 0, true))).build();
        var oldHub = state.commercialHubs().getFirst();
        var stock = new HashMap<>(oldHub.activeOrders());
        stock.put("carbon", new MarketOrder("carbon", 20, 0, 2, 0));
        state = state.withCommercialHubs(List.of(new CommercialHub(oldHub.id(), oldHub.entityId(),
                oldHub.transactionTariffRate(), oldHub.storageCapacityKg(), oldHub.currentStoredWeightKg() + 20,
                oldHub.logisticsRangeUnits(), stock)));
        assertTrue(PowerInvestmentQuote.shortage(state, "world") > 20);
        var unpaid = state.withIndustryAccounts(List.of(IndustryAccount.empty("refinery")));
        assertEquals(0, PowerInvestmentQuote.shortage(unpaid, "world"));
    }
}
