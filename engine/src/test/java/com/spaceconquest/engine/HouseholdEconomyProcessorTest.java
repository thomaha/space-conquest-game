package com.spaceconquest.engine;

import com.spaceconquest.engine.economy.HouseholdAccount;
import com.spaceconquest.engine.economy.HouseholdEconomyProcessor;
import com.spaceconquest.engine.economy.PlanetaryBalanceSheet;
import com.spaceconquest.engine.economy.PlanetaryMunicipalProcessor;
import com.spaceconquest.engine.economy.SystemEconomy;
import com.spaceconquest.engine.demographics.ColonyFocus;
import com.spaceconquest.engine.industry.IndustrialFacility;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        assertTrue(wages > 0.0);
        assertEquals(wages * 0.10, taxes, 0.001);
        assertEquals(wages - taxes - spending, savings, 0.001);
        assertEquals(taxes, result.incomeTaxByBody().get("earth"), 0.001);
        assertEquals(spending, result.marketAccounts().getFirst().unsettledSalesCredits(), 0.001);
        assertTrue(result.commercialHubs().getFirst().activeOrders().get("food_matrix").supplyKg() < 100.0);
        assertTrue(result.commercialHubs().getFirst().activeOrders().get("food_matrix").supplyKg() >= 0.0);
        assertTrue(result.corporations().getFirst().liquidCapitalReserves() < 1_000.0);
        assertTrue(result.householdAccounts().stream().anyMatch(account -> !account.unmetBasicKg().isEmpty()));

        var municipal = new PlanetaryMunicipalProcessor().processMunicipalFinances(state, result);
        PlanetaryBalanceSheet sheet = municipal.balanceSheets().getFirst();
        assertEquals(taxes, sheet.incomeTaxRevenue(), 0.001);
        assertEquals(result.publicWagesByBody().get("earth"), sheet.workforceSalaries(), 0.001);
        assertEquals(wages, sheet.grossPlanetaryProduct(), 0.001);
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
    void childrenWithoutIncomeCannotPurchaseGoodsOrPayTax() {
        GameState state = state(10);
        var result = processor.process(state, List.of(human()));

        assertEquals(0.0, result.incomeTaxByBody().get("earth"), 0.001);
        assertTrue(result.householdAccounts().stream().allMatch(account -> account.wageIncomeCredits() == 0.0));
        assertTrue(result.householdAccounts().stream().allMatch(account -> account.marketSpendingCredits() == 0.0));
        assertTrue(result.householdAccounts().stream().allMatch(account -> !account.unmetBasicKg().isEmpty()));
        assertEquals(100.0, result.commercialHubs().getFirst().activeOrders().get("food_matrix").supplyKg(), 0.001);
        assertTrue(result.marketAccounts().isEmpty());
    }

    @Test
    void savingsSurviveWhenAGroupTemporarilyDisappears() {
        HouseholdAccount old = new HouseholdAccount("earth", "sol", "empire", "human", "retired",
                100, 75.0, 0.0, 0.0, 0.0, 0.0, Map.of(), 1.0, 1.0, 0.0, 0.0);
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

        assertEquals(50.0, welfare, 0.001);
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
