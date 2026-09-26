package com.spaceconquest.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GalaxyGeneratorTest {

    private GalaxyGenerator generator;

    @BeforeEach
    public void setUp() throws IOException {
        generator = new GalaxyGenerator();
    }

    @Test
    public void testGameStartScenarioEnum() {
        assertEquals("Contemporary industry", GameStartScenario.PRE_SPACE_FLIGHT.displayName());
        assertEquals("Advanced rocketry", GameStartScenario.ADVANCED_ROCKETRY.displayName());
        assertEquals("Basic warp technology", GameStartScenario.BASIC_WARP.displayName());

        assertEquals(GameStartScenario.PRE_SPACE_FLIGHT, GameStartScenario.fromDisplayName("Contemporary industry"));
        assertEquals(GameStartScenario.PRE_SPACE_FLIGHT,
                GameStartScenario.fromDisplayName(GameStartScenario.PRE_SPACE_FLIGHT.toString()));
        assertEquals(GameStartScenario.ADVANCED_ROCKETRY, GameStartScenario.fromDisplayName("Advanced rocketry"));
        assertEquals(GameStartScenario.BASIC_WARP, GameStartScenario.fromDisplayName("Basic warp technology"));
        assertEquals(GameStartScenario.PRE_SPACE_FLIGHT, GameStartScenario.fromDisplayName(null));

        assertTrue(GameStartScenario.PRE_SPACE_FLIGHT.startingTechnologies().contains("rocketry"));
        assertTrue(GameStartScenario.ADVANCED_ROCKETRY.startingTechnologies().contains("nuclear_fission"));
        assertTrue(GameStartScenario.BASIC_WARP.startingTechnologies().contains("warp"));
        assertEquals(2027, GameStartScenario.PRE_SPACE_FLIGHT.startTime().getYear());
        assertEquals(2050, GameStartScenario.ADVANCED_ROCKETRY.startTime().getYear());
        assertEquals(2200, GameStartScenario.BASIC_WARP.startTime().getYear());
    }

    @Test
    public void testPreSpaceFlightGeneration() {
        List<SolarSystem> systems = generator.generate(5, GameStartScenario.PRE_SPACE_FLIGHT);
        assertEquals(5, systems.size());

        SolarSystem homeSystem = systems.getFirst();
        assertNotNull(homeSystem);

        // Find home planet
        List<Planet> populatedPlanets = homeSystem.planets().stream()
                .filter(p -> !p.populations().isEmpty())
                .toList();
        assertEquals(1, populatedPlanets.size(), "Pre-space flight should have exactly one populated home planet in home system");

        Planet homePlanet = populatedPlanets.getFirst();
        long popCount = homePlanet.populations().getFirst().ageGroups().values().stream().mapToLong(Long::longValue).sum();
        assertTrue(popCount > 5_000_000_000L, "Home planet should have civilization-scale population");

        // All moons in home system should have empty population
        long populatedMoons = homeSystem.planets().stream()
                .flatMap(p -> p.moons().stream())
                .filter(m -> !m.populations().isEmpty())
                .count();
        assertEquals(0, populatedMoons, "Pre-space flight should have no populated moons");

        // Asteroid belts in home system should have empty population
        long populatedBelts = homeSystem.asteroidBelts().stream()
                .filter(ab -> !ab.populations().isEmpty())
                .count();
        assertEquals(0, populatedBelts, "Pre-space flight should have no populated asteroid belts");
    }

    @Test
    public void testAdvancedRocketryGeneration() {
        List<SolarSystem> systems = generator.generate(5, GameStartScenario.ADVANCED_ROCKETRY);
        assertEquals(5, systems.size());

        SolarSystem homeSystem = systems.getFirst();
        assertNotNull(homeSystem);

        // Home system should have colonized friendly planets or mining moons/belts
        long totalPopulatedEntities = homeSystem.planets().stream().filter(p -> !p.populations().isEmpty()).count()
                + homeSystem.planets().stream().flatMap(p -> p.moons().stream()).filter(m -> !m.populations().isEmpty()).count()
                + homeSystem.asteroidBelts().stream().filter(ab -> !ab.populations().isEmpty()).count();

        assertTrue(totalPopulatedEntities >= 1, "Advanced rocketry should have colonies and offworld mining outposts");
    }

    @Test
    public void testBasicWarpGeneration() {
        List<SolarSystem> systems = generator.generate(6, GameStartScenario.BASIC_WARP);
        assertEquals(6, systems.size());

        SolarSystem homeSystem = systems.getFirst();
        assertNotNull(homeSystem);

        // Check if neighboring systems have outpost populations
        long populatedOtherSystems = systems.stream()
                .skip(1)
                .filter(sys -> sys.planets().stream().anyMatch(p -> !p.populations().isEmpty()))
                .count();

        assertTrue(populatedOtherSystems >= 1, "Basic warp technology should have established bases in neighboring systems");
    }

    @Test
    public void testGenerateGameState() {
        GameState state = generator.generateGameState(8, GameStartScenario.BASIC_WARP);
        assertNotNull(state);
        assertEquals(8, state.solarSystems().size());
        assertFalse(state.empires().isEmpty());

        Empire terran = state.empires().getFirst();
        assertEquals("terran_confederation", terran.id());
        assertTrue(terran.unlockedTechIds().contains("warp"));
        assertTrue(terran.controlledSystemIds().size() >= 2, "Basic warp should control home system and neighbor systems");
        assertFalse(state.commercialHubs().isEmpty(), "Should have commercial hubs initialized");
        assertFalse(state.corporations().isEmpty(), "Should have corporations initialized");
        Planet home = state.solarSystems().getFirst().planets().stream()
                .filter(planet -> !planet.populations().isEmpty()).findFirst().orElseThrow();
        assertTrue(PopulationProcessor.hasAmbientBreathableOxygen(home.atmosphere()));
        assertFalse(state.commercialHubs().stream()
                .filter(hub -> home.id().equals(hub.entityId())).findFirst().orElseThrow()
                .activeOrders().containsKey("oxygen_gas"));
    }

    @Test
    public void testGenerateAIEmpires() {
        int numSystems = 20;
        int numAI = 3;
        GameState state = generator.generateGameState(numSystems, numAI, GameStartScenario.PRE_SPACE_FLIGHT);
        
        assertNotNull(state);
        assertEquals(numSystems, state.solarSystems().size());
        // 1 player empire + 3 AI empires = 4 total
        assertEquals(4, state.empires().size());
        
        assertTrue(state.empires().stream().anyMatch(e -> e.id().equals("terran_confederation")));
        assertTrue(state.empires().stream().anyMatch(e -> e.id().equals("ai_empire_1")));
        assertTrue(state.empires().stream().anyMatch(e -> e.id().equals("ai_empire_2")));
        assertTrue(state.empires().stream().anyMatch(e -> e.id().equals("ai_empire_3")));
        
        // Each AI empire should have corporations and hubs
        for (int i = 1; i <= numAI; i++) {
            String aiId = "ai_empire_" + i;
            assertTrue(state.corporations().stream().anyMatch(c -> c.empireId().equals(aiId)));
            
            String aiHomeSystemId = state.empires().stream()
                    .filter(e -> e.id().equals(aiId))
                    .findFirst().orElseThrow()
                    .controlledSystemIds().get(0);
            
            SolarSystem sys = state.solarSystems().stream()
                    .filter(s -> s.id().equals(aiHomeSystemId))
                    .findFirst().orElseThrow();
            
            String homePlanetId = sys.planets().stream()
                    .filter(p -> !p.populations().isEmpty())
                    .findFirst().orElseThrow().id();
            
            assertTrue(state.commercialHubs().stream().anyMatch(h -> h.entityId().equals(homePlanetId)));
        }
    }

    @Test
    public void generatedHomeworldsStartWithIndustryOwnersAndMoney() {
        GameState state = generator.generateGameState(8, 2, GameStartScenario.PRE_SPACE_FLIGHT);
        for (Empire empire : state.empires()) {
            String homeSystemId = empire.controlledSystemIds().getFirst();
            SolarSystem homeSystem = state.solarSystems().stream()
                    .filter(system -> system.id().equals(homeSystemId)).findFirst().orElseThrow();
            Planet home = homeSystem.planets().stream()
                    .filter(planet -> !planet.populations().isEmpty()).findFirst().orElseThrow();
            var facilities = state.industrialFacilities().stream()
                    .filter(facility -> facility.planetId().equals(home.id())).toList();
            assertTrue(facilities.size() >= 7, "Each empire's homeworld should begin with a broad industry mix");
            assertTrue(facilities.stream().anyMatch(facility -> facility.ownerEntityId().equals(empire.id())));
            assertTrue(empire.treasuryCredits() > 0.0);
            if (!empire.societyStructure().toLowerCase().contains("hive")) {
                var corporations = state.corporations().stream()
                        .filter(corporation -> corporation.empireId().equals(empire.id())).toList();
                assertTrue(corporations.stream().anyMatch(corporation -> corporation.liquidCapitalReserves() > 0.0));
                assertTrue(facilities.stream().anyMatch(facility -> corporations.stream().anyMatch(corporation ->
                        corporation.id().equals(facility.ownerEntityId())
                                && corporation.ownedFacilityIds().contains(facility.id()))));
                assertTrue(state.householdAccounts().stream().anyMatch(account -> account.bodyId().equals(home.id())
                        && account.savingsCredits() > 0.0));
            }
            assertTrue(state.commercialHubs().stream().anyMatch(hub -> hub.entityId().equals(home.id())
                    && !hub.activeOrders().isEmpty()));
            assertTrue(state.planetaryBalanceSheets().stream().anyMatch(sheet -> sheet.planetId().equals(home.id())
                    && sheet.outstandingDebtCredits() == 0.0));
        }
    }

    @Test
    public void generatedIndustryTradesOnFirstDay() {
        SpaceConquestEngine engine = new SpaceConquestEngine(GameStartScenario.PRE_SPACE_FLIGHT);
        engine.stepTurn();
        GameState state = engine.getGameState();
        assertTrue(state.industryAccounts().stream().anyMatch(account ->
                !account.producedKg().isEmpty() && account.inputCostsCredits() > 0.0
                        && account.salesCredits() > 0.0));
        assertTrue(state.industryAccounts().stream().allMatch(account ->
                account.salesCredits() >= account.tariffCredits()));
        double plantSales = state.industryAccounts().stream().filter(account -> account.generatedKwh() > 0.0)
                .mapToDouble(account -> account.salesCredits()).sum();
        double industryBills = state.industryAccounts().stream()
                .mapToDouble(account -> account.powerCostsCredits()).sum();
        double householdBills = state.householdAccounts().stream()
                .mapToDouble(account -> account.electricitySpendingCredits()).sum();
        assertTrue(plantSales > 0.0);
        assertTrue(industryBills > 0.0);
        assertTrue(householdBills > 0.0);
        assertEquals(industryBills + householdBills, plantSales, 0.01);
    }

    @Test
    public void startingHomeworldMeetsTrackedNeedsThroughFirstMonth() {
        for (GameStartScenario scenario : GameStartScenario.values()) {
            SpaceConquestEngine engine = new SpaceConquestEngine(scenario);
            GameState opening = engine.getGameState();
            String homeId = opening.solarSystems().getFirst().planets().stream()
                    .filter(planet -> !planet.populations().isEmpty())
                    .max(java.util.Comparator.comparingLong(planet -> planet.populations().stream()
                            .mapToLong(Population::totalCount).sum())).orElseThrow().id();
            long homePopulation = opening.solarSystems().getFirst().planets().stream()
                    .filter(planet -> homeId.equals(planet.id())).findFirst().orElseThrow()
                    .populations().stream().mapToLong(Population::totalCount).sum();
            for (int day = 1; day <= 30; day++) {
                engine.stepTurn();
                GameState state = engine.getGameState();
                var households = state.householdAccounts().stream()
                        .filter(account -> homeId.equals(account.bodyId()) && account.headcount() > 0).toList();
                assertFalse(households.isEmpty(), scenario + " day " + day);
                for (var household : households) {
                    String context = scenario + " day " + day + " household " + household.key();
                    assertTrue(household.unmetBasicKg().isEmpty(), context + " lacks basic goods");
                    assertEquals(0.0, household.unmetBasicElectricityKwh(), 0.001, context + " lacks power");
                    assertEquals(1.0, household.secondaryNeedsMetFraction(), 0.001, context + " lacks secondary goods");
                    assertEquals(1.0, household.luxuryNeedsMetFraction(), 0.001, context + " lacks luxury goods");
                }
                var homeSheets = state.planetaryBalanceSheets().stream()
                        .filter(sheet -> homeId.equals(sheet.planetId())).toList();
                assertTrue(homeSheets.stream().allMatch(sheet -> sheet.outstandingDebtCredits() < 0.01),
                        scenario + " day " + day + " starts municipal debt: " + homeSheets);
                double foodProduced = state.industryAccounts().stream()
                        .filter(account -> state.industrialFacilities().stream().anyMatch(facility ->
                                facility.id().equals(account.facilityId()) && homeId.equals(facility.planetId())))
                        .mapToDouble(account -> account.producedKg().getOrDefault("food_matrix", 0.0)).sum();
                assertTrue(foodProduced >= homePopulation * 0.1,
                        scenario + " day " + day + " relies on opening food stock");
                double consumerGoodsProduced = state.industryAccounts().stream()
                        .filter(account -> state.industrialFacilities().stream().anyMatch(facility ->
                                facility.id().equals(account.facilityId()) && homeId.equals(facility.planetId())))
                        .mapToDouble(account -> account.producedKg().getOrDefault("consumer_goods", 0.0)).sum();
                assertTrue(consumerGoodsProduced >= homePopulation * 0.005,
                        scenario + " day " + day + " relies on opening consumer goods");
            }
        }
    }

    @Test
    public void nonHumanHomeworldsMeetTrackedNeedsThroughFirstMonth() throws IOException {
        List<Race> raceCatalog = DataModelLoader.loadRaces();
        PopulationProcessor nutrients = new PopulationProcessor();
        for (GameStartScenario scenario : GameStartScenario.values()) {
            SpaceConquestEngine engine = new SpaceConquestEngine(scenario);
            engine.applyGameState(generator.generateGameState(10, 4, scenario));
            GameState opening = engine.getGameState();
            var homeByEmpire = new java.util.HashMap<String, String>();
            var claimedSystems = new java.util.HashSet<String>();
            for (Empire empire : opening.empires()) {
                for (String controlledId : empire.controlledSystemIds()) {
                    assertTrue(claimedSystems.add(controlledId),
                            scenario + " gives more than one empire control of " + controlledId);
                }
                String systemId = empire.controlledSystemIds().getFirst();
                SolarSystem system = opening.solarSystems().stream()
                        .filter(candidate -> systemId.equals(candidate.id())).findFirst().orElseThrow();
                Planet home = system.planets().stream().filter(planet -> !planet.populations().isEmpty())
                        .max(java.util.Comparator.comparingLong(planet -> planet.populations().stream()
                                .mapToLong(Population::totalCount).sum())).orElseThrow();
                homeByEmpire.put(empire.id(), home.id());
                assertTrue(opening.industrialFacilities().stream()
                        .anyMatch(facility -> home.id().equals(facility.planetId())));
                CommercialHub hub = opening.commercialHubs().stream()
                        .filter(candidate -> home.id().equals(candidate.entityId()))
                        .findFirst().orElseThrow();
                for (Population group : home.populations()) {
                    Race race = raceCatalog.stream().filter(candidate -> candidate.id().equals(group.raceId()))
                            .findFirst().orElseThrow();
                    nutrients.calculateDailyMarketRequirements(group.totalCount(), race, home.atmosphere())
                            .forEach((resource, perDay) -> assertTrue(
                                    hub.activeOrders().get(resource).supplyKg() >= perDay * 30.0,
                                    scenario + " " + empire.id() + " lacks opening " + resource));
                }
            }
            for (int day = 1; day <= 30; day++) {
                engine.stepTurn();
                GameState state = engine.getGameState();
                for (var account : state.householdAccounts()) {
                    if (!account.bodyId().equals(homeByEmpire.get(account.empireId()))
                            || account.headcount() == 0) continue;
                    String context = scenario + " " + account.empireId() + " day " + day + " " + account.key();
                    assertTrue(account.unmetBasicKg().isEmpty(), context + " lacks basic goods");
                    assertEquals(0.0, account.unmetBasicElectricityKwh(), 0.001, context + " lacks power");
                    assertEquals(1.0, account.secondaryNeedsMetFraction(), 0.001, context + " lacks secondary goods");
                    assertEquals(1.0, account.luxuryNeedsMetFraction(), 0.001, context + " lacks luxury goods");
                }
                for (Empire empire : state.empires()) {
                    String homeId = homeByEmpire.get(empire.id());
                    var homeSheets = state.planetaryBalanceSheets().stream()
                            .filter(sheet -> homeId.equals(sheet.planetId())).toList();
                    assertTrue(homeSheets.stream().allMatch(sheet -> sheet.outstandingDebtCredits() < 0.01),
                            scenario + " " + empire.id() + " day " + day + " starts municipal debt: " + homeSheets);
                }
            }
        }
    }

    @Test
    public void testEngineScenarioInitialization() {
        SpaceConquestEngine engine = new SpaceConquestEngine(GameStartScenario.ADVANCED_ROCKETRY);
        engine.start();

        GameState state = engine.getGameState();
        assertNotNull(state);
        assertEquals(10, state.solarSystems().size());

        Empire empire = state.empires().getFirst();
        assertTrue(empire.unlockedTechIds().contains("nuclear_fission"));
        assertTrue(empire.unlockedTechIds().contains("supply_chain_automation"));

        // Advance turns
        engine.update();
        assertEquals(1, engine.getGameState().turn());
        engine.stop();
    }
}
