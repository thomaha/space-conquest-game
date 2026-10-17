package com.spaceconquest.engine.industry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.market.MarketDemandProcessor;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ProductionWorkTest {
    @TempDir Path directory;

    @Test void equipmentCapsWorkWhileSkillAndAutomationChangeStaffingRatherThanTaskSize() throws Exception {
        var normal = facility("alloy_steel", null, 100);
        var state = world(List.of(normal), Map.of("refined_iron", 980.0, "carbon", 20.0), 100000);
        var full = ProductionWork.quote(state, normal, 10);
        assertEquals(full, ProductionWork.quote(state, normal, 100));
        var novice = equipment("ALLOYING", 1000, 0, .5);
        var lower = ProductionWork.quote(state, facility("alloy_steel", novice, 100), 10);
        assertEquals(full.requiredWorkPerBatch(), lower.requiredWorkPerBatch());
        assertEquals(full.workUnitsPerDay() / 2, lower.workUnitsPerDay());
        assertEquals(full.workUnitsPerDay(), ProductionWork.quote(state, facility("alloy_steel", novice, 100), 20).workUnitsPerDay());
        var autonomous = facility("alloy_steel", equipment("ALLOYING", 1000, 1, 1), 0);
        assertEquals(full.workUnitsPerDay(), ProductionWork.quote(state, autonomous, 0).workUnitsPerDay());
        assertEquals(full.requiredWorkPerBatch(), ProductionWork.quote(state, autonomous, 0).requiredWorkPerBatch());
    }

    @Test void incompatibleProcessResearchAndResidenceTimeAreRealGates() throws Exception {
        var state = world(List.of(), Map.of(), 100000);
        var wrong = facility("alloy_steel", equipment("SMELTING", 1000, 0, 1), 10);
        assertEquals(0, ProductionWork.quote(state, wrong, 10).workUnitsPerDay());
        var fast = facility("alloy_steel", equipment("ALLOYING", 1e9, 1, 1), 0);
        assertEquals(20, ProductionWork.quote(state, fast, 0).batchesPerDay(), 1e-9);
        var locked = state.withEmpires(List.of(new Empire("empire", "Empire", "human", "Individualist", 100000, 0,
                List.of("sol"), List.of(), Map.of(), List.of(), List.of())));
        assertEquals(0, ProductionWork.quote(locked, fast, 0).workUnitsPerDay());
    }

    @Test void partialMixedInputWorkScalesEveryOutputAndPaysOnlyActualEnergy() throws Exception {
        var smelter = facility("pyro_iron_smelting", null, 100);
        var state = world(List.of(smelter), Map.of("iron_ore", 500.0, "carbon", 56.25), 100000);
        var result = day(state, Map.of("work", 100), 1000);
        var account = result.production().industryAccounts().stream().filter(a -> a.facilityId().equals("work")).findFirst().orElseThrow();
        assertEquals(350, account.producedKg().get("refined_iron"), 1e-8);
        assertEquals(206.25, account.producedKg().get("carbon_dioxide_gas"), 1e-8);
        assertEquals(556.25, account.producedKg().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-8);
        assertEquals(2000, result.production().productionEnergyKwh().get("earth"), 1e-8);
        assertEquals(40, account.powerCostsCredits(), 1e-8);
        assertEquals(40, result.production().productionPlantSales().get("plant"), 1e-8);
        assertEquals(350, result.production().hubs().getFirst().activeOrders().get("refined_iron").supplyKg(), 1e-8);
        assertEquals(206.25, result.production().hubs().getFirst().activeOrders().get("carbon_dioxide_gas").supplyKg(), 1e-8);
        assertEquals(2000.0 / 24, result.grids().getFirst().totalDemandKw(), 1e-8);
        assertEquals(money(state), money(result.production()), .001);
    }

    @Test void noInputsNoPowerAndCombinedInputPowerFundingCannotCreateWork() throws Exception {
        var steel = facility("alloy_steel", null, 10);
        var empty = world(List.of(steel), Map.of(), 100000);
        assertTrue(account(day(empty, Map.of("work", 10), 1000)).producedKg().isEmpty());
        assertTrue(day(empty, Map.of("work", 10), 1000).production().productionEnergyKwh().isEmpty());
        var stocked = world(List.of(steel), Map.of("refined_iron", 980.0, "carbon", 20.0), 100000);
        assertTrue(account(day(stocked, Map.of("work", 10), 0)).producedKg().isEmpty());
        var poor = world(List.of(steel), Map.of("refined_iron", 980.0, "carbon", 20.0), 2480);
        var funded = day(poor, Map.of("work", 10), 1000);
        assertEquals(500, account(funded).producedKg().get("steel"), 1e-8);
        assertEquals(500, funded.production().productionEnergyKwh().get("earth"), 1e-8);
        assertTrue(funded.production().corporations().getFirst().liquidCapitalReserves() >= 0);
        assertEquals(money(poor), money(funded.production()), .001);
    }

    @Test void partialGridSupplyAndCompetingFactoriesCannotDoubleSpendEnergyOrMaterials() throws Exception {
        var first = facility("alloy_steel", null, 10);
        var second = new IndustrialFacility("second", "earth", "alloy_steel", "corp",
                IndustrialFacility.PRIVATE_CORPORATE, 1, 10, "industrial_worker", false, 0);
        var state = world(List.of(first, second), Map.of("refined_iron", 980.0, "carbon", 20.0), 100000);
        var result = day(state, Map.of("work", 10, "second", 10), 500.0 / 24);
        double output = result.production().industryAccounts().stream().mapToDouble(a -> a.producedKg().getOrDefault("steel", 0.0)).sum();
        assertEquals(500, output, 1e-8);
        assertEquals(500, result.production().productionEnergyKwh().get("earth"), 1e-8);
        assertTrue(result.production().hubs().getFirst().activeOrders().values().stream().allMatch(o -> o.supplyKg() >= 0));
        var competing = day(state, Map.of("work", 10, "second", 10), 1000);
        assertEquals(1000, competing.production().industryAccounts().stream().mapToDouble(a -> a.producedKg().getOrDefault("steel", 0.0)).sum(), 1e-8);
        assertEquals(1000, competing.production().productionEnergyKwh().get("earth"), 1e-8);
    }

    @Test void autonomousMachineryStillNeedsRealEnergyAndRetainsItsEquipmentThroughDiskReload() throws Exception {
        var factory = facility("alloy_steel", equipment("ALLOYING", 1000, 1, .8), 0);
        var state = world(List.of(factory), Map.of("refined_iron", 980.0, "carbon", 20.0), 100000);
        var manager = new SaveGameManager(directory);
        var file = manager.save("automated", state, 1, GameClock.START_TIME.toString());
        var loaded = manager.load(file.toFile()).toGameState(state.turn(), state.status());
        assertEquals(factory.productionEquipment(), loaded.industrialFacilities().getFirst().productionEquipment());
        assertEquals(1000, account(day(loaded, Map.of(), 1000)).producedKg().get("steel"), 1e-8);
        assertTrue(account(day(loaded, Map.of(), 0)).producedKg().isEmpty());
        var mapper = new ObjectMapper();
        var old = mapper.readTree(mapper.writeValueAsString(facility("alloy_steel", null, 10)));
        ((com.fasterxml.jackson.databind.node.ObjectNode) old).remove("productionEquipment");
        ((com.fasterxml.jackson.databind.node.ObjectNode) old).remove("effectiveThroughputMultiplier");
        assertNotNull(mapper.treeToValue(old, IndustrialFacility.class).productionEquipment());
    }

    @Test void mixedStoredAndGeneratedElectricityPreservesUnpricedBatteryPolicy() throws Exception {
        var state = world(List.of(facility("alloy_steel", null, 10)), Map.of("refined_iron", 980.0, "carbon", 20.0), 100000)
                .withPowerGrids(List.of(new PowerGridState("earth", 0, 0, 0, 1000, 500, false)));
        var result = day(state, Map.of("work", 10), 500.0 / 24);
        assertEquals(1000, account(result).producedKg().get("steel"), 1e-8);
        assertEquals(10, account(result).powerCostsCredits(), 1e-8);
        assertEquals(0, result.grids().getFirst().currentStoredKwh(), 1e-8);
        assertEquals(money(state), money(result.production()), .001);
    }

    @Test void normalEngineTickPublishesActualWorkEnergyAndHiveMaterialTransfers() throws Exception {
        var steel = new IndustrialFacility("work", "earth", "alloy_steel", "empire", IndustrialFacility.HIVE_GRID,
                1, 10, "industrial_worker", false, 0);
        var plant = new IndustrialFacility("plant", "earth", "solar_power", "empire", IndustrialFacility.HIVE_GRID,
                1, 100, "technician", false, 0);
        var state = world(List.of(), Map.of("refined_iron", 980.0, "carbon", 20.0), 0);
        var empire = state.empires().getFirst();
        empire = new Empire(empire.id(), empire.name(), empire.raceId(), "Hive Mind", 0, 0,
                empire.controlledSystemIds(), empire.ministries(), empire.systemGovernorAssignments(), empire.unlockedTechIds(), List.of());
        var planet = new Planet("earth", "Earth", "", 5.97e24, 9.81, SolarRadiation.AU_KM, 0, 12742,
                "terrestrial", "breathable", true, 1, List.of(), List.of(), List.of(new Population("human", Map.of(25, 20000L))));
        var system = new SolarSystem("sol", "Sol", "", 0, 0, 0, SolarRadiation.SOLAR_MASS_KG, 1392700, "Yellow", List.of(planet), List.of());
        state = state.toBuilder().empires(List.of(empire)).corporations(List.of()).industrialFacilities(List.of(steel, plant))
                .solarSystems(List.of(system)).build();
        var engine = new SpaceConquestEngine(); engine.applyGameState(state); engine.stepTurn();
        var result = engine.getGameState();
        var receipt = result.industryAccounts().stream().filter(a -> a.facilityId().equals("work")).findFirst().orElseThrow();
        assertEquals(1000, receipt.producedKg().get("steel"), 1e-8);
        assertEquals(1000, result.commercialHubs().getFirst().activeOrders().get("steel").supplyKg(), 1e-8);
        assertEquals(1000.0 / 24, result.powerGrids().getFirst().totalDemandKw(), 1e-8);
        assertEquals(0, receipt.powerCostsCredits());
        assertEquals(1000, result.industryAccounts().stream().filter(a -> a.facilityId().equals("plant"))
                .findFirst().orElseThrow().generatedKwh(), 1e-8);
    }

    private ProductionEquipment equipment(String process, double capacity, double automation, double skill) {
        return new ProductionEquipment("test_line", process, capacity, 10, "industrial_worker", automation, skill);
    }
    private IndustrialFacility facility(String recipe, ProductionEquipment equipment, int workers) {
        var profile = ProductionWork.definition(recipe);
        return new IndustrialFacility("work", "earth", recipe, "corp", IndustrialFacility.PRIVATE_CORPORATE,
                1, workers, profile.equipment().operatorProfession(), false, 0, equipment);
    }
    private GameState world(List<IndustrialFacility> factories, Map<String, Double> inputs, double funds) throws Exception {
        var orders = new HashMap<String, MarketOrder>();
        inputs.forEach((id, kg) -> orders.put(id, new MarketOrder(id, kg, 1000,
                com.spaceconquest.engine.market.MarketProcessor.basePricePerKg(id), 0)));
        for (var facility : factories) for (var output : IndustryRecipeCatalog.find(facility.applicationId()).outputsKg().keySet())
            orders.putIfAbsent(output, new MarketOrder(output, 0, 100000, 10, 0));
        var all = new ArrayList<>(factories);
        all.add(new IndustrialFacility("plant", "earth", "solar_power", "empire", IndustrialFacility.PUBLIC_STATE, 1, 100, "technician", false, 0));
        return GameState.builder()
                .empires(List.of(new Empire("empire", "Empire", "human", "Individualist", 100000, 0,
                        List.of("sol"), List.of(), Map.of(), List.of("industrial_production", "electricity", "pyro_iron_smelting", "alloy_steel", "mining_outpost", "solar_power"), List.of())))
                .corporations(List.of(new Corporation("corp", "Corp", "empire", "earth", "INDUSTRY", funds,
                        factories.stream().map(IndustrialFacility::id).toList(), List.of(), List.of())))
                .industrialFacilities(all).commercialHubs(List.of(new CommercialHub("hub", "earth", 0, 1e8,
                        inputs.values().stream().mapToDouble(Double::doubleValue).sum(), 10, orders)))
                .marketAccounts(List.of(new MarketAccount("hub", 1e8))).build();
    }
    private record Day(IndustryMarketProcessor.TurnResult production, List<PowerGridState> grids) {}
    private Day day(GameState state, Map<String, Integer> operators, double generationKw) {
        var generator = new IndustryAccount("plant", Map.of(), Map.of(), Map.of(), 0, 0, 0, 0, generationKw * 24, 0).withOperatingCash(10000);
        state = state.toBuilder().industryAccounts(List.of(generator)).build();
        var balance = new PowerProcessor().balanceDay(state, Map.of("earth", generationKw), operators);
        var billing = new PowerBillingProcessor().process(state, balance, List.of(generator), operators, Map.of("earth", .02));
        var billed = state.toBuilder().industryAccounts(billing.industryAccounts()).corporations(billing.corporations())
                .empires(billing.empires()).powerGrids(billing.grids()).build();
        var result = new IndustryMarketProcessor().process(billed, billing.poweredWorkers(), Map.of(), operators, billing.productionEnergy());
        return new Day(result, ProductionWork.settleGrids(state, billing.grids(), result.productionEnergyKwh()));
    }
    private IndustryAccount account(Day day) {
        return day.production().industryAccounts().stream().filter(a -> a.facilityId().equals("work")).findFirst().orElseThrow();
    }
    private double money(GameState state) {
        return state.corporations().stream().mapToDouble(Corporation::liquidCapitalReserves).sum()
                + state.marketAccounts().stream().mapToDouble(MarketAccount::unsettledSalesCredits).sum();
    }
    private double money(IndustryMarketProcessor.TurnResult result) {
        // The helper gives the public plant a separate 10,000-credit opening account.
        return result.corporations().stream().mapToDouble(Corporation::liquidCapitalReserves).sum()
                + result.marketAccounts().stream().mapToDouble(MarketAccount::unsettledSalesCredits).sum()
                + result.industryAccounts().stream().mapToDouble(IndustryAccount::operatingCashCredits).sum()
                - 10000 + result.industryAccounts().stream().mapToDouble(IndustryAccount::maintenanceCostsCredits).sum();
    }
}
