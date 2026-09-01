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
        assertEquals("Pre space flight", GameStartScenario.PRE_SPACE_FLIGHT.displayName());
        assertEquals("Advanced rocketry", GameStartScenario.ADVANCED_ROCKETRY.displayName());
        assertEquals("Basic warp technology", GameStartScenario.BASIC_WARP.displayName());

        assertEquals(GameStartScenario.PRE_SPACE_FLIGHT, GameStartScenario.fromDisplayName("Pre space flight"));
        assertEquals(GameStartScenario.ADVANCED_ROCKETRY, GameStartScenario.fromDisplayName("Advanced rocketry"));
        assertEquals(GameStartScenario.BASIC_WARP, GameStartScenario.fromDisplayName("Basic warp technology"));
        assertEquals(GameStartScenario.PRE_SPACE_FLIGHT, GameStartScenario.fromDisplayName(null));

        assertTrue(GameStartScenario.PRE_SPACE_FLIGHT.startingTechnologies().contains("rocketry"));
        assertTrue(GameStartScenario.ADVANCED_ROCKETRY.startingTechnologies().contains("nuclear_fission"));
        assertTrue(GameStartScenario.BASIC_WARP.startingTechnologies().contains("warp"));
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
    public void testEngineScenarioInitialization() {
        SpaceConquestEngine engine = new SpaceConquestEngine(GameStartScenario.ADVANCED_ROCKETRY);
        engine.start();

        GameState state = engine.getGameState();
        assertNotNull(state);
        assertEquals(10, state.solarSystems().size());

        Empire empire = state.empires().getFirst();
        assertTrue(empire.unlockedTechIds().contains("nuclear_fission"));
        assertTrue(empire.unlockedTechIds().contains("deep_core_drilling"));

        // Advance turns
        engine.update();
        assertEquals(1, engine.getGameState().turn());
        engine.stop();
    }
}
