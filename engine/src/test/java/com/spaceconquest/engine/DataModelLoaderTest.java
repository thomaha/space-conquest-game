package com.spaceconquest.engine;


import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    public void testLoadMaterials() throws IOException {
        List<Material> materials = DataModelLoader.loadMaterials();
        assertNotNull(materials);
        assertFalse(materials.isEmpty());

        Material ironOre = materials.stream().filter(m -> m.id().equals("iron_ore")).findFirst().orElseThrow();
        assertTrue(ironOre.foundInNature());
        assertEquals(70.0, ironOre.composition().get("Fe"));

        Material refinedIron = materials.stream().filter(m -> m.id().equals("refined_iron")).findFirst().orElseThrow();
        assertFalse(refinedIron.foundInNature());

        Material gold = materials.stream().filter(m -> m.id().equals("gold")).findFirst().orElseThrow();
        assertTrue(gold.foundInNature());

        Material helium3 = materials.stream().filter(m -> m.id().equals("helium_3")).findFirst().orElseThrow();
        assertTrue(helium3.foundInNature());

        Material steel = materials.stream().filter(m -> m.id().equals("steel")).findFirst().orElseThrow();
        assertFalse(steel.foundInNature());
        assertEquals(98.0, steel.composition().get("Fe"));

        Material refinedSilicon = materials.stream().filter(m -> m.id().equals("refined_silicon")).findFirst().orElseThrow();
        assertTrue(refinedSilicon.foundInNature());

        Material refinedNickel = materials.stream().filter(m -> m.id().equals("refined_nickel")).findFirst().orElseThrow();
        assertTrue(refinedNickel.foundInNature());

        Material refinedCarbon = materials.stream().filter(m -> m.id().equals("refined_carbon")).findFirst().orElseThrow();
        assertTrue(refinedCarbon.foundInNature());

        Material refinedSulfur = materials.stream().filter(m -> m.id().equals("refined_sulfur")).findFirst().orElseThrow();
        assertTrue(refinedSulfur.foundInNature());

        Material lithiumOre = materials.stream().filter(m -> m.id().equals("lithium_ore")).findFirst().orElseThrow();
        assertTrue(lithiumOre.foundInNature());

        Material graphene = materials.stream().filter(m -> m.id().equals("graphene")).findFirst().orElseThrow();
        assertFalse(graphene.foundInNature());
        assertEquals(100.0, graphene.composition().get("C"));

        Material hydrogenGas = materials.stream().filter(m -> m.id().equals("hydrogen_gas")).findFirst().orElseThrow();
        assertTrue(hydrogenGas.foundInNature());

        Material fusionFuel = materials.stream().filter(m -> m.id().equals("fusion_fuel_pellets")).findFirst().orElseThrow();
        assertFalse(fusionFuel.foundInNature());
        assertEquals(50.0, fusionFuel.composition().get("He3"));

        Material manganese = materials.stream().filter(m -> m.id().equals("refined_manganese")).findFirst().orElseThrow();
        assertEquals(100.0, manganese.composition().get("Mn"));
        assertFalse(manganese.foundInNature());

        Material deuterium = materials.stream().filter(m -> m.id().equals("deuterium_gas")).findFirst().orElseThrow();
        assertEquals(100.0, deuterium.composition().get("H2"));
    }

    @Test
    public void testLoadTechnologies() throws IOException {
        List<Technology> technologies = DataModelLoader.loadTechnologies();
        assertNotNull(technologies);
        assertFalse(technologies.isEmpty());

        Technology electricity = technologies.stream().filter(t -> t.id().equals("electricity")).findFirst().orElseThrow();
        assertEquals("Electricity", electricity.name());
        assertFalse(electricity.applications().isEmpty());

        TechnicalApplication solar = electricity.applications().stream().filter(a -> a.id().equals("solar_power")).findFirst().orElseThrow();
        assertEquals("Solar power", solar.name());
        assertTrue(solar.affectedFactors().contains("power_production"));

        Technology rocketry = technologies.stream().filter(t -> t.id().equals("rocketry")).findFirst().orElseThrow();
        TechnicalApplication fissionEngine = rocketry.applications().stream().filter(a -> a.id().equals("fission_engines")).findFirst().orElseThrow();
        assertTrue(fissionEngine.requiredTechnologies().contains("fission_reactors"));
    }
}
