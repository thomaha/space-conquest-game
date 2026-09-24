package com.spaceconquest.engine;

import com.spaceconquest.engine.economy.PlanetaryBalanceSheet;
import com.spaceconquest.engine.economy.PlanetaryMunicipalProcessor;
import com.spaceconquest.engine.economy.SystemEconomy;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.industry.PowerGridState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanetaryMunicipalProcessorTest {

    private PlanetaryMunicipalProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new PlanetaryMunicipalProcessor();
    }

    private Planet createPlanet(String id, String name, List<Population> populations) {
        return new Planet(
                id, name, "Planet description", 5.97e24, 9.81, 1.0, 0.0, 12742.0, "TERRESTRIAL",
                "BREATHABLE", true, 0.70, List.of(), List.of(), populations
        );
    }

    private SolarSystem createSolarSystem(String id, String name, List<Planet> planets) {
        return new SolarSystem(
                id, name, "Solar system description", 0, 0, 0, 1.0, 1.0, "Yellow", planets, List.of()
        );
    }

    @Test
    void testMunicipalAccountingRevenuesAndCosts() {
        Population pop = new Population("race_human", Map.of(25, 100_000L));
        Planet planet = createPlanet("planet_sol_3", "Earth", List.of(pop));
        SolarSystem sol = createSolarSystem("sys_sol", "Sol", List.of(planet));

        Empire empire = new Empire(
                "terran_confederation", "Terran Confederation", "race_human", "Democracy",
                50_000.0, 0.15, List.of("sys_sol"), List.of(), Map.of("sys_sol", "gov_1"),
                List.of(), List.of()
        );

        SystemEconomy sysEcon = SystemEconomy.createDefault("sys_sol", "terran_confederation", 100_000L);
        CommercialHub hub = new CommercialHub("hub_earth", "planet_sol_3", 0.05, 500000.0, 0.0, 20.0, Map.of());
        IndustrialFacility corpFacility = new IndustrialFacility("fac_corp_1", "planet_sol_3", "smelting", "corp_mining", "PRIVATE_CORPORATE", 2, 500, "miner", false, 0.0);
        IndustrialFacility publicFacility = new IndustrialFacility("fac_public_1", "planet_sol_3", "research_lab", "terran_confederation", "PUBLIC_STATE", 1, 200, "scientist", false, 0.0);
        PowerGridState grid = new PowerGridState("planet_sol_3", 1000.0, 800.0, 200.0, 5000.0, 5000.0, false);

        GameState state = GameState.builder()
                .turn(1)
                .status("RUNNING")
                .solarSystems(List.of(sol))
                .empires(List.of(empire))
                .commercialHubs(List.of(hub))
                .powerGrids(List.of(grid))
                .industrialFacilities(List.of(corpFacility, publicFacility))
                .systemEconomies(List.of(sysEcon))
                .build();

        PlanetaryMunicipalProcessor.MunicipalTurnResult result = processor.processMunicipalFinances(state);
        assertNotNull(result);
        assertEquals(1, result.balanceSheets().size());

        PlanetaryBalanceSheet sheet = result.balanceSheets().get(0);
        assertEquals("planet_sol_3", sheet.planetId());
        assertEquals("sys_sol", sheet.systemId());
        assertEquals("terran_confederation", sheet.empireId());

        assertTrue(sheet.grossPlanetaryProduct() > 0.0);
        assertTrue(sheet.incomeTaxRevenue() > 0.0);
        assertTrue(sheet.corporateTariffRevenue() > 0.0);
        assertTrue(sheet.dockingFeeRevenue() > 0.0);
        assertTrue(sheet.totalRevenueCredits() > 0.0);

        assertTrue(sheet.workforceSalaries() > 0.0);
        assertTrue(sheet.facilityMaintenanceCosts() > 0.0);
        assertTrue(sheet.publicWelfareExpenditures() > 0.0);
        assertTrue(sheet.infrastructureUpkeepCosts() > 0.0);
        assertTrue(sheet.totalExpenditureCredits() > 0.0);
        assertEquals(sysEcon.totalBudgetCredits(), sheet.publicSectorFundingCredits(), 0.001);
        assertEquals(sheet.workforceSalaries() + sheet.facilityMaintenanceCosts()
                        + sheet.publicWelfareExpenditures() + sheet.infrastructureUpkeepCosts()
                        + sheet.publicSectorFundingCredits(),
                sheet.totalExpenditureCredits(), 0.001);
    }

    @Test
    void testMinistrySynergyBonus() {
        Population pop = new Population("race_human", Map.of(30, 50_000L));
        Planet planet = createPlanet("planet_earth", "Earth", List.of(pop));
        SolarSystem sol = createSolarSystem("sys_sol", "Sol", List.of(planet));

        MinistryAssignment financeMinistry = new MinistryAssignment(
                "ministry_finance_commerce", "bureaucrat", 1.25
        );

        Empire standardEmpire = new Empire(
                "emp_standard", "Standard", "race_human", "Democracy",
                10_000.0, 0.20, List.of("sys_sol"), List.of(), Map.of("sys_sol", "gov_1"),
                List.of(), List.of()
        );

        Empire boostedEmpire = new Empire(
                "emp_boosted", "Boosted", "race_human", "Democracy",
                10_000.0, 0.20, List.of("sys_sol"), List.of(financeMinistry), Map.of("sys_sol", "gov_1"),
                List.of(), List.of()
        );

        CommercialHub hub = new CommercialHub("hub_1", "planet_earth", 0.05, 100000.0, 0.0, 10.0, Map.of());
        IndustrialFacility corpFacility = new IndustrialFacility("fac_1", "planet_earth", "fab", "corp_a", "PRIVATE_CORPORATE", 1, 100, "technician", false, 0.0);

        GameState standardState = GameState.builder()
                .turn(1)
                .status("RUNNING")
                .solarSystems(List.of(sol))
                .empires(List.of(standardEmpire))
                .commercialHubs(List.of(hub))
                .industrialFacilities(List.of(corpFacility))
                .build();

        GameState boostedState = GameState.builder()
                .turn(1)
                .status("RUNNING")
                .solarSystems(List.of(sol))
                .empires(List.of(boostedEmpire))
                .commercialHubs(List.of(hub))
                .industrialFacilities(List.of(corpFacility))
                .build();

        PlanetaryBalanceSheet standardSheet = processor.processMunicipalFinances(standardState).balanceSheets().get(0);
        PlanetaryBalanceSheet boostedSheet = processor.processMunicipalFinances(boostedState).balanceSheets().get(0);

        assertTrue(boostedSheet.corporateTariffRevenue() > standardSheet.corporateTariffRevenue(),
                "Finance ministry synergy should boost corporate tariff revenue");
        assertTrue(boostedSheet.dockingFeeRevenue() > standardSheet.dockingFeeRevenue(),
                "Finance ministry synergy should boost commercial docking fee revenue");
    }

    @Test
    void testSubspaceBankingInstantElectronicTransfer() {
        Population pop = new Population("race_human", Map.of(30, 20_000L));
        Planet planet = createPlanet("planet_alpha", "Alpha", List.of(pop));
        SolarSystem sys = createSolarSystem("sys_alpha", "Alpha Centauri", List.of(planet));

        Empire techEmpire = new Empire(
                "emp_tech", "Tech Empire", "race_human", "Democracy",
                50_000.0, 0.10, List.of("sys_alpha"), List.of(), Map.of("sys_alpha", "gov_1"),
                List.of(PlanetaryMunicipalProcessor.TECH_SUBSPACE_BANKING), List.of()
        );

        GameState state = GameState.builder()
                .turn(1)
                .status("RUNNING")
                .solarSystems(List.of(sys))
                .empires(List.of(techEmpire))
                .build();

        state = state.withSystemEconomies(List.of(SystemEconomy.createDefault(
                "sys_alpha", "emp_tech", 20_000L).withEmpireContributionRate(1.0)));
        PlanetaryMunicipalProcessor.MunicipalTurnResult result = processor.processMunicipalFinances(state);
        PlanetaryBalanceSheet sheet = result.balanceSheets().get(0);

        assertTrue(sheet.netBalanceCredits() > 0.0);
        assertEquals(1000.0, sheet.empireTransferCredits(), 0.001);
        assertEquals(51_000.0, result.updatedEmpires().get(0).treasuryCredits(), 0.001);
        assertTrue(result.dispatchedCouriers().isEmpty(), "No physical courier ships should be dispatched when subspace banking is active");
    }

    @Test
    void testCourierShipDispatchForRequestedContribution() {
        Population pop = new Population("race_human", Map.of(30, 200_000L));
        Planet planet = createPlanet("planet_mining", "Ore Planet", List.of(pop));
        SolarSystem sys = createSolarSystem("sys_frontier", "Frontier", List.of(planet));

        Empire empire = new Empire(
                "emp_frontier", "Frontier Empire", "race_human", "Democracy",
                10_000.0, 0.10, List.of("sys_capital", "sys_frontier"), List.of(), Map.of(),
                List.of(), List.of() // No subspace banking
        );

        PlanetaryBalanceSheet previousSheet = new PlanetaryBalanceSheet(
                "planet_mining", "sys_frontier", "emp_frontier",
                100_000.0, 10_000.0, 0.0, 0.0, 10_000.0,
                500.0, 0.0, 0.0, 50.0, 550.0,
                9450.0, 9500.0, 0.0 // Pre-existing uncollected credits: 9500
        );

        GameState state = GameState.builder()
                .turn(2)
                .status("RUNNING")
                .solarSystems(List.of(sys))
                .empires(List.of(empire))
                .planetaryBalanceSheets(List.of(previousSheet))
                .build();

        state = state.withSystemEconomies(List.of(SystemEconomy.createDefault(
                "sys_frontier", "emp_frontier", 200_000L).withEmpireContributionRate(1.0)));
        PlanetaryMunicipalProcessor.MunicipalTurnResult result = processor.processMunicipalFinances(state);
        PlanetaryBalanceSheet newSheet = result.balanceSheets().get(0);

        assertTrue(newSheet.uncollectedLocalCredits() > 0.0, "Credits outside the transfer target stay local");
        assertEquals(1, result.dispatchedCouriers().size(), "A courier ship should be dispatched");
        CourierShip courier = result.dispatchedCouriers().get(0);
        assertEquals(1000.0, courier.credits(), 0.001);
        assertEquals(1000.0, newSheet.empireTransferCredits(), 0.001);
        assertEquals("sys_frontier", courier.originSystemId());
        assertEquals("sys_capital", courier.destinationSystemId());
    }

    @Test
    void testDeficitCentralTreasurySubsidy() {
        // Outpost with low pop but massive public facility maintenance
        Population pop = new Population("race_human", Map.of(20, 100L));
        Planet planet = createPlanet("planet_frozen", "Frozen Outpost", List.of(pop));
        SolarSystem sys = createSolarSystem("sys_rim", "Outer Rim", List.of(planet));

        Empire empire = new Empire(
                "emp_rich", "Rich Empire", "race_human", "Democracy",
                50_000.0, 0.10, List.of("sys_rim"), List.of(), Map.of(),
                List.of(), List.of()
        );

        IndustrialFacility heavyPublicComplex = new IndustrialFacility("fac_costly", "planet_frozen", "deep_station", "emp_rich", "PUBLIC_STATE", 5, 100, "technician", false, 0.0);

        GameState state = GameState.builder()
                .turn(1)
                .status("RUNNING")
                .solarSystems(List.of(sys))
                .empires(List.of(empire))
                .industrialFacilities(List.of(heavyPublicComplex))
                .build();

        state = state.withSystemEconomies(List.of(SystemEconomy.createDefault(
                "sys_rim", "emp_rich", 100L).withEmpireContributionRate(-1.0)));
        PlanetaryMunicipalProcessor.MunicipalTurnResult result = processor.processMunicipalFinances(state);
        PlanetaryBalanceSheet sheet = result.balanceSheets().get(0);

        assertTrue(sheet.isDeficit(), "Should run a deficit due to heavy facility maintenance");
        assertFalse(sheet.isSelfSufficient());
        assertEquals(1000.0, sheet.centralSubsidyReceivedCredits(), 0.001);
        assertEquals(-1000.0, sheet.empireTransferCredits(), 0.001);
        assertEquals(0.0, sheet.uncollectedLocalCredits());

        Empire updatedEmpire = result.updatedEmpires().get(0);
        assertEquals(50_000.0 - sheet.centralSubsidyReceivedCredits(), updatedEmpire.treasuryCredits(), 0.001);
    }

    @Test
    void testDeficitAndSubsidyCanAccrueDebtAtTheirOwnLevels() {
        Planet planet = createPlanet("outpost", "Outpost",
                List.of(new Population("race_human", Map.of(20, 100L))));
        SolarSystem system = createSolarSystem("frontier", "Frontier", List.of(planet));
        Empire empire = new Empire("empire", "Empire", "race_human", "Democracy",
                50.0, 0.10, List.of("frontier"), List.of(), Map.of(), List.of(), List.of());
        SystemEconomy economy = SystemEconomy.createDefault("frontier", "empire", 100L);
        GameState state = GameState.builder()
                .turn(1)
                .status("RUNNING")
                .solarSystems(List.of(system))
                .build()
                .withEmpires(List.of(empire)).withSystemEconomies(List.of(economy));

        PlanetaryMunicipalProcessor.MunicipalTurnResult neutral = processor.processMunicipalFinances(state);
        assertTrue(neutral.balanceSheets().getFirst().netBalanceCredits() < 0.0);
        assertTrue(neutral.balanceSheets().getFirst().outstandingDebtCredits() > 0.0);
        assertEquals(0.0, neutral.balanceSheets().getFirst().centralSubsidyReceivedCredits(), 0.001);
        assertEquals(50.0, neutral.updatedEmpires().getFirst().treasuryCredits(), 0.001);

        PlanetaryMunicipalProcessor.MunicipalTurnResult subsidized = processor.processMunicipalFinances(
                state.withSystemEconomies(List.of(economy.withEmpireContributionRate(-1.0))));
        PlanetaryBalanceSheet sheet = subsidized.balanceSheets().getFirst();
        assertTrue(sheet.netBalanceCredits() < 0.0);
        assertEquals(1000.0, sheet.centralSubsidyReceivedCredits(), 0.001);
        assertEquals(-1000.0, sheet.empireTransferCredits(), 0.001);
        assertEquals(0.0, subsidized.updatedEmpires().getFirst().treasuryCredits(), 0.001);
        assertEquals(950.0, subsidized.newImperialDebtCredits().get("empire"), 0.001);

        PlanetaryMunicipalProcessor.MunicipalTurnResult contributing = processor.processMunicipalFinances(
                state.withSystemEconomies(List.of(economy.withEmpireContributionRate(1.0))));
        assertEquals(0.0, contributing.balanceSheets().getFirst().empireTransferCredits(), 0.001);
        assertTrue(contributing.dispatchedCouriers().isEmpty());
    }

    @Test
    void testMunicipalDebtCarriesForwardAndSurplusRepaysBeforeContribution() {
        Planet planet = createPlanet("outpost", "Outpost",
                List.of(new Population("race_human", Map.of(20, 100L))));
        SolarSystem system = createSolarSystem("frontier", "Frontier", List.of(planet));
        Empire empire = new Empire("empire", "Empire", "race_human", "Democracy",
                5000.0, 0.10, List.of("frontier"), List.of(), Map.of(),
                List.of(PlanetaryMunicipalProcessor.TECH_SUBSPACE_BANKING), List.of());
        SystemEconomy economy = SystemEconomy.createDefault("frontier", "empire", 100L);
        GameState state = GameState.builder()
                .turn(1)
                .status("RUNNING")
                .solarSystems(List.of(system))
                .build()
                .withEmpires(List.of(empire)).withSystemEconomies(List.of(economy));

        PlanetaryBalanceSheet first = processor.processMunicipalFinances(state).balanceSheets().getFirst();
        PlanetaryBalanceSheet second = processor.processMunicipalFinances(
                state.withPlanetaryBalanceSheets(List.of(first))).balanceSheets().getFirst();
        assertEquals(first.outstandingDebtCredits() * 2.0, second.outstandingDebtCredits(), 0.001);

        SystemEconomy subsidized = economy.withEmpireContributionRate(-1.0);
        PlanetaryBalanceSheet third = processor.processMunicipalFinances(state
                .withSystemEconomies(List.of(subsidized))
                .withPlanetaryBalanceSheets(List.of(second))).balanceSheets().getFirst();
        assertEquals(Math.max(0.0, second.outstandingDebtCredits() - 1000.0 - third.netBalanceCredits()),
                third.outstandingDebtCredits(), 0.001);
        assertEquals(0.0, third.empireTransferCredits() + third.centralSubsidyReceivedCredits(), 0.001);
    }

    @Test
    void testDepopulationDoesNotEraseLocalDebt() {
        Planet empty = createPlanet("outpost", "Outpost", List.of());
        SolarSystem system = createSolarSystem("frontier", "Frontier", List.of(empty));
        PlanetaryBalanceSheet previous = PlanetaryBalanceSheet.createEmpty("outpost", "frontier", "empire")
                .withOutstandingDebt(250.0);
        GameState state = GameState.builder()
                .turn(2)
                .status("RUNNING")
                .solarSystems(List.of(system))
                .build()
                .withPlanetaryBalanceSheets(List.of(previous));

        var result = processor.processMunicipalFinances(state);
        assertEquals(250.0, result.balanceSheets().getFirst().outstandingDebtCredits(), 0.001);
    }

    @Test
    void testHiveMindBypass() {
        Population pop = new Population("race_hive", Map.of(10, 500_000L));
        Planet planet = createPlanet("planet_hive", "Hive Nest", List.of(pop));
        SolarSystem sys = createSolarSystem("sys_hive", "Hive System", List.of(planet));

        Empire hiveEmpire = new Empire(
                "emp_hive", "The Overmind", "race_hive", "Hive Mind",
                0.0, 0.0, List.of("sys_hive"), List.of(), Map.of(),
                List.of(), List.of()
        );

        GameState state = GameState.builder()
                .turn(1)
                .status("RUNNING")
                .solarSystems(List.of(sys))
                .empires(List.of(hiveEmpire))
                .build();

        PlanetaryMunicipalProcessor.MunicipalTurnResult result = processor.processMunicipalFinances(state);
        assertEquals(1, result.balanceSheets().size());
        PlanetaryBalanceSheet sheet = result.balanceSheets().get(0);

        assertEquals(0.0, sheet.totalRevenueCredits());
        assertEquals(0.0, sheet.totalExpenditureCredits());
        assertEquals(0.0, sheet.netBalanceCredits());
        assertEquals(0.0, sheet.uncollectedLocalCredits());
    }
}
