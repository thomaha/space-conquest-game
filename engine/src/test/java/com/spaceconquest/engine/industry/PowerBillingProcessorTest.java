package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.economy.HouseholdAccount;
import com.spaceconquest.engine.economy.HouseholdEmployment;
import com.spaceconquest.engine.economy.HouseholdWellbeing;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerBillingProcessorTest {
    private final PowerBillingProcessor billing = new PowerBillingProcessor();

    @Test
    void generatorRevenueUsesEachPlantsShareOfLocalProduction() {
        GameState base = state(1_000.0, 10.0);
        IndustrialFacility second = new IndustrialFacility("second_plant", "earth",
                "wind_power", "empire", IndustrialFacility.PUBLIC_STATE, 1, 100,
                "technician", false, 0.0);
        GameState state = base.toBuilder()
                .industrialFacilities(List.of(base.industrialFacilities().getFirst(),
                        base.industrialFacilities().get(1), second))
                .industryAccounts(List.of(base.industryAccounts().getFirst(),
                        IndustryAccount.empty(second.id()))).build();
        var requested = new PowerProcessor().balanceDay(state, Map.of("earth", 100.0), Map.of());
        IndustryAccount quarter = generation(25.0);
        IndustryAccount threeQuarters = new IndustryAccount(second.id(), Map.of(), Map.of(),
                Map.of(), 0, 0, 0, 0, 75.0, 0);
        var result = billing.process(state, requested,
                List.of(quarter, threeQuarters), Map.of(), Map.of("earth", .02));
        assertEquals(2.0, result.households().getFirst().electricitySpendingCredits(), 0.001);
        assertEquals(0.5, result.plantSales().get("plant"), 0.001);
        assertEquals(1.5, result.plantSales().get("second_plant"), 0.001);
    }

    @Test
    void industrialShiftsPayPlantAndStopWhenOwnerCannotAffordElectricity() {
        GameState state = state(1.0, 10.0);
        var requested = new PowerProcessor().balanceDay(state, Map.of("earth", 6_000.0),
                Map.of("farm", 100));
        var result = billing.process(state, requested, List.of(generation(144_000.0)),
                Map.of("farm", 100), Map.of("earth", .02));

        assertEquals(100, result.poweredWorkers().get("farm"));
        assertTrue(result.facilityPowerCosts().isEmpty(), "Typed work is charged on actual production");
        assertEquals(150, result.productionEnergy().get("farm").availableKwh(), 0.001);
        var funded = state.toBuilder().empires(result.empires()).corporations(result.corporations())
                .industryAccounts(List.of(result.industryAccounts().getFirst().withDailyTransactions(generation(144000))))
                .marketAccounts(List.of()).build();
        var production = new IndustryMarketProcessor().process(funded, result.poweredWorkers(), Map.of(), Map.of("farm", 100), result.productionEnergy());
        var account = production.industryAccounts().stream().filter(a -> a.facilityId().equals("farm")).findFirst().orElseThrow();
        assertEquals(1, account.powerCostsCredits(), .001);
        assertEquals(800.0 / 3, account.producedKg().get("food_matrix"), .001);
        assertEquals(0, production.corporations().getFirst().liquidCapitalReserves(), .001);
        assertEquals(1, production.productionPlantSales().get("plant"), .001);
        assertEquals(2.0, result.households().getFirst().electricitySpendingCredits(), 0.001);
        assertEquals(0.0, result.households().getFirst().unmetBasicElectricityKwh(), 0.001);
        assertEquals(8.0, result.households().getFirst().savingsCredits(), 0.001);
        assertEquals(2.0, result.plantSales().get("plant"), 0.001);
        assertEquals(1_000.0, result.empires().getFirst().treasuryCredits(), 0.001);
        assertEquals(1_002.0, result.industryAccounts().getFirst().operatingCashCredits(), 0.001);
        assertTrue(result.imperialReceipts().isEmpty());
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
                List.of("electricity", "industrial_production", "food_processing", "solar_power"), List.of());
        Corporation corporation = new Corporation("corp", "Farm", "empire", "earth",
                "AGRICULTURE", corporateCash, List.of("farm"), List.of(), List.of());
        IndustrialFacility plant = new IndustrialFacility("plant", "earth", "solar_power", "empire",
                IndustrialFacility.PUBLIC_STATE, 1, 100, "technician", false, 0.0);
        IndustrialFacility farm = new IndustrialFacility("farm", "earth", "food_processing",
                "corp", IndustrialFacility.PRIVATE_CORPORATE, 1, 100, "industrial_worker", false, 0.0);
        HouseholdAccount household = new HouseholdAccount("earth", "sol", "empire", "human", "farmer",
                100, householdCash, 0.0, 0.0, 0.0, 0.0, Map.of(), 1.0, 1.0, 0.0, 100.0,
                HouseholdWellbeing.healthy(), HouseholdEmployment.none());
        return GameState.builder().empires(List.of(empire)).corporations(List.of(corporation))
                .commercialHubs(List.of(new com.spaceconquest.engine.CommercialHub("hub", "earth", 0, 10000, 1000, 10,
                        Map.of("harvested_crops", new com.spaceconquest.engine.MarketOrder("harvested_crops", 1000, 0, 0, 0)))))
                .industrialFacilities(List.of(plant, farm)).householdAccounts(List.of(household))
                .industryAccounts(List.of(IndustryAccount.empty("plant").withOperatingCash(1_000.0)))
                .powerGrids(List.of(new PowerGridState("earth", 0.0, 100.0 / 24.0,
                        -100.0 / 24.0, 0.0, 0.0, false))).build();
    }

    private IndustryAccount generation(double kwh) {
        return new IndustryAccount("plant", Map.of(), Map.of(), Map.of(), 0.0, 0.0,
                0.0, 0.0, kwh, 0.0);
    }
}
