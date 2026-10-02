package com.spaceconquest.engine;

import com.spaceconquest.engine.economy.PlanetaryBalanceSheet;
import com.spaceconquest.engine.economy.HouseholdAccount;
import com.spaceconquest.engine.economy.HouseholdEmployment;
import com.spaceconquest.engine.economy.HouseholdWellbeing;
import com.spaceconquest.engine.economy.SystemEconomy;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.StationModule;
import com.spaceconquest.engine.ship.ShipConstructionOrder;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipRole;
import org.junit.jupiter.api.io.TempDir;
import com.spaceconquest.engine.market.CorporateInvestmentProcessor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class SpaceConquestEngineTest {
    @TempDir
    Path tempDir;

    @Test
    public void defaultEngineStartsWithoutAScenario() {
        SpaceConquestEngine engine = new SpaceConquestEngine();
        GameState state = engine.getGameState();
        assertEquals(0, state.turn());
        assertTrue(state.solarSystems().isEmpty());
        assertTrue(state.empires().isEmpty());
        assertTrue(state.corporations().isEmpty());
        assertTrue(state.commercialHubs().isEmpty());
    }

    @Test
    public void activeWarAppliesSavedCivilianAndCorporateTrustPenalties() {
        Empire initiator = new Empire("terran", "Terran", "human", "Individualist",
                1000.0, 0.1, List.of("sol"), List.of(), Map.of(), List.of(), List.of());
        Empire target = new Empire("vulkan", "Vulkan", "vulkan", "Collectivist",
                1000.0, 0.1, List.of("vulkan_sys"), List.of(), Map.of(), List.of(), List.of());
        GameState state = GameState.builder().empires(List.of(initiator, target))
                .diplomaticRelations(List.of(new DiplomaticRelation("terran", "vulkan", "TOTAL_WAR", 0.0)))
                .warDeclarations(List.of(new com.spaceconquest.engine.governance.WarDeclarationRecord(
                        1, "terran", "vulkan", null, false, -0.4, -50.0, "Unprovoked aggression")))
                .build();
        SpaceConquestEngine engine = new SpaceConquestEngine();
        engine.applyGameState(state);

        assertEquals(0.4, engine.activeCivilianWarStress("terran"), 0.0001);
        assertEquals(-50.0, engine.activeCorporateTrustPenalties().get("terran"));
        assertEquals(0.0, engine.activeCivilianWarStress("vulkan"));
    }

    @Test
    public void testNewCampaignHasNoUnprocessedMunicipalDay() {
        GameState opening = SpaceConquestEngine.fromSolScenario().getGameState();
        assertFalse(opening.planetaryBalanceSheets().isEmpty());
        assertTrue(opening.planetaryBalanceSheets().stream().allMatch(sheet ->
                sheet.totalRevenueCredits() == 0.0 && sheet.totalExpenditureCredits() == 0.0
                        && sheet.outstandingDebtCredits() == 0.0));
    }

    @Test
    public void corporateFactoryProjectSurvivesTheLiveTurn() {
        SpaceConquestEngine engine = SpaceConquestEngine.fromSolScenario();
        GameState opening = engine.getGameState();
        List<CommercialHub> starved = opening.commercialHubs().stream().map(hub -> {
            if (!"earth".equals(hub.entityId())) return hub;
            Map<String, MarketOrder> orders = new HashMap<>(hub.activeOrders());
            orders.put("food_matrix", new MarketOrder("food_matrix", 0.0, 0.0, 2.0, 0.0));
            for (String material : List.of("refined_iron", "refined_aluminum", "refined_copper", "silicon")) {
                orders.put(material, new MarketOrder(material, 2_000.0, 0.0, 1.0, 0.0));
            }
            return new CommercialHub(hub.id(), hub.entityId(), hub.transactionTariffRate(),
                    hub.storageCapacityKg(), hub.currentStoredWeightKg(), hub.logisticsRangeUnits(), orders);
        }).toList();
        List<Empire> industrial = opening.empires().stream().map(empire -> {
            if (!"terran_confederation".equals(empire.id())) return empire;
            List<String> technologies = new ArrayList<>(empire.unlockedTechIds());
            technologies.add("industrial_production");
            return new Empire(empire.id(), empire.name(), empire.raceId(), empire.societyStructure(),
                    empire.treasuryCredits(), empire.corporateTaxRate(), empire.controlledSystemIds(),
                    empire.ministries(), empire.systemGovernorAssignments(), technologies,
                    empire.activeShipDesignIds());
        }).toList();
        GameState ready = opening.toBuilder().commercialHubs(starved).empires(industrial).build();
        GameState invested = new CorporateInvestmentProcessor().invest(ready, "corp_bio_harvest",
                "earth", "INFRASTRUCTURE", CorporateInvestmentProcessor.INFRASTRUCTURE_COST);
        assertEquals(ready.industrialFacilities().size() + 1, invested.industrialFacilities().size());
        engine.applyGameState(invested);

        engine.stepTurn();
        GameState after = engine.getGameState();
        var newFactory = after.industrialFacilities().stream()
                .filter(facility -> "corp_bio_harvest".equals(facility.ownerEntityId()))
                .filter(facility -> opening.industrialFacilities().stream()
                        .noneMatch(original -> original.id().equals(facility.id())))
                .findFirst().orElseThrow();
        assertEquals(0, newFactory.tier());
        assertEquals(0.2, newFactory.expansionProgress(), 0.001);
        assertTrue(after.expansionProjects().stream()
                .anyMatch(project -> newFactory.id().equals(project.facilityId())));
    }

    @Test
    public void restoredEnginePaysOrbitalYardWorkersBeforeAdvancingSavedBuild() throws Exception {
        SpaceConquestEngine source = SpaceConquestEngine.fromSolScenario();
        GameState opening = source.getGameState();
        String stationId = "save_resume_yard";
        StationModule control = new StationModule("yard_control", "Station control",
                StationModule.TYPE_CONTROL, 2, 1_000, 0, 100, Map.of(),
                "technician", 0, true);
        StationModule grid = new StationModule("yard_grid", "Shipyard grid",
                StationModule.TYPE_SHIPYARD_GRID, 10, 20_000, 20, 0, Map.of(),
                "industrial_worker", 20, true).withPayroll(17, 425);
        OrbitalStation station = new OrbitalStation(stationId, "Save resume yard", "sol", "earth",
                "terran_confederation", OrbitalStation.OWNERSHIP_PUBLIC_STATE, 30,
                List.of(control, grid), Map.of(), 100, 20, 0, 0, 100, 100,
                "steel", 1, true, List.of(new Population("human", Map.of(25, 200L))));
        CommercialHub stationHub = new CommercialHub("yard_market", stationId, 0,
                1_000_000, 100_000, 10,
                Map.of("steel", new MarketOrder("steel", 100_000, 0, 1, 0)));
        ShipDesign design = new ShipDesign("yard_design", "Yard design", "terran_confederation",
                ShipRole.CARGO_TRANSPORT, "steel", List.of(), "steel", 0,
                10_000, 10_000, 0, 1, 0, 0, true, false);
        ShipConstructionOrder order = new ShipConstructionOrder("yard_order",
                "terran_confederation", design.id(), "sol", stationId,
                100, 500, Map.of("steel", 1_000.0), Map.of("steel", 200.0));
        HouseholdAccount workers = new HouseholdAccount(stationId, "sol", "terran_confederation",
                "human", "industrial_worker", 200, 0, 0, 0, 0, 0, Map.of(), 1, 1, 0, 0,
                HouseholdWellbeing.healthy(), new HouseholdEmployment(200, 0, 0, 0));
        List<Empire> wealthy = opening.empires().stream().map(empire ->
                "terran_confederation".equals(empire.id()) ? wealthyEmpire(empire) : empire).toList();
        GameState ready = opening.toBuilder().empires(wealthy)
                .orbitalStations(List.of(station)).commercialHubs(
                        java.util.stream.Stream.concat(opening.commercialHubs().stream(),
                                java.util.stream.Stream.of(stationHub)).toList())
                .shipDesigns(java.util.stream.Stream.concat(opening.shipDesigns().stream(),
                        java.util.stream.Stream.of(design)).toList())
                .shipConstructionOrders(List.of(order))
                .householdAccounts(java.util.stream.Stream.concat(opening.householdAccounts().stream(),
                        java.util.stream.Stream.of(workers)).toList()).build();

        var start = GameClock.START_TIME;
        var savedTime = start.plusDays(1);
        SaveGameManager manager = new SaveGameManager(tempDir);
        var file = tempDir.resolve("orbital_yard_resume.scsave").toFile();
        manager.save(file, ready, 1, savedTime.toString(), start.toString());
        SpaceConquestEngine restored = new SpaceConquestEngine(manager.load(file));
        restored.stepTurn();

        GameState nextDay = restored.getGameState();
        StationModule paidGrid = nextDay.orbitalStations().getFirst().modules().stream()
                .filter(module -> grid.id().equals(module.id())).findFirst().orElseThrow();
        assertEquals(12, paidGrid.paidWorkers());
        assertEquals(300.0, paidGrid.dailyWageCostsCredits(), 0.001);
        ShipConstructionOrder progressing = nextDay.shipConstructionOrders().getFirst();
        assertTrue(progressing.accumulatedWorkHours() > order.accumulatedWorkHours());
        assertTrue(progressing.consumedMaterialsKg().getOrDefault("steel", 0.0) > 200.0);
    }

    private Empire wealthyEmpire(Empire empire) {
        List<String> technologies = new ArrayList<>(empire.unlockedTechIds());
        technologies.add("automated_assembly_lines");
        technologies.add("cybernetic_workforce_integration");
        return new Empire(empire.id(), empire.name(), empire.raceId(), empire.societyStructure(),
                1_000_000, empire.corporateTaxRate(), empire.controlledSystemIds(),
                empire.ministries(), empire.systemGovernorAssignments(), technologies,
                empire.activeShipDesignIds());
    }

    @Test
    public void testDailyHouseholdTaxesReachTheirLocalBalanceSheets() {
        SpaceConquestEngine engine = SpaceConquestEngine.fromSolScenario();
        engine.stepTurn();
        GameState state = engine.getGameState();

        assertFalse(state.householdAccounts().isEmpty());
        double collected = state.householdAccounts().stream()
                .mapToDouble(HouseholdAccount::incomeTaxPaidCredits).sum();
        assertTrue(collected > 0.0);
        assertEquals(collected, state.planetaryBalanceSheets().stream()
                .mapToDouble(PlanetaryBalanceSheet::incomeTaxRevenue).sum(), 0.001);
        for (PlanetaryBalanceSheet sheet : state.planetaryBalanceSheets()) {
            double paidHere = state.householdAccounts().stream()
                    .filter(account -> sheet.planetId().equals(account.bodyId()))
                    .mapToDouble(HouseholdAccount::incomeTaxPaidCredits).sum();
            assertEquals(paidHere, sheet.incomeTaxRevenue(), 0.001);
        }
    }

    @Test
    public void testClockSchedulesDailyTurnsWithoutRunningEarly() {
        SpaceConquestEngine engine = SpaceConquestEngine.fromSolScenario();
        GameClock clock = engine.getGameClock();
        clock.setSpeed(GameClock.ClockSpeed.SPEED_6_HOURS);
        for (int i = 0; i < 3; i++) {
            assertEquals(0, clock.update(1.0));
            assertEquals(0, engine.getGameState().turn());
        }
        assertEquals(1, clock.update(1.0));
        engine.processScheduledTurn();
        assertEquals(1, engine.getGameState().turn());
        assertEquals(clock.getCurrentTurn(), engine.getGameState().turn());
    }


    @Test
    public void testPopulationAgesOnYearBoundaryRatherThanEveryDailyTurn() {
        SpaceConquestEngine engine = SpaceConquestEngine.fromSolScenario();
        engine.start();
        
        GameState initialState = engine.getGameState();
        SolarSystem sol = initialState.solarSystems().stream().filter(ss -> ss.id().equals("sol")).findFirst().orElseThrow();
        Planet earth = sol.planets().stream().filter(p -> p.id().equals("earth")).findFirst().orElseThrow();
        Population initialPop = earth.populations().getFirst();
        long initialTotal = initialPop.ageGroups().values().stream().mapToLong(Long::longValue).sum();
        
        // Jump to the end of 2200, then cross the annual boundary one day at a time.
        engine.reset(initialState.withTurn(363));
        engine.update();
        Population beforeNewYear = engine.getGameState().solarSystems().stream()
                .filter(ss -> ss.id().equals("sol")).findFirst().orElseThrow()
                .planets().stream().filter(p -> p.id().equals("earth")).findFirst().orElseThrow()
                .populations().getFirst();
        assertEquals(initialPop.ageGroups(), beforeNewYear.ageGroups());

        engine.update();
        
        GameState futureState = engine.getGameState();
        SolarSystem futureSol = futureState.solarSystems().stream().filter(ss -> ss.id().equals("sol")).findFirst().orElseThrow();
        Planet futureEarth = futureSol.planets().stream().filter(p -> p.id().equals("earth")).findFirst().orElseThrow();
        Population futurePop = futureEarth.populations().getFirst();
        long futureTotal = futurePop.ageGroups().values().stream().mapToLong(Long::longValue).sum();
        
        assertTrue(futureTotal > initialTotal, "Population should have grown after one year. Initial: " + initialTotal + ", Future: " + futureTotal);
        assertTrue(futurePop.ageGroups().containsKey(0), "Should have newborns");
        assertTrue(futurePop.ageGroups().containsKey(1), "Existing newborns should have aged by one year");
    }

    @Test
    public void testEconomicAndMarketSimulationTurns() {
        SpaceConquestEngine engine = new SpaceConquestEngine(GameStartScenario.BASIC_WARP);
        engine.start();

        GameState initialState = engine.getGameState();
        assertFalse(initialState.commercialHubs().isEmpty(), "Commercial hubs should exist in BASIC_WARP scenario");
        assertFalse(initialState.corporations().isEmpty(), "Corporations should exist in BASIC_WARP scenario");

        // Seed an active order with shortage in one hub
        CommercialHub firstHub = initialState.commercialHubs().getFirst();
        MarketOrder order = new MarketOrder("refined_silicon", 5.0, 500.0, 10.0, 0.0);
        CommercialHub seededHub = new CommercialHub(
                firstHub.id(),
                firstHub.entityId(),
                firstHub.transactionTariffRate(),
                firstHub.storageCapacityKg(),
                firstHub.currentStoredWeightKg(),
                firstHub.logisticsRangeUnits(),
                Map.of("refined_silicon", order)
        );

        GameState modifiedState = initialState.toBuilder()
                .commercialHubs(List.of(seededHub))
                .build();
        engine.reset(modifiedState);

        // Run 5 turns
        for (int i = 0; i < 5; i++) {
            engine.update();
        }

        GameState advancedState = engine.getGameState();
        assertEquals(5, advancedState.turn());
        CommercialHub updatedHub = advancedState.commercialHubs().getFirst();
        MarketOrder updatedOrder = updatedHub.activeOrders().get("refined_silicon");
        assertNotNull(updatedOrder);
        assertTrue(updatedOrder.pricePerKg() > 10.0, "Shortage should have driven price up");
        assertTrue(updatedOrder.shortcomingScore() > 0.0, "Shortcoming score should be positive");
    }

    @Test
    public void testDailyEconomySettlesSystemContributionFromLocalCredits() {
        SpaceConquestEngine engine = SpaceConquestEngine.fromSolScenario();
        GameState initial = engine.getGameState();
        SystemEconomy solEconomy = initial.systemEconomies().stream()
                .filter(economy -> "sol".equals(economy.systemId())).findFirst().orElseThrow();
        List<SystemEconomy> economies = initial.systemEconomies().stream()
                .map(economy -> "sol".equals(economy.systemId())
                        ? economy.withEmpireContributionRate(1.0) : economy)
                .toList();
        engine.reset(initial.toBuilder().systemEconomies(economies)
                .planetaryBalanceSheets(initial.planetaryBalanceSheets().stream()
                        .map(sheet -> "sol".equals(sheet.systemId())
                                ? sheet.withUncollectedLocalCredits(1_000_000_000_000.0) : sheet)
                        .toList()).build());

        engine.stepTurn();

        GameState settled = engine.getGameState();
        double contribution = settled.planetaryBalanceSheets().stream()
                .filter(sheet -> "sol".equals(sheet.systemId()))
                .mapToDouble(PlanetaryBalanceSheet::empireTransferCredits).sum();
        double funding = settled.planetaryBalanceSheets().stream()
                .filter(sheet -> "sol".equals(sheet.systemId()))
                .mapToDouble(PlanetaryBalanceSheet::publicSectorFundingCredits).sum();
        double publicSalaries = settled.planetaryBalanceSheets().stream()
                .filter(sheet -> "sol".equals(sheet.systemId()))
                .mapToDouble(PlanetaryBalanceSheet::workforceSalaries).sum();
        assertEquals(solEconomy.totalBudgetCredits(), funding + publicSalaries, 0.001);
        assertTrue(contribution > 0.0);
        assertTrue(contribution <= solEconomy.totalBudgetCredits());
        assertTrue(settled.courierShips().stream().anyMatch(courier -> "sol".equals(courier.originSystemId())));
        for (Empire empire : settled.empires()) {
            Empire opening = initial.empires().stream()
                    .filter(candidate -> candidate.id().equals(empire.id())).findFirst().orElseThrow();
            var balance = settled.imperialBalanceSheets().stream()
                    .filter(sheet -> sheet.empireId().equals(empire.id())).findFirst().orElseThrow();
            assertEquals(opening.treasuryCredits() + balance.incomeCredits()
                    - balance.expenditureCredits() - balance.debtRepaidCredits(),
                    empire.treasuryCredits(), 0.001);
        }
    }

    @Test
    public void testGovernanceAndDemocraticElectionSimulationTurns() {
        SpaceConquestEngine engine = new SpaceConquestEngine(GameStartScenario.BASIC_WARP);
        engine.start();

        GameState initialState = engine.getGameState();
        assertFalse(initialState.empires().isEmpty(), "Empires should exist");

        // The fifth annual election falls at the start of 2205, after 1826 daily turns.
        engine.reset(initialState.withTurn(1825));
        engine.update();

        GameState advancedState = engine.getGameState();
        assertEquals(1826, advancedState.turn());
        assertFalse(advancedState.empires().isEmpty());

        Empire terran = advancedState.empires().stream()
                .filter(e -> "Individualist".equalsIgnoreCase(e.societyStructure()))
                .findFirst()
                .orElse(null);

        if (terran != null) {
            assertFalse(terran.ministries().isEmpty(), "Individualist empire should have populated cabinet post-election");
            for (MinistryAssignment assignment : terran.ministries()) {
                assertTrue(assignment.calculatedEfficiencyModifier() >= 1.0, "Efficiency modifier should be computed");
            }
        }
    }

    @Test
    public void testDefaultScenarioEarthAndVulcanBelongToDifferentEmpires() {
        SpaceConquestEngine engine = SpaceConquestEngine.fromSolScenario();
        GameState state = engine.getGameState();

        // Locate Earth and Vulcan systems
        SolarSystem sol = state.solarSystems().stream()
                .filter(ss -> ss.planets().stream().anyMatch(p -> p.id().equals("earth")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Earth system not found"));

        SolarSystem vulcanSystem = state.solarSystems().stream()
                .filter(ss -> ss.planets().stream().anyMatch(p -> p.id().equals("vulcan")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Vulcan system not found"));

        // Find controlling empires
        Empire earthEmpire = state.empires().stream()
                .filter(e -> e.controlledSystemIds().contains(sol.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Controlling empire for Earth system not found"));

        Empire vulcanEmpire = state.empires().stream()
                .filter(e -> e.controlledSystemIds().contains(vulcanSystem.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Controlling empire for Vulcan system not found"));

        assertNotEquals(earthEmpire.id(), vulcanEmpire.id(), "Earth and Vulcan must belong to different empires in the default starting scenario");
        assertEquals("terran_confederation", earthEmpire.id());
        assertEquals("vulkan_forge", vulcanEmpire.id());
        assertEquals("human", earthEmpire.raceId());
        assertEquals("vulkan", vulcanEmpire.raceId());
    }
}
