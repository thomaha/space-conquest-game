package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.MarketOrder;
import com.spaceconquest.engine.Moon;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.economy.MarketAccount;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerGenerationProcessorTest {
    private final PowerGenerationProcessor generator = new PowerGenerationProcessor();

    @Test
    void powerPlantTechnologiesAndFuelsExistInTheCatalogs() throws IOException {
        var technologies = DataModelLoader.loadTechnologies().stream().map(item -> item.id()).toList();
        var materials = DataModelLoader.loadMaterials().stream().map(item -> item.id()).toList();
        var applications = DataModelLoader.loadTechnologies().stream()
                .flatMap(item -> item.applications().stream()).map(item -> item.id()).toList();
        for (var entry : PowerPlantCatalog.all().entrySet()) {
            assertTrue(applications.contains(entry.getKey()), entry.getKey());
            assertTrue(technologies.contains(entry.getValue().requiredTechnology()), entry.getKey());
            assertTrue(entry.getValue().fuelMaterialId() == null
                    || materials.contains(entry.getValue().fuelMaterialId()), entry.getKey());
        }
    }

    @Test
    void staffedReactorPaysForFuelAndReplacesStoredGenerationEstimate() {
        GameState state = state(List.of("electricity", "nuclear_fission"), 10.0, 500.0);
        var result = generator.process(state, Map.of("reactor", 100), Map.of("reactor", 40.0));

        assertEquals(10_000.0, result.generationKw().get("earth"), 0.001);
        assertEquals(240_000.0, result.accounts().getFirst().generatedKwh(), 0.001);
        assertEquals(20.0, result.accounts().getFirst().inputCostsCredits(), 0.001);
        assertEquals(480.0, result.empires().getFirst().treasuryCredits(), 0.001);
        assertEquals(0.0, result.hubs().getFirst().activeOrders().get("refined_uranium").supplyKg(), 0.001);
        assertEquals(20.0, result.marketAccounts().getFirst().unsettledSalesCredits(), 0.001);

        GameState fueled = state.toBuilder().empires(result.empires()).commercialHubs(result.hubs())
                .marketAccounts(result.marketAccounts()).build();
        var grid = new PowerProcessor().balanceDay(fueled, result.generationKw(), Map.of()).grids().getFirst();
        assertEquals(10_000.0, grid.totalGenerationKw(), 0.001);
        assertFalse(grid.isDeficitBrownoutActive());
    }

    @Test
    void missingFuelOrResearchStopsGenerationAndCausesBrownout() {
        var noFuel = generator.process(state(List.of("electricity", "nuclear_fission"), 0.0, 500.0),
                Map.of("reactor", 100), Map.of());
        var noResearch = generator.process(state(List.of("electricity"), 10.0, 500.0),
                Map.of("reactor", 100), Map.of());
        assertEquals(0.0, noFuel.generationKw().get("earth"), 0.001);
        assertEquals(0.0, noResearch.generationKw().get("earth"), 0.001);
        assertTrue(new PowerProcessor().balanceDay(state(List.of("electricity"), 0.0, 500.0),
                noResearch.generationKw(), Map.of()).grids().getFirst().isDeficitBrownoutActive());
    }

    @Test
    void renewableCombustionAndFusionPlantsUseDistinctFuelRules() {
        GameState base = state(List.of("electricity", "nuclear_fusion"), 0.0, 500.0);
        IndustrialFacility solar = plant("solar", "solar_power");
        IndustrialFacility combustion = plant("burner", "combustion_power");
        IndustrialFacility fusion = plant("fusion", "fusion_power_app");
        CommercialHub hub = new CommercialHub("hub", "earth", 0.05, 1_000.0, 105.0, 10.0,
                Map.of("hydrocarbons", new MarketOrder("hydrocarbons", 100.0, 0.0, 2.0, 0.0),
                        "fusion_fuel_pellets", new MarketOrder("fusion_fuel_pellets", 5.0, 0.0, 2.0, 0.0)));
        GameState state = base.toBuilder().industrialFacilities(List.of(solar, combustion, fusion))
                .commercialHubs(List.of(hub)).build();

        var result = generator.process(state, Map.of("solar", 100, "burner", 100, "fusion", 100), Map.of());
        assertEquals(35_500.0, result.generationKw().get("earth"), 0.001);
        assertEquals(0.0, result.accounts().get(0).inputCostsCredits(), 0.001);
        assertEquals(200.0, result.accounts().get(1).inputCostsCredits(), 0.001);
        assertEquals(10.0, result.accounts().get(2).inputCostsCredits(), 0.001);
        assertEquals(0.0, result.hubs().getFirst().activeOrders().get("hydrocarbons").supplyKg(), 0.001);
        assertEquals(0.0, result.hubs().getFirst().activeOrders().get("fusion_fuel_pellets").supplyKg(), 0.001);
    }

    @Test
    void airlessDryMoonSupportsSolarButNotWindOrHydro() {
        Moon moon = new Moon("moon", "Moon", "", 1.0, 1.0, 384_400.0, 3_474.0,
                "none", false, 0.0, List.of(), List.of());
        Planet earth = new Planet("earth", "Earth", "", 1.0, 1.0, 149_600_000.0,
                0.0, 12_742.0, "terrestrial", "nitrogen_oxygen", true, 0.7,
                List.of(), List.of(moon), List.of());
        SolarSystem sol = new SolarSystem("sol", "Sol", "", 0.0, 0.0, 0.0,
                1.0, 1.0, "Yellow", List.of(earth), List.of());
        GameState state = state(List.of("electricity"), 0.0, 500.0).toBuilder()
                .solarSystems(List.of(sol))
                .industrialFacilities(List.of(plant("solar", "solar_power", "moon"),
                        plant("wind", "wind_power", "moon"),
                        plant("hydro", "hydropower", "moon"))).build();

        var result = generator.process(state, Map.of("solar", 100, "wind", 100, "hydro", 100), Map.of());
        assertEquals(6_000.0, result.generationKw().get("moon"), 0.001);
        assertEquals(0.0, result.accounts().get(1).generatedKwh(), 0.001);
        assertEquals(0.0, result.accounts().get(2).generatedKwh(), 0.001);
    }

    private IndustrialFacility plant(String id, String application) {
        return plant(id, application, "earth");
    }

    private IndustrialFacility plant(String id, String application, String bodyId) {
        return new IndustrialFacility(id, bodyId, application, "empire",
                IndustrialFacility.PUBLIC_STATE, 1, 100, "technician", false, 0.0);
    }

    private GameState state(List<String> tech, double fuelKg, double treasury) {
        Empire empire = new Empire("empire", "Empire", "human", "Individualist", treasury,
                0.2, List.of("sol"), List.of(), Map.of(), tech, List.of());
        IndustrialFacility reactor = new IndustrialFacility("reactor", "earth", "nuclear_power_app",
                "empire", IndustrialFacility.PUBLIC_STATE, 1, 100, "engineer", false, 0.0);
        CommercialHub hub = new CommercialHub("hub", "earth", 0.05, 1_000.0, fuelKg, 10.0,
                Map.of("refined_uranium", new MarketOrder("refined_uranium", fuelKg, 0.0, 2.0, 0.0)));
        return GameState.builder().empires(List.of(empire)).industrialFacilities(List.of(reactor))
                .commercialHubs(List.of(hub)).marketAccounts(List.of(new MarketAccount("hub", 0.0)))
                .powerGrids(List.of(new PowerGridState("earth", 99_000.0, 5_000.0, 94_000.0,
                        0.0, 0.0, false))).build();
    }
}
