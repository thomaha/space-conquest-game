package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.economy.HouseholdAccount;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerBillingProcessorTest {
    private final PowerBillingProcessor billing = new PowerBillingProcessor();

    @Test
    void industrialShiftsPayPlantAndStopWhenOwnerCannotAffordElectricity() {
        GameState state = state(1_000.0, 10.0);
        var requested = new PowerProcessor().balanceDay(state, Map.of("earth", 6_000.0),
                Map.of("farm", 100));
        var result = billing.process(state, requested, List.of(generation(144_000.0)),
                Map.of("farm", 100));

        assertEquals(83, result.poweredWorkers().get("farm"));
        assertEquals(996.0, result.facilityPowerCosts().get("farm"), 0.001);
        assertEquals(4.0, result.corporations().getFirst().liquidCapitalReserves(), 0.001);
        assertEquals(2.0, result.households().getFirst().electricitySpendingCredits(), 0.001);
        assertEquals(0.0, result.households().getFirst().unmetBasicElectricityKwh(), 0.001);
        assertEquals(8.0, result.households().getFirst().savingsCredits(), 0.001);
        assertEquals(998.0, result.plantSales().get("plant"), 0.001);
        assertEquals(1_998.0, result.empires().getFirst().treasuryCredits(), 0.001);
        assertEquals(998.0, result.imperialReceipts().get("empire"), 0.001);
        assertEquals((100.0 + 60_000.0 * 0.83) / 24.0,
                result.grids().getFirst().totalDemandKw(), 0.001);
    }

    @Test
    void unmetHouseholdElectricityDoesNotCreateRevenueOrNegativeSavings() {
        GameState state = state(100.0, 1.0);
        var requested = new PowerProcessor().balanceDay(state, Map.of("earth", 0.0), Map.of());
        var result = billing.process(state, requested, List.of(generation(0.0)), Map.of());

        assertEquals(100.0, result.households().getFirst().unmetBasicElectricityKwh(), 0.001);
        assertEquals(0.0, result.households().getFirst().electricitySpendingCredits(), 0.001);
        assertEquals(1.0, result.households().getFirst().savingsCredits(), 0.001);
        assertTrue(result.plantSales().isEmpty());
    }

    @Test
    void batteriesCanSupplyAlreadyStoredPowerWithoutInventingPlantRevenue() {
        GameState state = state(100.0, 1.0).toBuilder()
                .powerGrids(List.of(new PowerGridState("earth", 0.0, 100.0 / 24.0,
                        -100.0 / 24.0, 100.0, 100.0, false))).build();
        var requested = new PowerProcessor().balanceDay(state, Map.of("earth", 0.0), Map.of());
        var result = billing.process(state, requested, List.of(generation(0.0)), Map.of());

        assertEquals(0.0, result.households().getFirst().unmetBasicElectricityKwh(), 0.001);
        assertEquals(0.0, result.households().getFirst().electricitySpendingCredits(), 0.001);
        assertEquals(0.0, result.grids().getFirst().currentStoredKwh(), 0.001);
        assertTrue(result.plantSales().isEmpty());
    }

    private GameState state(double corporateCash, double householdCash) {
        Empire empire = new Empire("empire", "Empire", "human", "Individualist", 1_000.0,
                0.2, List.of("sol"), List.of(), Map.of(),
                List.of("electricity", "industrial_production"), List.of());
        Corporation corporation = new Corporation("corp", "Farm", "empire", "earth",
                "AGRICULTURE", corporateCash, List.of("farm"), List.of(), List.of());
        IndustrialFacility plant = new IndustrialFacility("plant", "earth", "solar_power", "empire",
                IndustrialFacility.PUBLIC_STATE, 1, 100, "technician", false, 0.0);
        IndustrialFacility farm = new IndustrialFacility("farm", "earth", "industrial_soil_cultivation",
                "corp", IndustrialFacility.PRIVATE_CORPORATE, 1, 100, "farmer", false, 0.0);
        HouseholdAccount household = new HouseholdAccount("earth", "sol", "empire", "human", "farmer",
                100, householdCash, 0.0, 0.0, 0.0, 0.0, Map.of(), 1.0, 1.0, 0.0, 100.0);
        return GameState.builder().empires(List.of(empire)).corporations(List.of(corporation))
                .industrialFacilities(List.of(plant, farm)).householdAccounts(List.of(household))
                .powerGrids(List.of(new PowerGridState("earth", 0.0, 100.0 / 24.0,
                        -100.0 / 24.0, 0.0, 0.0, false))).build();
    }

    private IndustryAccount generation(double kwh) {
        return new IndustryAccount("plant", Map.of(), Map.of(), Map.of(), 0.0, 0.0,
                0.0, 0.0, kwh, 0.0);
    }
}
