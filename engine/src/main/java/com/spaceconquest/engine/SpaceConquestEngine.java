package com.spaceconquest.engine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.concurrent.atomic.AtomicBoolean;
public class SpaceConquestEngine implements GameEngine {
    private static final Logger logger = LogManager.getLogger(SpaceConquestEngine.class);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private long turn = 0;
    private java.util.List<SolarSystem> solarSystems = new java.util.ArrayList<>();
    private java.util.List<Race> races = new java.util.ArrayList<>();
    private final PopulationProcessor populationProcessor = new PopulationProcessor();

    public SpaceConquestEngine() {
        try {
            solarSystems = DataModelLoader.loadSolarSystems();
            races = DataModelLoader.loadRaces();
        } catch (java.io.IOException e) {
            logger.error("Failed to load data", e);
        }
    }

    @Override
    public void start() {
        running.set(true);
        logger.info("Game Engine Started.");
    }

    @Override
    public void stop() {
        running.set(false);
        logger.info("Game Engine Stopped.");
    }

    @Override
    public void update() {
        if (running.get()) {
            turn++;
            logger.info("Processing turn: {}", turn);
            updatePopulations();
        }
    }

    private void updatePopulations() {
        // Assume 1 turn = 1 year for simplicity in this mock
        solarSystems = solarSystems.stream()
            .map(ss -> new SolarSystem(
                ss.id(), ss.name(), ss.description(), ss.sunMass(), ss.sunDiameter(),
                ss.planets().stream().map(this::updatePlanet).toList(),
                ss.asteroidBelts().stream().map(this::updateAsteroidBelt).toList()
            ))
            .toList();
    }

    private Planet updatePlanet(Planet p) {
        return new Planet(
            p.id(), p.name(), p.description(), p.mass(), p.gravity(), p.distance(), 
            p.inclination(), p.diameter(), p.type(), p.resources(),
            p.moons().stream().map(this::updateMoon).toList(),
            p.populations().stream().map(this::updatePopulation).toList()
        );
    }

    private Moon updateMoon(Moon m) {
        return new Moon(
            m.id(), m.name(), m.description(), m.mass(), m.gravity(), m.distance(),
            m.diameter(), m.resources(),
            m.populations().stream().map(this::updatePopulation).toList()
        );
    }

    private AsteroidBelt updateAsteroidBelt(AsteroidBelt ab) {
        return new AsteroidBelt(
            ab.id(), ab.name(), ab.description(), ab.resources(),
            ab.populations().stream().map(this::updatePopulation).toList()
        );
    }

    private Population updatePopulation(Population pop) {
        Race race = races.stream()
            .filter(r -> r.id().equals(pop.raceId()))
            .findFirst()
            .orElse(null);
        if (race == null) return pop;
        return populationProcessor.advanceYears(pop, race, 1);
    }

    @Override
    public GameState getGameState() {
        return new GameState(turn, running.get() ? "RUNNING" : "STOPPED", solarSystems);
    }
}
