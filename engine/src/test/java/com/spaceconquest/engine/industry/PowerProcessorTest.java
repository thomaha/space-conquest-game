package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.GameState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class PowerProcessorTest {

    private PowerProcessor powerProcessor;

    @BeforeEach
    public void setUp() {
        powerProcessor = new PowerProcessor();
    }

    @Test
    public void testPowerSurplusChargesBattery() {
        // Gen: 1000 kW, Demand: 600 kW, Net: +400 kW, Battery initial: 200 kWh, Max: 1000 kWh
        PowerGridState grid = powerProcessor.balanceGrid("earth", 1000.0, 600.0, 200.0, 1000.0);

        assertEquals(400.0, grid.netBalanceKw(), 0.001);
        assertEquals(1000.0, grid.currentStoredKwh(), 0.001);
        assertFalse(grid.isDeficitBrownoutActive());
    }

    @Test
    public void testPowerDeficitDischargesBatteryBuffer() {
        // Gen: 500 kW, Demand: 700 kW, Net: -200 kW, Battery initial: 500 kWh
        PowerGridState grid = powerProcessor.balanceGrid("mars", 500.0, 700.0, 500.0, 1000.0);

        assertEquals(-200.0, grid.netBalanceKw(), 0.001);
        assertEquals(0.0, grid.currentStoredKwh(), 0.001);
        assertTrue(grid.isDeficitBrownoutActive());
    }

    @Test
    public void testBatteryDepletionTriggersBrownout() {
        // Gen: 200 kW, Demand: 800 kW, Net: -600 kW, Battery initial: 100 kWh
        PowerGridState grid = powerProcessor.balanceGrid("luna", 200.0, 800.0, 100.0, 500.0);

        assertEquals(-600.0, grid.netBalanceKw(), 0.001);
        assertEquals(0.0, grid.currentStoredKwh(), 0.001);
        assertTrue(grid.isDeficitBrownoutActive(), "Empty battery during deficit must trigger brownout");
    }

    @Test
    public void testEmergencyLoadSheddingProtectsFarmsAndLifeSupport() {
        IndustrialFacility heavyFoundry = new IndustrialFacility(
                "fac_foundry", "earth", "refining_blast_furnace", "emp_terran",
                IndustrialFacility.PUBLIC_STATE, 2, 50, "industrial_worker", false, 0.0
        );

        IndustrialFacility hydroFarm = new IndustrialFacility(
                "fac_farm", "earth", "hydroponic_growth_array", "emp_terran",
                IndustrialFacility.PUBLIC_STATE, 1, 20, "farmer", false, 0.0
        );

        List<IndustrialFacility> processed = powerProcessor.applyEmergencyLoadShedding(
                List.of(heavyFoundry, hydroFarm), true
        );

        assertEquals(2, processed.size());

        IndustrialFacility updatedFoundry = processed.stream().filter(f -> f.id().equals("fac_foundry")).findFirst().orElseThrow();
        IndustrialFacility updatedFarm = processed.stream().filter(f -> f.id().equals("fac_farm")).findFirst().orElseThrow();

        assertEquals(0, updatedFoundry.allocatedWorkers(), "Heavy foundry should have workers idled during brownout");
        assertEquals(20, updatedFarm.allocatedWorkers(), "Hydroponic farm should remain fully operational");
    }

    @Test
    void gridAllocatesLimitedGenerationToFoodBeforeSmelting() throws Exception {
        IndustrialFacility farm = new IndustrialFacility("farm", "earth", "industrial_soil_cultivation",
                "empire", IndustrialFacility.PUBLIC_STATE, 1, 100, "farmer", false, 0.0);
        IndustrialFacility smelter = new IndustrialFacility("smelter", "earth", "pyro_iron_smelting",
                "empire", IndustrialFacility.PUBLIC_STATE, 1, 100, "industrial_worker", false, 0.0);
        GameState state = GameState.builder().industrialFacilities(List.of(farm, smelter))
                .solarSystems(com.spaceconquest.engine.DataModelLoader.loadSolarSystems())
                .empires(List.of(new com.spaceconquest.engine.Empire("empire", "Empire", "human", "Individualist", 10000, 0,
                        List.of("sol"), List.of(), Map.of(), List.of("industrial_production", "industrial_soil_cultivation", "pyro_iron_smelting"), List.of())))
                .commercialHubs(List.of(new com.spaceconquest.engine.CommercialHub("hub", "earth", 0, 10000, 2000, 10,
                        Map.of("nitrates", new com.spaceconquest.engine.MarketOrder("nitrates", 100, 0, 1, 0),
                                "phosphates", new com.spaceconquest.engine.MarketOrder("phosphates", 100, 0, 1, 0),
                                "potash", new com.spaceconquest.engine.MarketOrder("potash", 100, 0, 1, 0),
                                "iron_ore", new com.spaceconquest.engine.MarketOrder("iron_ore", 1000, 0, 1, 0),
                                "carbon", new com.spaceconquest.engine.MarketOrder("carbon", 200, 0, 1, 0)))))
                .powerGrids(List.of(new PowerGridState("earth", 99_000.0, 1_000.0, 98_000.0,
                        0.0, 0.0, false))).build();

        var result = powerProcessor.balanceDay(state, Map.of("earth", 25.0 / 48),
                Map.of("farm", 100, "smelter", 100));
        assertEquals(50, result.poweredWorkers().get("farm"));
        assertEquals(0, result.poweredWorkers().get("smelter"));
        assertEquals(12.5, result.productionEnergyKwh().get("farm"), 0.001);
        assertEquals(25.0 / 48, result.grids().getFirst().totalGenerationKw(), 0.001);
        assertTrue(result.grids().getFirst().isDeficitBrownoutActive());
    }
}
