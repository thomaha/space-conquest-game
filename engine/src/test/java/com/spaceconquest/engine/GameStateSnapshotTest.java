package com.spaceconquest.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameStateSnapshotTest {
    @Test
    void nestedGalaxyRecordsKeepTheirCollectionsImmutableAndDetached() {
        Map<Integer, Long> ages = new HashMap<>(Map.of(30, 100L));
        Population population = new Population("human", ages);
        List<Population> moonPopulations = new ArrayList<>(List.of(population));
        Moon moon = new Moon("luna", "Luna", "", 1.0, 1.6, 384_400.0,
                3474.0, "None", false, 0.0, new ArrayList<>(List.of("silicate")), moonPopulations);
        List<Moon> moons = new ArrayList<>(List.of(moon));
        Planet planet = new Planet("earth", "Earth", "", 1.0, 9.81, 1.0, 0.0,
                12_742.0, "TERRESTRIAL", "nitrogen_oxygen", true, 0.7,
                new ArrayList<>(List.of("iron")), moons, List.of(population));
        List<Planet> planets = new ArrayList<>(List.of(planet));
        SolarSystem system = new SolarSystem("sol", "Sol", "", 0.0, 0.0, 0.0,
                1.0, 1.0, "yellow", planets, List.of());
        List<SolarSystem> systems = new ArrayList<>(List.of(system));

        GameState snapshot = GameState.builder().solarSystems(systems).build();

        ages.put(31, 50L);
        moonPopulations.clear();
        moons.clear();
        planets.clear();
        systems.clear();

        SolarSystem copiedSystem = snapshot.solarSystems().getFirst();
        Planet copiedPlanet = copiedSystem.planets().getFirst();
        Moon copiedMoon = copiedPlanet.moons().getFirst();
        assertEquals(Map.of(30, 100L), copiedMoon.populations().getFirst().ageGroups());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.solarSystems().clear());
        assertThrows(UnsupportedOperationException.class, () -> copiedSystem.planets().clear());
        assertThrows(UnsupportedOperationException.class, () -> copiedPlanet.moons().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> copiedMoon.populations().getFirst().ageGroups().put(31, 50L));
    }
}
