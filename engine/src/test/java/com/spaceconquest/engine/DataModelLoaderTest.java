package com.spaceconquest.engine;


import org.junit.jupiter.api.Test;

import java.io.IOException;

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
}
