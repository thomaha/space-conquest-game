package com.spaceconquest.engine;


import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DataModelLoaderTest {

    @Test
    public void testLoadSolarSystem() throws IOException {
        SolarSystem sol = DataModelLoader.loadSolarSystem("sol");
        
        assertNotNull(sol);
        assertEquals("sol", sol.id());
        assertEquals("Sol", sol.name());
        assertEquals(1.989e30, sol.sunMass());
        assertEquals(1392700.0, sol.sunDiameter());
        
        assertEquals(9, sol.planets().size());
        Planet earth = sol.planets().stream().filter(p -> p.id().equals("earth")).findFirst().orElseThrow();
        assertEquals("Earth", earth.name());
        assertEquals(1.496e8, earth.distance());
        assertEquals(0.0, earth.inclination());
        assertEquals(12742.0, earth.diameter());
        
        assertEquals(1, earth.populations().size());
        Population humanPop = earth.populations().getFirst();
        assertEquals("human", humanPop.raceId());
        assertEquals(1000000000L, humanPop.ageGroups().get(0));
        assertEquals(2000000000L, humanPop.ageGroups().get(20));

        assertEquals(1, earth.moons().size());
        Moon moon = earth.moons().getFirst();
        assertEquals("Moon", moon.name());
        assertEquals(3.844e5, moon.distance());
        assertEquals(3474.0, moon.diameter());

        Planet pluto = sol.planets().stream().filter(p -> p.id().equals("pluto")).findFirst().orElseThrow();
        assertEquals("Pluto", pluto.name());
        assertEquals(17.1, pluto.inclination());
        assertEquals("planetoid", pluto.type());
        assertEquals(1, pluto.moons().size());
        
        assertEquals(2, sol.asteroidBelts().size());
        assertEquals("Main Asteroid Belt", sol.asteroidBelts().getFirst().name());
    }

    @Test
    public void testLoadRaces() throws IOException {
        List<Race> races = DataModelLoader.loadRaces();
        assertNotNull(races);
        assertEquals(2, races.size());

        Race human = races.stream().filter(r -> r.id().equals("human")).findFirst().orElseThrow();
        assertEquals("Human", human.name());
        assertEquals(1.0, human.intelligence());
        assertEquals(1.0, human.physicalStrength());
        assertEquals("Individualist", human.societyStructure());
        assertEquals(1.0, human.preferredGForce());
        assertEquals(288.0, human.preferredTemperature());
        assertEquals("Carbon based", human.chemicalComposition());
        assertEquals("Oxygen based", human.breathingAtmosphere());
        assertEquals(15, human.fertileAgeStart());
        assertEquals(45, human.fertileAgeEnd());
        assertEquals("Organic", human.nutrientType());
        assertEquals("Diverse", human.nutrientSpreadRequirement());
        assertEquals(65, human.retirementAge());

        Race vulkan = races.stream().filter(r -> r.id().equals("vulkan")).findFirst().orElseThrow();
        assertEquals("Vulkan", vulkan.name());
        assertEquals(1.5, vulkan.intelligence());
        assertEquals(1.2, vulkan.physicalStrength());
        assertEquals(200, vulkan.retirementAge());
    }
}
