package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.economy.CorporateProfitTaxProcessor;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.industry.IndustryAccount;
import com.spaceconquest.engine.industry.IndustryMarketProcessor;
import com.spaceconquest.engine.industry.PowerBillingProcessor;
import com.spaceconquest.engine.industry.PowerGridState;
import com.spaceconquest.engine.industry.PowerProcessor;
import com.spaceconquest.engine.macrostructure.SpaceElevator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LaunchServiceAccountingTest {
    @Test
    void corporateDriverPaysGeneratorAndTaxOnNetLaunchResult() {
        GameState opening = state();
        LaunchService.Plan plan = LaunchService.choose(opening, "earth", "carrier",
                1_000.0, false, false, 0.0, LaunchService.Mode.MASS_DRIVER);
        assertNotNull(plan);
        assertEquals(5.0, plan.powerCostCredits(), 0.001);
        GameState launched = LaunchService.settle(opening, "carrier", plan);
        assertEquals(92.0, corporation(launched, "carrier").liquidCapitalReserves(), 0.001);
        assertEquals(103.0, corporation(launched, "provider").liquidCapitalReserves(), 0.001);

        PowerProcessor.DayResult power = new PowerProcessor().balanceDay(launched,
                Map.of("earth", 1_000.0), Map.of());
        IndustryAccount generator = new IndustryAccount("plant", Map.of(), Map.of(), Map.of(),
                0, 0, 0, 0, 24_000.0, 0);
        var billing = new PowerBillingProcessor().process(launched, power,
                List.of(generator), Map.of());
        assertEquals(5.0, billing.plantSales().get("plant"), 0.001);
        assertEquals(105.0, billing.industryAccounts().stream()
                .filter(account -> "plant".equals(account.facilityId()))
                .findFirst().orElseThrow().operatingCashCredits(), 0.001);
        assertEquals(5.0, billing.launchActivities().getFirst().powerCostCredits(), 0.001);
        GameState settled = launched.toBuilder().corporations(billing.corporations())
                .industryAccounts(billing.industryAccounts())
                .launchActivities(billing.launchActivities()).build();
        var market = new IndustryMarketProcessor().process(settled, Map.of(), Map.of());
        IndustryAccount driver = market.industryAccounts().stream()
                .filter(account -> "driver".equals(account.facilityId()))
                .findFirst().orElseThrow();
        assertEquals(8.0, driver.salesCredits(), 0.001);
        assertEquals(5.0, driver.powerCostsCredits(), 0.001);
        var tax = new CorporateProfitTaxProcessor().process(settled.toBuilder()
                .industryAccounts(market.industryAccounts()).build());
        assertEquals(0.6, tax.accounts().stream()
                .filter(account -> "provider".equals(account.corporationId()))
                .findFirst().orElseThrow().assessedTaxCredits(), 0.001);
    }

    @Test
    void corporateElevatorIncomeAlsoEntersTaxBase() {
        GameState opening = state().toBuilder().spaceElevators(List.of(new SpaceElevator(
                "elevator", "earth", "provider", 2_000.0, 0.95, 100, true))).build();
        LaunchService.Plan plan = LaunchService.choose(opening, "earth", "carrier",
                1_000.0, true, true, 1_000.0, LaunchService.Mode.SPACE_ELEVATOR);
        assertNotNull(plan);
        GameState launched = LaunchService.settle(opening, "carrier", plan);
        var power = new PowerProcessor().balanceDay(launched, Map.of("earth", 1_000.0), Map.of());
        IndustryAccount generator = new IndustryAccount("plant", Map.of(), Map.of(), Map.of(),
                0, 0, 0, 0, 24_000.0, 0);
        var billing = new PowerBillingProcessor().process(launched, power,
                List.of(generator), Map.of());
        GameState settled = launched.toBuilder().corporations(billing.corporations())
                .launchActivities(billing.launchActivities()).build();
        var tax = new CorporateProfitTaxProcessor().process(settled);
        assertEquals((plan.serviceFeeCredits() - plan.powerCostCredits()) * 0.2,
                tax.accounts().stream().filter(account -> "provider".equals(account.corporationId()))
                        .findFirst().orElseThrow().assessedTaxCredits(), 0.001);
    }

    @Test
    void publicDriverKeepsItsFeeAndPaysElectricityFromOperatingCash() {
        GameState opening = state();
        IndustrialFacility driver = opening.industrialFacilities().stream()
                .filter(item -> "driver".equals(item.id())).findFirst().orElseThrow();
        IndustrialFacility publicDriver = new IndustrialFacility(driver.id(), driver.planetId(),
                driver.applicationId(), "emp", IndustrialFacility.PUBLIC_STATE,
                driver.tier(), driver.allocatedWorkers(), driver.workerProfessionId(),
                false, 0);
        opening = opening.toBuilder().industrialFacilities(List.of(
                opening.industrialFacilities().getFirst(), publicDriver))
                .industryAccounts(List.of(IndustryAccount.empty("plant").withOperatingCash(100),
                        IndustryAccount.empty("driver").withOperatingCash(100))).build();
        LaunchService.Plan plan = LaunchService.choose(opening, "earth", "carrier",
                1_000, false, false, 0, LaunchService.Mode.MASS_DRIVER);
        assertNotNull(plan);
        GameState launched = LaunchService.settle(opening, "carrier", plan);
        assertEquals(103.0, launched.industryAccounts().stream()
                .filter(account -> "driver".equals(account.facilityId()))
                .findFirst().orElseThrow().operatingCashCredits(), 0.001);
        assertEquals(1_000.0, launched.empires().getFirst().treasuryCredits(), 0.001);
    }

    @Test
    void failedDailyGenerationLeavesLaunchDemandOnGridAndRefundsUnbilledPower() {
        GameState opening = state();
        LaunchService.Plan plan = LaunchService.choose(opening, "earth", "carrier",
                1_000, false, false, 0, LaunchService.Mode.MASS_DRIVER);
        GameState launched = LaunchService.settle(opening, "carrier", plan);
        var power = new PowerProcessor().balanceDay(launched,
                Map.of("earth", 0.0), Map.of());
        IndustryAccount generator = new IndustryAccount("plant", Map.of(), Map.of(), Map.of(),
                0, 0, 0, 0, 0, 0);
        var billing = new PowerBillingProcessor().process(launched, power,
                List.of(generator), Map.of());
        assertEquals(0.0, billing.launchActivities().getFirst().powerCostCredits(), 0.001);
        assertEquals(108.0, billing.corporations().stream()
                .filter(item -> "provider".equals(item.id()))
                .findFirst().orElseThrow().liquidCapitalReserves(), 0.001);
        assertTrue(billing.grids().getFirst().netBalanceKw() < 0.0);
        assertTrue(billing.grids().getFirst().isDeficitBrownoutActive());
    }

    @Test
    void selfOwnedElevatorDoesNotCreateFeeIncomeButStillPaysForPower() {
        GameState opening = state().toBuilder().spaceElevators(List.of(new SpaceElevator(
                "elevator", "earth", "carrier", 2_000.0, 0.95, 100, true))).build();
        LaunchService.Plan plan = LaunchService.choose(opening, "earth", "carrier",
                1_000, true, true, 1_000, LaunchService.Mode.SPACE_ELEVATOR);
        assertNotNull(plan);
        assertEquals(plan.powerCostCredits(),
                LaunchService.payerOperatingCost(opening, plan, "carrier"), 0.001);
        GameState launched = LaunchService.settle(opening, "carrier", plan);
        assertEquals(100.0 - plan.powerCostCredits(),
                corporation(launched, "carrier").liquidCapitalReserves(), 0.001);
        assertEquals(0.0, launched.launchActivities().getFirst().feeCredits(), 0.001);
    }

    private GameState state() {
        Planet earth = new Planet("earth", "Earth", "", 1, 9.81, 1, 0, 12_742,
                "terrestrial", "breathable", true, 1, List.of(), List.of(),
                List.of(new Population("human", Map.of(30, 1_000L))));
        SolarSystem sol = new SolarSystem("sol", "Sol", "", 0, 0, 0,
                1, 1, "Yellow", List.of(earth), List.of());
        Empire empire = new Empire("emp", "Empire", "human", "Individualist",
                1_000, 0.2, List.of("sol"), List.of(), Map.of(),
                List.of("electricity"), List.of());
        Corporation carrier = new Corporation("carrier", "Carrier", "emp", "earth",
                "TRANSPORT", 100, List.of(), List.of(), List.of());
        Corporation provider = new Corporation("provider", "Provider", "emp", "earth",
                "TRANSPORT", 100, List.of("driver"), List.of(), List.of());
        IndustrialFacility plant = new IndustrialFacility("plant", "earth", "solar_power",
                "emp", IndustrialFacility.PUBLIC_STATE, 1, 100, "technician", false, 0);
        IndustrialFacility driver = new IndustrialFacility("driver", "earth", "mass_driver",
                "provider", IndustrialFacility.PRIVATE_CORPORATE, 1, 10, "technician", false, 0);
        return GameState.builder().solarSystems(List.of(sol)).empires(List.of(empire))
                .corporations(List.of(carrier, provider))
                .industrialFacilities(List.of(plant, driver))
                .industryAccounts(List.of(IndustryAccount.empty("plant").withOperatingCash(100)))
                .commercialHubs(List.of(new CommercialHub("hub", "earth", 0, 10_000,
                        0, 10, Map.of())))
                .powerGrids(List.of(new PowerGridState("earth", 1_000, 0,
                        1_000, 0, 0, false))).build();
    }

    private Corporation corporation(GameState state, String id) {
        return state.corporations().stream().filter(item -> id.equals(item.id()))
                .findFirst().orElseThrow();
    }
}
