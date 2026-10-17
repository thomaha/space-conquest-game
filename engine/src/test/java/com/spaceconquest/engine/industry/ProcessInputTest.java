package com.spaceconquest.engine.industry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.MarketAccount;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProcessInputTest {
    @Test void mixedSourcesBindEachMaterialAndConsumeSeveralDepositsWithoutReadingOutputNames() {
        var inputs = List.of(new ProcessInput(ProcessInput.Source.MARKET, "carbon", 5),
                new ProcessInput(ProcessInput.Source.DEPOSIT, "iron_ore", 100),
                new ProcessInput(ProcessInput.Source.DEPOSIT, "copper_ore", 20),
                new ProcessInput(ProcessInput.Source.SURFACE_LIQUID_WATER, "purified_water", 30));
        var deposits = new LinkedHashMap<String, GeologicalDeposit>();
        deposits.put("a", deposit("a", "iron_ore", 40)); deposits.put("b", deposit("b", "iron_ore", 60));
        deposits.put("c", deposit("c", "copper_ore", 10));
        assertEquals(Map.of("carbon", 5.0), ProcessInput.purchased(inputs));
        assertEquals(0, ProcessInput.maximumBatches(world(false, List.of(), Map.of()), "body", inputs, deposits.values()));
        assertEquals(.5, ProcessInput.maximumBatches(world(true, List.of(), Map.of()), "body", inputs, deposits.values()));
        var before = Map.copyOf(deposits);
        assertThrows(IllegalArgumentException.class, () -> ProcessInput.consumeDeposits(deposits, "body", inputs, -1));
        assertThrows(IllegalStateException.class, () -> ProcessInput.consumeDeposits(deposits, "body", inputs, 1));
        assertEquals(before, deposits);
        ProcessInput.consumeDeposits(deposits, "body", inputs, .5);
        assertEquals(0, deposits.get("a").remainingVolumeKg()); assertEquals(50, deposits.get("b").remainingVolumeKg());
        assertEquals(0, deposits.get("c").remainingVolumeKg());
    }

    @Test void competingFactoriesCannotConsumeTheSameDepositOrProduceBeyondItsActualInput() {
        var mines = List.of(facility("a", "mining_outpost"), facility("b", "mining_outpost"));
        var state = world(true, mines, Map.of()).toBuilder()
                .geologicalDeposits(List.of(deposit("a", "iron_ore", 40), deposit("b", "iron_ore", 60))).build();
        var result = execute(state, 12000);
        assertEquals(100, result.industryAccounts().stream().mapToDouble(a -> a.producedKg().getOrDefault("iron_ore", 0.0)).sum(), 1e-8);
        assertEquals(0, result.deposits().stream().mapToDouble(GeologicalDeposit::remainingVolumeKg).sum(), 1e-8);
        assertEquals(12000, result.productionEnergyKwh().get("body"), 1e-8);
    }

    @Test void iceTreatmentAccountsForUnprocessedIceAndElectrolysisScalesAllProductsAndEnergy() {
        var ice = execute(world(false, List.of(facility("work", "ice_water_treatment")), Map.of("water_ice", 500.0)), 75);
        var receipt = ice.industryAccounts().getFirst();
        assertEquals(475, receipt.producedKg().get("purified_water"), 1e-8);
        assertEquals(25, receipt.producedKg().get("water_ice"), 1e-8);
        assertEquals(25, ice.hubs().getFirst().activeOrders().get("water_ice").supplyKg(), 1e-8);
        assertEquals(75, ice.productionEnergyKwh().get("body"), 1e-8);
        var gas = execute(world(false, List.of(facility("work", "water_electrolysis")), Map.of("purified_water", 500.0)), 3050);
        assertEquals(55.55, gas.industryAccounts().getFirst().producedKg().get("hydrogen_gas"), 1e-8);
        assertEquals(444.45, gas.industryAccounts().getFirst().producedKg().get("oxygen_gas"), 1e-8);
        assertEquals(3050, gas.productionEnergyKwh().get("body"), 1e-8);
    }

    @Test void sourcePresenceControlsTheSameProcessOnWetAndDryBodies() {
        var facility = facility("arbitrary_facility_name", "surface_water_treatment");
        var dry = execute(world(false, List.of(facility), Map.of()), .4);
        assertTrue(dry.industryAccounts().getFirst().producedKg().isEmpty());
        assertTrue(dry.productionEnergyKwh().isEmpty());
        var wet = execute(world(true, List.of(facility), Map.of()), .4);
        assertEquals(1000, wet.industryAccounts().getFirst().producedKg().get("purified_water"), 1e-8);
    }

    @Test void installedParallelLinesScaleStaffingResidenceLimitsAndRetainedStorage() throws Exception {
        var equipment = new ProductionEquipment("parallel", "ALLOYING", 1e9, 10, "industrial_worker", 0, 1, 2);
        assertEquals(.5, equipment.staffedFraction(10)); assertEquals(1, equipment.staffedFraction(20));
        var fast = new ProductionEquipment("parallel", "ALLOYING", 1e9, 10, "industrial_worker", 1, 1, 2);
        var factory = new IndustrialFacility("work", "body", "alloy_steel", "corp", IndustrialFacility.PRIVATE_CORPORATE, 1, 0, "industrial_worker", false, 0, fast);
        assertEquals(40, ProductionWork.quote(world(true, List.of(factory), Map.of()), factory, 0).batchesPerDay(), 1e-8);
        var mapper = new ObjectMapper(); assertEquals(fast, mapper.readValue(mapper.writeValueAsBytes(fast), ProductionEquipment.class));
        var base = ProductionWork.initialEquipment("rare_earth_refining");
        var parallel = new ProductionEquipment(base.id(), base.processType(), base.workUnitsPerDay(), base.referenceOperators(), base.operatorProfession(), 0, 1, 2);
        var refinery = new IndustrialFacility("work", "body", "rare_earth_refining", "corp", IndustrialFacility.PRIVATE_CORPORATE, 1, 20, "industrial_worker", false, 0, parallel);
        assertEquals(200000, IndustryRecipeCatalog.find("rare_earth_refining").refineryProcess().storageCapacityKg(refinery));
    }

    private GeologicalDeposit deposit(String id, String material, double mass) {
        return new GeologicalDeposit(id, "body", material, mass, mass, 1, true, "corp");
    }
    private IndustrialFacility facility(String id, String application) {
        var equipment = ProductionWork.initialEquipment(application);
        return new IndustrialFacility(id, "body", application, "corp", IndustrialFacility.PRIVATE_CORPORATE, 1,
                equipment.referenceOperators(), equipment.operatorProfession(), false, 0);
    }
    private IndustryMarketProcessor.TurnResult execute(GameState state, double energy) {
        var workers = new HashMap<String, Integer>(); var budgets = new HashMap<String, ProductionWork.EnergyBudget>();
        state.industrialFacilities().forEach(f -> { workers.put(f.id(), f.allocatedWorkers()); budgets.put(f.id(), new ProductionWork.EnergyBudget(energy, 0)); });
        return new IndustryMarketProcessor().process(state, workers, Map.of(), workers, budgets);
    }
    private GameState world(boolean wet, List<IndustrialFacility> facilities, Map<String, Double> stock) {
        var orders = new HashMap<String, MarketOrder>();
        for (String id : List.of("water_ice", "purified_water", "oxygen_gas", "hydrogen_gas", "iron_ore", "steel"))
            orders.put(id, new MarketOrder(id, stock.getOrDefault(id, 0.0), 1e6, 1, 0));
        var planet = new Planet("body", "Body", "", 1e24, 9.81, SolarRadiation.AU_KM, 0, 12742, "terrestrial", "breathable", wet, wet ? 1 : 0, List.of(), List.of(), List.of());
        var system = new SolarSystem("sol", "Sol", "", 0, 0, 0, SolarRadiation.SOLAR_MASS_KG, 1392700, "Yellow", List.of(planet), List.of());
        return GameState.builder().solarSystems(List.of(system)).industrialFacilities(facilities)
                .empires(List.of(new Empire("owner", "Owner", "human", "Individualist", 1e6, 0, List.of("sol"), List.of(), Map.of(),
                        List.of("industrial_production", "electricity", "surface_water_treatment", "ice_water_treatment", "water_electrolysis", "mining_outpost", "pyro_iron_smelting", "alloy_steel"), List.of())))
                .corporations(List.of(new Corporation("corp", "Corp", "owner", "body", "AGRICULTURE", 1e6, List.of(), List.of(), List.of())))
                .commercialHubs(List.of(new CommercialHub("hub", "body", 0, 1e9, stock.values().stream().mapToDouble(Double::doubleValue).sum(), 10, orders)))
                .marketAccounts(List.of(new MarketAccount("hub", 1e6))).build();
    }
}
