package com.spaceconquest.engine;

import com.spaceconquest.engine.economy.HouseholdAccount;
import com.spaceconquest.engine.economy.HouseholdEmployment;
import com.spaceconquest.engine.economy.HouseholdWellbeing;
import com.spaceconquest.engine.economy.HouseholdEconomyProcessor;
import com.spaceconquest.engine.economy.PlanetaryBalanceSheet;
import com.spaceconquest.engine.economy.PlanetaryMunicipalProcessor;
import com.spaceconquest.engine.economy.SystemEconomy;
import com.spaceconquest.engine.demographics.ColonyFocus;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.industry.IndustryAccount;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.StationModule;
import com.spaceconquest.engine.ship.ShipyardWorkCapacity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HouseholdEconomyProcessorTest {
    private final HouseholdEconomyProcessor processor = new HouseholdEconomyProcessor();

    @Test
    void oneResidentStillFormsAHousehold() {
        Population population = new Population("human", Map.of(25, 1L));
        assertEquals(1L, population.toDemographics("earth", "sol", ColonyFocus.BALANCED).totalHeadcount());
    }

    @Test
    void wagesTaxesPurchasesAndLocalBooksReconcile() {
        GameState state = state(25);
        var result = processor.process(state, List.of(human()));
        double wages = result.householdAccounts().stream().mapToDouble(HouseholdAccount::wageIncomeCredits).sum();
        double taxes = result.householdAccounts().stream().mapToDouble(HouseholdAccount::incomeTaxPaidCredits).sum();
        double spending = result.householdAccounts().stream().mapToDouble(HouseholdAccount::marketSpendingCredits).sum();
        double savings = result.householdAccounts().stream().mapToDouble(HouseholdAccount::savingsCredits).sum();
        double welfare = result.householdAccounts().stream().mapToDouble(HouseholdAccount::welfareIncomeCredits).sum();

        assertTrue(wages > 0.0);
        assertEquals(wages * 0.10, taxes, 0.001);
        assertEquals(wages + welfare - taxes - spending, savings, 0.001);
        assertEquals(taxes, result.incomeTaxByBody().get("earth"), 0.001);
        assertEquals(spending, result.marketAccounts().getFirst().unsettledSalesCredits(), 0.001);
        double stockSoldAtPostedPrices = state.commercialHubs().getFirst().activeOrders()
                .entrySet().stream().mapToDouble(entry -> (entry.getValue().supplyKg()
                        - result.commercialHubs().getFirst().activeOrders()
                        .get(entry.getKey()).supplyKg()) * entry.getValue().pricePerKg()).sum();
        assertEquals(spending, stockSoldAtPostedPrices, 0.001);
        assertEquals(state.corporations().getFirst().liquidCapitalReserves()
                        - result.corporations().getFirst().liquidCapitalReserves(),
                result.wagesByFacility().values().stream().mapToDouble(Double::doubleValue).sum(), 0.001);
        assertTrue(result.commercialHubs().getFirst().activeOrders().get("food_matrix").supplyKg() < 100.0);
        assertTrue(result.commercialHubs().getFirst().activeOrders().get("food_matrix").supplyKg() >= 0.0);
        assertTrue(result.corporations().getFirst().liquidCapitalReserves() < 1_000.0);
        assertTrue(result.householdAccounts().stream().allMatch(account -> account.unmetBasicKg().isEmpty()));

        var municipal = new PlanetaryMunicipalProcessor().processMunicipalFinances(state, result);
        PlanetaryBalanceSheet sheet = municipal.balanceSheets().getFirst();
        assertEquals(taxes, sheet.incomeTaxRevenue(), 0.001);
        assertEquals(result.publicWagesByBody().get("earth"), sheet.workforceSalaries(), 0.001);
        assertEquals(wages, sheet.grossPlanetaryProduct(), 0.001);
    }

    @Test
    void orbitalResidentsFormHouseholdsAndBuyFromTheirStationHub() {
        GameState initial = state(25);
        StationModule habitation = new StationModule("hab", "Habitation",
                StationModule.TYPE_HABITATION, 6, 12_000, 30, 0, Map.of(),
                "technician", 3, true);
        OrbitalStation station = new OrbitalStation("station", "Orbital home", "sol", "earth",
                "empire", OrbitalStation.OWNERSHIP_PUBLIC_STATE, 40, List.of(habitation),
                Map.of(), 0, 30, 0, 0, 100, 100, "steel", 1, true,
                List.of(new Population("human", Map.of(25, 200L))));
        CommercialHub orbitalHub = new CommercialHub("station_market", station.id(), 0,
                50_000, 2_000, 10, Map.of("food_matrix", new MarketOrder("food_matrix",
                1_000, 0, 1, 0), "oxygen_gas", new MarketOrder("oxygen_gas", 1_000,
                0, 1, 0), "consumer_goods", new MarketOrder("consumer_goods", 1_000,
                0, 2, 0), "luxury_goods", new MarketOrder("luxury_goods", 1_000,
                0, 5, 0)));
        GameState populated = initial.toBuilder().orbitalStations(List.of(station))
                .commercialHubs(List.of(initial.commercialHubs().getFirst(), orbitalHub)).build();

        var result = processor.process(populated, List.of(human()));
        assertEquals(200, result.householdAccounts().stream()
                .filter(account -> "station".equals(account.bodyId()))
                .mapToLong(HouseholdAccount::headcount).sum());
        assertTrue(result.householdAccounts().stream().filter(account ->
                "station".equals(account.bodyId())).mapToDouble(HouseholdAccount::marketSpendingCredits)
                .sum() > 0.0);
        assertTrue(result.marketAccounts().stream().anyMatch(account ->
                "station_market".equals(account.hubId())
                        && account.unsettledSalesCredits() > 0.0));
    }

    @Test
    void orbitalShipyardModulesHireWorkersAndPersistPayrollHeadcount() {
        GameState initial = state(25);
        StationModule grid = new StationModule("yard_grid", "Shipyard grid",
                StationModule.TYPE_SHIPYARD_GRID, 10, 20_000, 100, 0, Map.of(),
                "industrial_worker", 20, true);
        OrbitalStation station = new OrbitalStation("yard_station", "Orbital yard", "sol", "earth",
                "empire", OrbitalStation.OWNERSHIP_PUBLIC_STATE, 30, List.of(grid),
                Map.of(), 100, 100, 0, 0, 100, 100, "steel", 1, true,
                List.of(new Population("human", Map.of(25, 200L))));
        HouseholdAccount workers = new HouseholdAccount("yard_station", "sol", "empire", "human",
                "industrial_worker", 200, 1_000, 0, 0, 0, 0, Map.of(), 1, 1, 0, 0,
                HouseholdWellbeing.healthy(), new HouseholdEmployment(200, 0, 0, 0));
        GameState populated = initial.toBuilder().orbitalStations(List.of(station))
                .householdAccounts(List.of(workers)).build();

        HouseholdEconomyProcessor.TurnResult payroll = processor.process(populated, List.of(human()));
        StationModule paidGrid = payroll.orbitalStations().getFirst().modules().getFirst();
        assertTrue(paidGrid.paidWorkers() > 0);
        assertTrue(paidGrid.dailyWageCostsCredits() > 0.0);
        assertEquals(paidGrid.paidWorkers(), payroll.paidWorkersByFacility().get("yard_grid"));

        GameState settled = populated.toBuilder().orbitalStations(payroll.orbitalStations())
                .industryAccounts(payroll.industryAccounts()).empires(payroll.empires()).build();
        ShipyardWorkCapacity.Profile profile = ShipyardWorkCapacity.forYard(
                settled, "empire", "sol", "yard_station");
        assertEquals(Math.min(100.0, paidGrid.paidWorkers() * 5.0), profile.workPerDay(), 0.001);
    }

    @Test
    void orbitalYardPayrollUsesResearchedStaffingReductions() {
        GameState initial = state(25);
        Empire original = initial.empires().getFirst();
        Empire researched = new Empire(original.id(), original.name(), original.raceId(),
                original.societyStructure(), original.treasuryCredits(), original.corporateTaxRate(),
                original.controlledSystemIds(), original.ministries(),
                original.systemGovernorAssignments(), List.of(
                ShipyardWorkCapacity.TECH_AUTOMATED_ASSEMBLY,
                ShipyardWorkCapacity.TECH_CYBERNETIC_WORKFORCE), original.activeShipDesignIds());
        StationModule grid = new StationModule("researched_grid", "Shipyard grid",
                StationModule.TYPE_SHIPYARD_GRID, 10, 20_000, 100, 0, Map.of(),
                "industrial_worker", 20, true);
        List<StationModule> modules = new java.util.ArrayList<>(List.of(grid));
        for (int index = 0; index < 4; index++) {
            modules.add(new StationModule("assembly_" + index, "Component assembly",
                    StationModule.TYPE_COMPONENT_ASSEMBLY, 1, 1_000, 1, 0, Map.of(),
                    "industrial_worker", 1, true));
        }
        OrbitalStation station = new OrbitalStation("researched_station", "Orbital yard", "sol", "earth",
                "empire", OrbitalStation.OWNERSHIP_PUBLIC_STATE, 30, modules,
                Map.of(), 100, 100, 0, 0, 100, 100, "steel", 1, true,
                List.of(new Population("human", Map.of(25, 200L))));
        HouseholdAccount workers = new HouseholdAccount(station.id(), "sol", "empire", "human",
                "industrial_worker", 200, 0, 0, 0, 0, 0, Map.of(), 1, 1, 0, 0,
                HouseholdWellbeing.healthy(), new HouseholdEmployment(200, 0, 0, 0));
        GameState populated = initial.toBuilder().empires(List.of(researched))
                .orbitalStations(List.of(station)).householdAccounts(List.of(workers)).build();

        var payroll = processor.process(populated, List.of(human()));
        StationModule paidGrid = payroll.orbitalStations().getFirst().modules().getFirst();
        assertEquals(12, paidGrid.paidWorkers());
        assertEquals(300.0, paidGrid.dailyWageCostsCredits(), 0.001);
        assertEquals(16, modules.stream().mapToInt(module -> payroll.paidWorkersByFacility()
                .getOrDefault(module.id(), 0)).sum());
        assertEquals(400.0, modules.stream().mapToDouble(module -> payroll.wagesByFacility()
                .getOrDefault(module.id(), 0.0)).sum(), 0.001);
        GameState settled = populated.toBuilder().orbitalStations(payroll.orbitalStations())
                .empires(payroll.empires()).build();
        ShipyardWorkCapacity.Profile profile = ShipyardWorkCapacity.forYard(
                settled, "empire", "sol", station.id());
        assertEquals(16, profile.staffingByProfession().get("industrial_worker").requiredWorkers());
        assertTrue(profile.isFullyStaffed());
        assertEquals(375.0, profile.workPerDay(), 0.001);
    }

    @Test
    void orbitalShipyardCapacityFallsWhenPayrollFundsOrLocalWorkersAreLimited() {
        GameState initial = state(25);
        StationModule grid = new StationModule("limited_grid", "Shipyard grid",
                StationModule.TYPE_SHIPYARD_GRID, 10, 20_000, 100, 0, Map.of(),
                "industrial_worker", 20, true);
        OrbitalStation station = new OrbitalStation("limited_station", "Orbital yard", "sol", "earth",
                "empire", OrbitalStation.OWNERSHIP_PUBLIC_STATE, 30, List.of(grid),
                Map.of(), 100, 100, 0, 0, 100, 100, "steel", 1, true,
                List.of(new Population("human", Map.of(25, 200L))));
        HouseholdAccount workers = new HouseholdAccount("limited_station", "sol", "empire", "human",
                "industrial_worker", 200, 0, 0, 0, 0, 0, Map.of(), 1, 1, 0, 0,
                HouseholdWellbeing.healthy(), new HouseholdEmployment(200, 0, 0, 0));
        Empire original = initial.empires().getFirst();
        Empire lowFunds = new Empire(original.id(), original.name(), original.raceId(),
                original.societyStructure(), 25, original.corporateTaxRate(),
                original.controlledSystemIds(), original.ministries(),
                original.systemGovernorAssignments(), original.unlockedTechIds(),
                original.activeShipDesignIds());
        GameState underfunded = initial.toBuilder().empires(List.of(lowFunds))
                .orbitalStations(List.of(station)).householdAccounts(List.of(workers)).build();

        var limitedPayroll = processor.process(underfunded, List.of(human()));
        StationModule limitedGrid = limitedPayroll.orbitalStations().getFirst().modules().getFirst();
        assertEquals(1, limitedGrid.paidWorkers());
        assertEquals(25.0, limitedGrid.dailyWageCostsCredits(), 0.001);
        GameState limitedState = underfunded.toBuilder()
                .orbitalStations(limitedPayroll.orbitalStations()).empires(limitedPayroll.empires()).build();
        ShipyardWorkCapacity.Profile limitedCapacity = ShipyardWorkCapacity.forYard(
                limitedState, "empire", "sol", station.id());
        assertEquals(5.0, limitedCapacity.workPerDay(), 0.001);
        assertFalse(limitedCapacity.isFullyStaffed());

        Empire restoredFunds = new Empire(original.id(), original.name(), original.raceId(),
                original.societyStructure(), 1_000, original.corporateTaxRate(),
                original.controlledSystemIds(), original.ministries(),
                original.systemGovernorAssignments(), original.unlockedTechIds(),
                original.activeShipDesignIds());
        GameState recoveredFunding = limitedState.toBuilder().empires(List.of(restoredFunds))
                .householdAccounts(limitedPayroll.householdAccounts()).build();
        var recoveredPayroll = processor.process(recoveredFunding, List.of(human()));
        StationModule recoveredGrid = recoveredPayroll.orbitalStations().getFirst().modules().getFirst();
        assertEquals(20, recoveredGrid.paidWorkers());
        assertEquals(500.0, recoveredGrid.dailyWageCostsCredits(), 0.001);
        GameState recoveredState = recoveredFunding.toBuilder()
                .orbitalStations(recoveredPayroll.orbitalStations())
                .empires(recoveredPayroll.empires()).build();
        assertEquals(100.0, ShipyardWorkCapacity.forYard(
                recoveredState, "empire", "sol", station.id()).workPerDay(), 0.001);

        OrbitalStation smallPopulationStation = new OrbitalStation(station.id(), station.name(),
                station.systemId(), station.planetOrbitId(), station.ownerEntityId(), station.ownershipType(),
                station.totalSlots(), station.modules(), station.storedCargoKg(),
                station.currentPowerGenerationKw(), station.currentPowerDemandKw(),
                station.currentShieldHealth(), station.maxShieldHealth(), station.currentHullHealth(),
                station.maxHullHealth(), station.armorMaterialId(), station.armorThicknessCm(),
                station.isOperational(), List.of(new Population("human", Map.of(25, 2L))));
        HouseholdAccount twoWorkers = new HouseholdAccount("limited_station", "sol", "empire", "human",
                "industrial_worker", 2, 0, 0, 0, 0, 0, Map.of(), 1, 1, 0, 0,
                HouseholdWellbeing.healthy(), new HouseholdEmployment(2, 0, 0, 0));
        GameState shortLabor = initial.toBuilder().orbitalStations(List.of(smallPopulationStation))
                .householdAccounts(List.of(twoWorkers)).build();
        var laborPayroll = processor.process(shortLabor, List.of(human()));
        StationModule laborLimitedGrid = laborPayroll.orbitalStations().getFirst().modules().getFirst();
        assertEquals(2, laborLimitedGrid.paidWorkers());
        assertEquals(50.0, laborLimitedGrid.dailyWageCostsCredits(), 0.001);
        GameState laborLimitedState = shortLabor.toBuilder()
                .orbitalStations(laborPayroll.orbitalStations()).empires(laborPayroll.empires()).build();
        assertEquals(10.0, ShipyardWorkCapacity.forYard(
                laborLimitedState, "empire", "sol", station.id()).workPerDay(), 0.001);
    }

    @Test
    void eachPublicProfessionIsPaidWithinItsSectorAllocation() {
        GameState state = state(25);
        SystemEconomy economy = state.systemEconomies().getFirst();
        var accounts = processor.process(state, List.of(human())).householdAccounts();
        double budget = economy.totalBudgetCredits();

        assertTrue(publicWages(accounts, "teacher", "scientist")
                <= budget * economy.educationAllocation() + 0.001);
        assertTrue(publicWages(accounts, "police")
                <= budget * economy.lawAndOrderAllocation() + 0.001);
        assertTrue(publicWages(accounts, "medic")
                <= budget * economy.healthAndWelfareAllocation() + 0.001);
        assertTrue(publicWages(accounts, "engineer", "technician")
                <= budget * economy.infrastructureAllocation() + 0.001);
        assertTrue(publicWages(accounts, "soldier")
                <= budget * economy.planetaryMilitiasAllocation() + 0.001);
    }

    @Test
    void dailyAccountsRecordFilledJobsAndUnemployment() {
        var accounts = processor.process(state(25), List.of(human())).householdAccounts();
        assertTrue(accounts.stream().anyMatch(account -> account.employment().workingAge() > 0));
        assertTrue(accounts.stream().anyMatch(account -> account.employment().unemployedWorkers() > 0));
        assertTrue(accounts.stream().allMatch(account -> account.employment().workingAge()
                == account.employment().publicWorkers() + account.employment().industryWorkers()
                        + account.employment().unemployedWorkers()));
    }

    @Test
    void stateIndustryPaysItsWorkersFromFacilityBalance() {
        GameState initial = state(25);
        IndustrialFacility farm = new IndustrialFacility("farm", "earth", "farming", "empire",
                IndustrialFacility.PUBLIC_STATE, 1, 10, "farmer", false, 0.0);
        GameState state = initial.toBuilder().industrialFacilities(List.of(farm))
                .industryAccounts(List.of(IndustryAccount.empty("farm").withOperatingCash(100.0)))
                .build();

        var result = processor.process(state, List.of(human()));
        double wages = result.wagesByFacility().get("farm");
        assertTrue(wages > 0.0);
        assertEquals(100.0 - wages, result.industryAccounts().getFirst().operatingCashCredits(), 0.001);
        assertEquals(1_000.0, result.empires().getFirst().treasuryCredits(), 0.001);
        assertEquals(processor.process(initial, List.of(human())).publicWagesByBody().get("earth"),
                result.publicWagesByBody().get("earth"), 0.001);
    }

    private double publicWages(List<HouseholdAccount> accounts, String... professions) {
        return accounts.stream().filter(account -> List.of(professions).contains(account.professionId()))
                .mapToDouble(HouseholdAccount::wageIncomeCredits).sum();
    }

    @Test
    void savingsAndUnsoldStockCarryIntoNextDay() {
        GameState first = state(25);
        var dayOne = processor.process(first, List.of(human()));
        GameState second = first.toBuilder()
                .householdAccounts(dayOne.householdAccounts())
                .marketAccounts(dayOne.marketAccounts())
                .commercialHubs(dayOne.commercialHubs())
                .corporations(dayOne.corporations())
                .build();
        var dayTwo = processor.process(second, List.of(human()));

        assertTrue(dayTwo.marketAccounts().getFirst().unsettledSalesCredits()
                >= dayOne.marketAccounts().getFirst().unsettledSalesCredits());
        assertTrue(dayTwo.commercialHubs().getFirst().activeOrders().get("food_matrix").supplyKg()
                <= dayOne.commercialHubs().getFirst().activeOrders().get("food_matrix").supplyKg());
        assertTrue(dayTwo.commercialHubs().getFirst().activeOrders().values().stream()
                .allMatch(order -> order.supplyKg() >= 0.0));
        assertTrue(dayTwo.householdAccounts().stream().allMatch(account -> account.savingsCredits() >= 0.0));
    }

    @Test
    void childrenWithoutIncomeReceiveBasicWelfareWithoutPayingTax() {
        GameState state = state(10);
        var result = processor.process(state, List.of(human()));

        assertEquals(0.0, result.incomeTaxByBody().get("earth"), 0.001);
        assertTrue(result.householdAccounts().stream().allMatch(account -> account.wageIncomeCredits() == 0.0));
        assertEquals(100.0, result.welfareByBody().get("earth"), 0.001);
        assertEquals(100.0, result.householdAccounts().stream()
                .mapToDouble(HouseholdAccount::marketSpendingCredits).sum(), 0.001);
        assertTrue(result.householdAccounts().stream()
                .allMatch(account -> account.unmetBasicKg().isEmpty()));
        assertEquals(0.0, result.commercialHubs().getFirst().activeOrders().get("food_matrix").supplyKg(), 0.001);
    }

    @Test
    void existingSavingsPayForBasicsBeforeWelfareIsGranted() {
        GameState initial = state(10);
        HouseholdAccount group = processor.process(initial, List.of(human())).householdAccounts().getFirst();
        HouseholdAccount saved = new HouseholdAccount("earth", "sol", "empire", "human", group.professionId(),
                group.headcount(), 200.0, 0.0, 0.0, 0.0, 0.0, Map.of(), 1.0, 1.0,
                0.0, 0.0, HouseholdWellbeing.healthy(), HouseholdEmployment.none());

        var result = processor.process(initial.withHouseholdAccounts(List.of(saved)), List.of(human()));
        HouseholdAccount supported = result.householdAccounts().stream()
                .filter(account -> group.key().equals(account.key())).findFirst().orElseThrow();
        assertEquals(0.0, supported.welfareIncomeCredits(), 0.001);
        assertTrue(supported.unmetBasicKg().isEmpty());
        assertTrue(supported.savingsCredits() < 200.0);
    }

    @Test
    void welfareBeyondHealthAllocationAccumulatesAsLocalDebt() {
        GameState initial = state(10);
        CommercialHub oldHub = initial.commercialHubs().getFirst();
        CommercialHub costlyHub = new CommercialHub(oldHub.id(), oldHub.entityId(),
                oldHub.transactionTariffRate(), oldHub.storageCapacityKg(),
                oldHub.currentStoredWeightKg(), oldHub.logisticsRangeUnits(),
                Map.of("food_matrix", new MarketOrder("food_matrix", 100.0, 100.0, 20.0, 0.0)));
        GameState state = initial.withCommercialHubs(List.of(costlyHub));
        var households = processor.process(state, List.of(human()));
        PlanetaryMunicipalProcessor municipal = new PlanetaryMunicipalProcessor();
        PlanetaryBalanceSheet first = municipal.processMunicipalFinances(state, households)
                .balanceSheets().getFirst();
        double healthBudget = state.systemEconomies().getFirst().totalBudgetCredits()
                * state.systemEconomies().getFirst().healthAndWelfareAllocation();

        assertEquals(2_000.0, households.welfareByBody().get("earth"), 0.001);
        assertEquals(2_000.0, first.publicWelfareExpenditures(), 0.001);
        assertEquals(state.systemEconomies().getFirst().totalBudgetCredits() - healthBudget,
                first.publicSectorFundingCredits(), 0.001);
        assertTrue(first.outstandingDebtCredits() > 0.0);
        PlanetaryBalanceSheet second = municipal.processMunicipalFinances(
                state.toBuilder().planetaryBalanceSheets(List.of(first)).build(), households)
                .balanceSheets().getFirst();
        assertTrue(second.outstandingDebtCredits() > first.outstandingDebtCredits());
    }

    @Test
    void savingsSurviveWhenAGroupTemporarilyDisappears() {
        HouseholdAccount old = new HouseholdAccount("earth", "sol", "empire", "human", "retired",
                100, 75.0, 0.0, 0.0, 0.0, 0.0, Map.of(), 1.0, 1.0, 0.0, 0.0,
                HouseholdWellbeing.healthy(), HouseholdEmployment.none());
        GameState state = state(25).withHouseholdAccounts(List.of(old));

        HouseholdAccount retained = processor.process(state, List.of(human())).householdAccounts().stream()
                .filter(account -> old.key().equals(account.key())).findFirst().orElseThrow();
        assertEquals(0, retained.headcount());
        assertEquals(75.0, retained.savingsCredits(), 0.001);
    }

    @Test
    void speciesBuyTheirOwnBasicNutrients() {
        Race rock = new Race("rock", "Rock", "", 1, 1, "Individualist", 9.81, 288,
                "Silicon", "None", 18, 50, "Rock", "Simple", 85);
        var result = processor.process(state(25, "rock"), List.of(rock));

        assertTrue(result.householdAccounts().stream()
                .anyMatch(account -> account.unmetBasicKg().containsKey("silicates")));
        assertEquals(100.0, result.commercialHubs().getFirst().activeOrders()
                .get("food_matrix").supplyKg(), 0.001);
    }

    @Test
    void breathablePlanetResidentsDoNotBuyAmbientOxygen() {
        var result = processor.process(state(25), List.of(human()));

        assertEquals(50.0, result.commercialHubs().getFirst().activeOrders()
                .get("oxygen_gas").supplyKg(), 0.001);
        assertTrue(result.householdAccounts().stream()
                .noneMatch(account -> account.unmetBasicKg().containsKey("oxygen_gas")));
    }

    @Test
    void airlessPlanetResidentsStillNeedPurchasedOxygen() {
        var result = processor.process(state(25, "human", "none"), List.of(human()));

        assertTrue(result.commercialHubs().getFirst().activeOrders()
                .get("oxygen_gas").supplyKg() < 50.0);
    }

    @Test
    void hostileAndTraceAtmospheresDoNotCountAsBreathable() {
        PopulationProcessor nutrients = new PopulationProcessor();
        assertTrue(nutrients.calculateDailyMarketRequirements(1_000, human(), "nitrogen_oxygen")
                .get("oxygen_gas") == null);
        assertEquals(50.0, nutrients.calculateDailyMarketRequirements(1_000, human(), "dense_co2")
                .get("oxygen_gas"), 0.001);
        assertEquals(50.0, nutrients.calculateDailyMarketRequirements(1_000, human(), "trace_oxygen")
                .get("oxygen_gas"), 0.001);
    }

    @Test
    void pensionsReachRetireesAndAreChargedLocally() {
        GameState state = state(80);
        var result = processor.process(state, List.of(human()));
        double welfare = result.householdAccounts().stream()
                .mapToDouble(HouseholdAccount::welfareIncomeCredits).sum();
        double spending = result.householdAccounts().stream()
                .mapToDouble(HouseholdAccount::marketSpendingCredits).sum();
        double savings = result.householdAccounts().stream()
                .mapToDouble(HouseholdAccount::savingsCredits).sum();

        assertEquals(100.0, welfare, 0.001);
        assertEquals(0.0, result.incomeTaxByBody().get("earth"), 0.001);
        assertEquals(welfare, spending + savings, 0.001);
        PlanetaryBalanceSheet sheet = new PlanetaryMunicipalProcessor()
                .processMunicipalFinances(state, result).balanceSheets().getFirst();
        assertEquals(welfare, sheet.publicWelfareExpenditures(), 0.001);
    }

    @Test
    void industrialHouseholdsReserveCreditsForBasicElectricityBeforeOptionalGoods() {
        GameState initial = state(25);
        Empire original = initial.empires().getFirst();
        Empire industrial = new Empire(original.id(), original.name(), original.raceId(),
                original.societyStructure(), original.treasuryCredits(), original.corporateTaxRate(),
                original.controlledSystemIds(), original.ministries(), original.systemGovernorAssignments(),
                List.of("electricity", "industrial_production"), original.activeShipDesignIds());
        var result = processor.process(initial.toBuilder().empires(List.of(industrial)).build(), List.of(human()));

        double unmetElectricity = result.householdAccounts().stream()
                .mapToDouble(HouseholdAccount::unmetBasicElectricityKwh).sum();
        assertEquals(0.012, unmetElectricity, 0.000001);
        assertTrue(result.householdAccounts().stream().allMatch(account ->
                account.savingsCredits() >= 0.0 && account.electricitySpendingCredits() == 0.0));
        assertEquals(0.0, processor.process(initial, List.of(human())).householdAccounts().stream()
                .mapToDouble(HouseholdAccount::unmetBasicElectricityKwh).sum(), 0.000001);
    }

    private GameState state(int age) {
        return state(age, "human");
    }

    private GameState state(int age, String raceId) {
        return state(age, raceId, "BREATHABLE");
    }

    private GameState state(int age, String raceId, String atmosphere) {
        Population population = new Population(raceId, Map.of(age, 1_000L));
        Planet planet = new Planet("earth", "Earth", "", 1, 9.81, 0, 0, 1,
                "TERRESTRIAL", atmosphere, true, 0.5, List.of(), List.of(), List.of(population));
        SolarSystem system = new SolarSystem("sol", "Sol", "", 0, 0, 0,
                1, 1, "Yellow", List.of(planet), List.of());
        Empire empire = new Empire("empire", "Empire", "human", "Individualist", 1_000.0,
                0.0, List.of("sol"), List.of(), Map.of(), List.of(), List.of());
        Corporation corporation = new Corporation("corp", "Farm", "empire", "earth",
                "AGRICULTURE", 1_000.0, List.of("farm"), List.of(), List.of());
        IndustrialFacility farm = new IndustrialFacility("farm", "earth", "farming", "corp",
                IndustrialFacility.PRIVATE_CORPORATE, 1, 10, "farmer", false, 0.0);
        CommercialHub hub = new CommercialHub("market", "earth", 0, 1_000, 200, 10,
                Map.of("food_matrix", new MarketOrder("food_matrix", 100, 0, 1, 0),
                        "oxygen_gas", new MarketOrder("oxygen_gas", 50, 0, 1, 0),
                        "consumer_goods", new MarketOrder("consumer_goods", 5, 0, 2, 0),
                        "luxury_goods", new MarketOrder("luxury_goods", 1, 0, 5, 0)));
        return GameState.builder().solarSystems(List.of(system)).empires(List.of(empire))
                .corporations(List.of(corporation)).industrialFacilities(List.of(farm))
                .commercialHubs(List.of(hub))
                .systemEconomies(List.of(SystemEconomy.createDefault("sol", "empire", 1_000)))
                .build();
    }

    private Race human() {
        return new Race("human", "Human", "", 1, 1, "Individualist", 9.81, 288,
                "Carbon", "Oxygen", 18, 50, "Organic", "Simple", 85);
    }
}
