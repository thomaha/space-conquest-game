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
        assertTrue(earth.hasLiquidWater());
        assertEquals(0.71, earth.waterLevel(), 0.001);
        
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
        assertFalse(moon.hasLiquidWater());
        assertEquals(0.0, moon.waterLevel());

        Planet pluto = sol.planets().stream().filter(p -> p.id().equals("pluto")).findFirst().orElseThrow();
        assertEquals("Pluto", pluto.name());
        assertEquals(17.1, pluto.inclination());
        assertEquals("dwarf_planet", pluto.type());
        assertEquals(1, pluto.moons().size());
        
        assertEquals(2, sol.asteroidBelts().size());
        assertEquals("Main Asteroid Belt", sol.asteroidBelts().getFirst().name());
    }

    @Test
    public void testLoadRaces() throws IOException {
        List<Race> races = DataModelLoader.loadRaces();
        assertNotNull(races);
        assertEquals(3, races.size());

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

        Race silicon = races.stream().filter(r -> r.id().equals("silicon_core")).findFirst().orElseThrow();
        assertEquals("Silicon core", silicon.name());
        assertEquals(0.5, silicon.intelligence());
        assertEquals(1.8, silicon.physicalStrength());
        assertEquals("Collectivist", silicon.societyStructure());
        assertEquals(700, silicon.retirementAge());
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

        Material silicon = materials.stream().filter(m -> m.id().equals("silicon")).findFirst().orElseThrow();
        assertTrue(silicon.foundInNature());
        assertEquals("Silicon", silicon.name());
        assertEquals(100.0, silicon.composition().get("Si"));

        Material nickel = materials.stream().filter(m -> m.id().equals("nickel")).findFirst().orElseThrow();
        assertTrue(nickel.foundInNature());
        assertEquals("Nickel", nickel.name());
        assertEquals(100.0, nickel.composition().get("Ni"));

        Material carbon = materials.stream().filter(m -> m.id().equals("carbon")).findFirst().orElseThrow();
        assertTrue(carbon.foundInNature());
        assertEquals("Carbon (Graphite)", carbon.name());
        assertEquals(100.0, carbon.composition().get("C"));

        Material sulfur = materials.stream().filter(m -> m.id().equals("sulfur")).findFirst().orElseThrow();
        assertTrue(sulfur.foundInNature());
        assertEquals("Sulfur", sulfur.name());
        assertEquals(100.0, sulfur.composition().get("S"));

        Material iridium = materials.stream().filter(m -> m.id().equals("iridium")).findFirst().orElseThrow();
        assertTrue(iridium.foundInNature());
        assertEquals("Iridium", iridium.name());
        assertEquals(100.0, iridium.composition().get("Ir"));

        for (Material m : materials) {
            if (m.foundInNature()) {
                assertFalse(m.id().startsWith("refined_"), "Natural material ID should not start with refined_: " + m.id());
                assertFalse(m.name().startsWith("Refined "), "Natural material name should not start with Refined : " + m.name());
            }
        }

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

        Material silicates = materials.stream().filter(m -> m.id().equals("silicates")).findFirst().orElseThrow();
        assertTrue(silicates.foundInNature());
        assertEquals(50.0, silicates.composition().get("Si"));

        Material nitrates = materials.stream().filter(m -> m.id().equals("nitrates")).findFirst().orElseThrow();
        assertTrue(nitrates.foundInNature());
        assertEquals(16.0, nitrates.composition().get("N"));

        Material phosphates = materials.stream().filter(m -> m.id().equals("phosphates")).findFirst().orElseThrow();
        assertTrue(phosphates.foundInNature());
        assertEquals(18.0, phosphates.composition().get("P"));

        Material potash = materials.stream().filter(m -> m.id().equals("potash")).findFirst().orElseThrow();
        assertTrue(potash.foundInNature());
        assertEquals(52.0, potash.composition().get("K"));

        Material limestone = materials.stream().filter(m -> m.id().equals("limestone")).findFirst().orElseThrow();
        assertTrue(limestone.foundInNature());
        assertEquals(40.0, limestone.composition().get("Ca"));

        Material pgm = materials.stream().filter(m -> m.id().equals("platinum_group_metals")).findFirst().orElseThrow();
        assertTrue(pgm.foundInNature());
        assertEquals(30.0, pgm.composition().get("Pt"));

        Material hydrocarbons = materials.stream().filter(m -> m.id().equals("hydrocarbons")).findFirst().orElseThrow();
        assertTrue(hydrocarbons.foundInNature());
        assertEquals(85.0, hydrocarbons.composition().get("C"));

        Material heavySands = materials.stream().filter(m -> m.id().equals("heavy_sands")).findFirst().orElseThrow();
        assertTrue(heavySands.foundInNature());
        assertEquals(35.0, heavySands.composition().get("Ti"));

        Material chromiumOre = materials.stream().filter(m -> m.id().equals("chromium_ore")).findFirst().orElseThrow();
        assertTrue(chromiumOre.foundInNature());
        assertEquals(46.0, chromiumOre.composition().get("Cr"));

        Material refinedGallium = materials.stream().filter(m -> m.id().equals("refined_gallium")).findFirst().orElseThrow();
        assertFalse(refinedGallium.foundInNature());
        assertEquals(100.0, refinedGallium.composition().get("Ga"));

        Material ceramics = materials.stream().filter(m -> m.id().equals("industrial_ceramics")).findFirst().orElseThrow();
        assertFalse(ceramics.foundInNature());
        assertEquals(35.0, ceramics.composition().get("Al"));

        Material nuclearGraphite = materials.stream().filter(m -> m.id().equals("isotopic_graphite")).findFirst().orElseThrow();
        assertFalse(nuclearGraphite.foundInNature());
        assertEquals(100.0, nuclearGraphite.composition().get("C"));

        Material sodiumCoolant = materials.stream().filter(m -> m.id().equals("liquid_sodium")).findFirst().orElseThrow();
        assertFalse(sodiumCoolant.foundInNature());
        assertEquals(100.0, sodiumCoolant.composition().get("Na"));

        Material cuprates = materials.stream().filter(m -> m.id().equals("superconducting_cuprates")).findFirst().orElseThrow();
        assertFalse(cuprates.foundInNature());
        assertEquals(50.0, cuprates.composition().get("Cu"));

        Material radSemiconductors = materials.stream().filter(m -> m.id().equals("radiation_hardened_semiconductors")).findFirst().orElseThrow();
        assertFalse(radSemiconductors.foundInNature());
        assertEquals(50.0, radSemiconductors.composition().get("Si"));
    }

    @Test
    public void testLoadTechnologies() throws IOException {
        List<Technology> technologies = DataModelLoader.loadTechnologies();
        assertNotNull(technologies);
        assertEquals(25, technologies.size());

        Technology electricity = technologies.stream().filter(t -> t.id().equals("electricity")).findFirst().orElseThrow();
        assertEquals("Electricity", electricity.name());
        assertEquals(1, electricity.complexity());
        assertTrue(electricity.requiredTechnologies().isEmpty());
        assertEquals(9, electricity.applications().size());

        TechnicalApplication solar = electricity.applications().stream().filter(a -> a.id().equals("solar_power")).findFirst().orElseThrow();
        assertEquals("Solar power", solar.name());
        assertTrue(solar.affectedFactors().contains("power_production"));
        assertEquals(3, solar.complexity());
        assertEquals(50.0, solar.costToBuildPerUnit(), 0.001);
        assertEquals(45.0, solar.calculateOptimizedUnitCost(1), 0.001);
        assertEquals(40.5, solar.calculateOptimizedUnitCost(2), 0.001);

        Technology fission = technologies.stream().filter(t -> t.id().equals("nuclear_fission")).findFirst().orElseThrow();
        assertEquals("Nuclear fission", fission.name());
        assertEquals(4, fission.complexity());
        assertTrue(fission.requiredTechnologies().contains("electricity"));

        Technology industrial = technologies.stream().filter(t -> t.id().equals("industrial_production")).findFirst().orElseThrow();
        assertEquals(2, industrial.complexity());
        assertTrue(industrial.applications().stream().anyMatch(a -> a.id().equals("industrial_soil_cultivation")));
        assertTrue(industrial.applications().stream().anyMatch(a -> a.id().equals("automated_biosphere_macro_farms")));
        assertTrue(industrial.applications().stream().anyMatch(a -> a.id().equals("hydroponic_growth_arrays")));
        assertTrue(industrial.applications().stream().anyMatch(a -> a.id().equals("aeroponic_nutrient_misting")));
        assertTrue(industrial.applications().stream().anyMatch(a -> a.id().equals("biomass_processing")));
        assertTrue(industrial.applications().stream().anyMatch(a -> a.id().equals("algae_carbon_scrubbing")));
        assertTrue(industrial.applications().stream().anyMatch(a -> a.id().equals("bioreactor_tissue_printing")));
        assertTrue(industrial.applications().stream().anyMatch(a -> a.id().equals("pyrometallurgical_smelting")));
        assertTrue(industrial.applications().stream().anyMatch(a -> a.id().equals("chemical_leaching_hydrometallurgy")));
        assertTrue(industrial.applications().stream().anyMatch(a -> a.id().equals("centrifugal_isotope_separation")));
        assertTrue(industrial.applications().stream().anyMatch(a -> a.id().equals("zero_g_magnetic_refining")));
        assertTrue(industrial.applications().stream().anyMatch(a -> a.id().equals("automated_assembly_lines")));
        assertTrue(industrial.applications().stream().anyMatch(a -> a.id().equals("nanofabrication_matrices")));
        assertTrue(industrial.applications().stream().anyMatch(a -> a.id().equals("replicators_matter_synthesizers")));
        assertTrue(industrial.applications().stream().anyMatch(a -> a.id().equals("heavy_steel_titanium_alloying")));
        assertTrue(industrial.applications().stream().anyMatch(a -> a.id().equals("crystal_lattice_tuning")));
        assertTrue(industrial.applications().stream().anyMatch(a -> a.id().equals("radiation_ablative_shielding")));
        assertTrue(industrial.applications().stream().anyMatch(a -> a.id().equals("orbital_drydocks")));
        assertTrue(industrial.applications().stream().anyMatch(a -> a.id().equals("microgravity_foundry")));
        assertTrue(industrial.applications().stream().anyMatch(a -> a.id().equals("asteroid_capture_processing")));

        Technology rocketry = technologies.stream().filter(t -> t.id().equals("rocketry")).findFirst().orElseThrow();
        assertEquals(3, rocketry.complexity());
        TechnicalApplication fissionEngine = rocketry.applications().stream().filter(a -> a.id().equals("fission_engines")).findFirst().orElseThrow();
        assertTrue(fissionEngine.requiredTechnologies().contains("nuclear_fission"));

        Technology warp = technologies.stream().filter(t -> t.id().equals("warp")).findFirst().orElseThrow();
        assertEquals("Warp", warp.name());
        assertEquals(10, warp.complexity());
        assertTrue(warp.requiredTechnologies().contains("gravitational_engineering"));

        TechnicalApplication warpDrive = warp.applications().stream().filter(a -> a.id().equals("warp_drive")).findFirst().orElseThrow();
        assertEquals("Warp drive", warpDrive.name());
        assertEquals(10, warpDrive.complexity());
        assertEquals(50000.0, warpDrive.costToBuildPerUnit(), 0.001);
        assertEquals(8, warpDrive.calculateOptimizedComplexity(2, 1.0));

        // Verify all required materials exist in materials.json
        List<Material> materials = DataModelLoader.loadMaterials();
        var materialIds = materials.stream().map(Material::id).toList();
        for (Technology tech : technologies) {
            for (TechnicalApplication app : tech.applications()) {
                for (String matId : app.requiredMaterials()) {
                    assertTrue(materialIds.contains(matId),
                            "Material " + matId + " referenced in app " + app.id() + " should exist in materials.json");
                }
            }
        }

        // Verify all technology IDs are unique
        assertEquals(technologies.size(), technologies.stream().map(Technology::id).distinct().count());
    }

    @Test
    public void testLoadStarProperties() throws IOException {
        List<StarProperty> properties = DataModelLoader.loadStarProperties();
        assertNotNull(properties);
        assertEquals(13, properties.size());
        
        StarProperty sunType = properties.stream().filter(p -> p.spectralType().contains("G")).findFirst().orElseThrow();
        assertEquals("#fff4ea", sunType.color());
        assertEquals(0.8, sunType.minMassSolar());
        assertEquals(1.04, sunType.maxMassSolar());
        assertEquals(0.8, sunType.hasPlanetsProbability());
        assertEquals(0.3, sunType.isBinaryProbability());

        StarProperty redGiant = properties.stream().filter(p -> p.spectralType().equals("Red Giant")).findFirst().orElseThrow();
        assertEquals("#ff5a00", redGiant.color());
        assertEquals(0.05, redGiant.hasPlanetsProbability());
        assertEquals(0.3, redGiant.isBinaryProbability());

        StarProperty blackHole = properties.stream().filter(p -> p.spectralType().equals("Black Hole")).findFirst().orElseThrow();
        assertEquals("#000000", blackHole.color());
        assertEquals(0.005, blackHole.hasPlanetsProbability());
    }

    @Test
    public void testLoadProfessions() throws IOException {
        List<Profession> professions = DataModelLoader.loadProfessions();
        assertNotNull(professions);
        assertFalse(professions.isEmpty());

        List<Race> races = DataModelLoader.loadRaces();
        Race human = races.stream().filter(r -> r.id().equals("human")).findFirst().orElseThrow();
        Race vulkan = races.stream().filter(r -> r.id().equals("vulkan")).findFirst().orElseThrow();

        Profession scientist = professions.stream().filter(p -> p.id().equals("scientist")).findFirst().orElseThrow();
        assertEquals("Scientist", scientist.name());
        assertEquals("scientist", scientist.type());
        assertEquals(8, scientist.minimumIntelligence());
        assertEquals(9, scientist.complexity());
        assertEquals(1.11, scientist.retirementAge(), 0.001);
        assertEquals(72, scientist.calculateRetirementAge(human));
        assertEquals(222, scientist.calculateRetirementAge(vulkan));

        Profession farmer = professions.stream().filter(p -> p.id().equals("farmer")).findFirst().orElseThrow();
        assertEquals(1.0, farmer.retirementAge(), 0.001);
        assertEquals(65, farmer.calculateRetirementAge(human));
        assertEquals(200, farmer.calculateRetirementAge(vulkan));

        Profession soldier = professions.stream().filter(p -> p.id().equals("soldier")).findFirst().orElseThrow();
        assertEquals(0.77, soldier.retirementAge(), 0.001);
        assertEquals(50, soldier.calculateRetirementAge(human));
        assertEquals(154, soldier.calculateRetirementAge(vulkan));

        Profession miner = professions.stream().filter(p -> p.id().equals("miner")).findFirst().orElseThrow();
        assertEquals(0.92, miner.retirementAge(), 0.001);
        assertEquals(60, miner.calculateRetirementAge(human));
        assertEquals(184, miner.calculateRetirementAge(vulkan));

        Race silicon = races.stream().filter(r -> r.id().equals("silicon_core")).findFirst().orElseThrow();
        assertEquals(700, farmer.calculateRetirementAge(silicon));
        assertEquals(777, scientist.calculateRetirementAge(silicon));
        assertEquals(539, soldier.calculateRetirementAge(silicon));
        assertEquals(644, miner.calculateRetirementAge(silicon));

        // Technology-adjusted baseline retirement age testing
        int techAdjustedHumanBaseline = 80;
        assertEquals(80, farmer.calculateRetirementAge(techAdjustedHumanBaseline));
        assertEquals(89, scientist.calculateRetirementAge(techAdjustedHumanBaseline));
        assertEquals(62, soldier.calculateRetirementAge(techAdjustedHumanBaseline));
        assertEquals(74, miner.calculateRetirementAge(techAdjustedHumanBaseline));

        assertTrue(professions.stream().anyMatch(p -> p.id().equals("miner")));
        assertEquals(professions.size(), professions.stream().map(Profession::id).distinct().count());
    }

    @Test
    public void testDataIsCached() throws IOException {
        assertSame(DataModelLoader.loadMaterials(), DataModelLoader.loadMaterials());
    }
}
