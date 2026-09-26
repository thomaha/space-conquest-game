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
        assertEquals(6, races.size());

        Race human = races.stream().filter(r -> r.id().equals("human")).findFirst().orElseThrow();
        assertEquals("Human", human.name());
        assertEquals(1.0, human.intelligence());
        assertEquals(1.0, human.physicalStrength());
        assertEquals("Individualist", human.societyStructure());
        assertEquals(9.81, human.preferredGravity());
        assertEquals(9.81, human.preferredGForce());
        assertEquals(288.0, human.preferredTemperature());
        assertEquals("Carbon based", human.chemicalComposition());
        assertEquals("Oxygen based", human.breathingAtmosphere());
        assertEquals(15, human.fertileAgeStart());
        assertEquals(45, human.fertileAgeEnd());
        assertEquals("Organic", human.nutrientType());
        assertEquals("Diverse", human.nutrientSpreadRequirement());
        assertEquals(85, human.naturalLifespan());
        assertEquals(85, human.retirementAge());
        assertEquals(5.0, human.calculateActualIntelligenceScore());
        assertEquals(5.0, human.calculateActualStrengthScore());

        Race silicon = races.stream().filter(r -> r.id().equals("silicon_core")).findFirst().orElseThrow();
        assertEquals("Silicon core", silicon.name());
        assertEquals(1.2, silicon.intelligence());
        assertEquals(1.8, silicon.physicalStrength());
        assertEquals("Collectivist", silicon.societyStructure());
        assertEquals(1.0, silicon.preferredGravity());
        assertEquals(1.0, silicon.preferredGForce());
        assertEquals(120.0, silicon.preferredTemperature());
        assertEquals("Silicon based", silicon.chemicalComposition());
        assertEquals("Vacuum compatible", silicon.breathingAtmosphere());
        assertEquals(50, silicon.fertileAgeStart());
        assertEquals(600, silicon.fertileAgeEnd());
        assertEquals("Rock", silicon.nutrientType());
        assertEquals("Diverse", silicon.nutrientSpreadRequirement());
        assertEquals(800, silicon.naturalLifespan());
        assertEquals(800, silicon.retirementAge());
        assertEquals(6.0, silicon.calculateActualIntelligenceScore(), 0.001);
        assertEquals(9.0, silicon.calculateActualStrengthScore(), 0.001);

        Race ammonia = races.stream().filter(r -> r.id().equals("ammonia_entity")).findFirst().orElseThrow();
        assertEquals("Ammonia entity", ammonia.name());
        assertEquals(1.0, ammonia.intelligence());
        assertEquals(0.8, ammonia.physicalStrength());
        assertEquals("Collectivist", ammonia.societyStructure());
        assertEquals(4.9, ammonia.preferredGravity());
        assertEquals(4.9, ammonia.preferredGForce());
        assertEquals(210.0, ammonia.preferredTemperature());
        assertEquals("Hydro-nitrogen composite", ammonia.chemicalComposition());
        assertEquals("Nitrogen based", ammonia.breathingAtmosphere());
        assertEquals(12, ammonia.fertileAgeStart());
        assertEquals(80, ammonia.fertileAgeEnd());
        assertEquals("Gas", ammonia.nutrientType());
        assertEquals("Simple", ammonia.nutrientSpreadRequirement());
        assertEquals(110, ammonia.naturalLifespan());

        Race synthetic = races.stream().filter(r -> r.id().equals("synthetic_machine")).findFirst().orElseThrow();
        assertEquals("Synthetic machine", synthetic.name());
        assertEquals(1.5, synthetic.intelligence());
        assertEquals(1.4, synthetic.physicalStrength());
        assertEquals("Individualist", synthetic.societyStructure());
        assertEquals(0.0, synthetic.preferredGravity());
        assertEquals(0.0, synthetic.preferredGForce());
        assertEquals(150.0, synthetic.preferredTemperature());
        assertEquals("Refined metal / Silicon substrate", synthetic.chemicalComposition());
        assertEquals("Vacuum compatible", synthetic.breathingAtmosphere());
        assertEquals(0, synthetic.fertileAgeStart());
        assertEquals(0, synthetic.fertileAgeEnd());
        assertEquals("Electricity", synthetic.nutrientType());
        assertEquals("Simple", synthetic.nutrientSpreadRequirement());
        assertEquals(9999, synthetic.naturalLifespan());
        assertEquals(7.5, synthetic.calculateActualIntelligenceScore(), 0.001);
        assertEquals(7.0, synthetic.calculateActualStrengthScore(), 0.001);

        Race plasma = races.stream().filter(r -> r.id().equals("plasma_anomaly")).findFirst().orElseThrow();
        assertEquals("Plasma anomaly", plasma.name());
        assertEquals(1.4, plasma.intelligence());
        assertEquals(0.5, plasma.physicalStrength());
        assertEquals("Hive mind", plasma.societyStructure());
        assertEquals(49.0, plasma.preferredGravity());
        assertEquals(49.0, plasma.preferredGForce());
        assertEquals(1500.0, plasma.preferredTemperature());
        assertEquals("Ionized gas / Electromagnetic plasma", plasma.chemicalComposition());
        assertEquals("Vacuum compatible", plasma.breathingAtmosphere());
        assertEquals(5, plasma.fertileAgeStart());
        assertEquals(120, plasma.fertileAgeEnd());
        assertEquals("Metal", plasma.nutrientType());
        assertEquals("Simple", plasma.nutrientSpreadRequirement());
        assertEquals(180, plasma.naturalLifespan());

        Race vulkan = races.stream().filter(r -> r.id().equals("vulkan")).findFirst().orElseThrow();
        assertEquals("Vulkan", vulkan.name());
        assertEquals(1.5, vulkan.intelligence());
        assertEquals(1.2, vulkan.physicalStrength());
        assertEquals("Individualist", vulkan.societyStructure());
        assertEquals(14.0, vulkan.preferredGravity());
        assertEquals(14.0, vulkan.preferredGForce());
        assertEquals(200, vulkan.naturalLifespan());
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

        Material boronOre = materials.stream().filter(m -> m.id().equals("boron_ore")).findFirst().orElseThrow();
        assertTrue(boronOre.foundInNature());

        Material rareEarthFluorides = materials.stream().filter(m -> m.id().equals("rare_earth_fluorides")).findFirst().orElseThrow();
        assertTrue(rareEarthFluorides.foundInNature());

        Material neutronium = materials.stream().filter(m -> m.id().equals("hyperdense_neutronium")).findFirst().orElseThrow();
        assertFalse(neutronium.foundInNature());
        assertEquals(5000.0, neutronium.strength());

        Material tachyon = materials.stream().filter(m -> m.id().equals("tachyon_condensate")).findFirst().orElseThrow();
        assertFalse(tachyon.foundInNature());
        assertEquals(10, tachyon.complexity());

        Material metamaterials = materials.stream().filter(m -> m.id().equals("metamaterial_composites")).findFirst().orElseThrow();
        assertFalse(metamaterials.foundInNature());

        Material antimatterCell = materials.stream().filter(m -> m.id().equals("antimatter_containment_cell")).findFirst().orElseThrow();
        assertFalse(antimatterCell.foundInNature());

        Material plasmonic = materials.stream().filter(m -> m.id().equals("exotic_plasmonic_alloys")).findFirst().orElseThrow();
        assertFalse(plasmonic.foundInNature());

        Material spinGlass = materials.stream().filter(m -> m.id().equals("quantum_spin_glass")).findFirst().orElseThrow();
        assertFalse(spinGlass.foundInNature());

        Material superinsulator = materials.stream().filter(m -> m.id().equals("cryogenic_superinsulator")).findFirst().orElseThrow();
        assertFalse(superinsulator.foundInNature());

        Material boronNitride = materials.stream().filter(m -> m.id().equals("crystalline_boron_nitride")).findFirst().orElseThrow();
        assertFalse(boronNitride.foundInNature());

        Material monoWire = materials.stream().filter(m -> m.id().equals("monomolecular_wire")).findFirst().orElseThrow();
        assertFalse(monoWire.foundInNature());

        Material progMatter = materials.stream().filter(m -> m.id().equals("programmable_matter")).findFirst().orElseThrow();
        assertFalse(progMatter.foundInNature());

        Material nanofluid = materials.stream().filter(m -> m.id().equals("polymorphic_nanofluid")).findFirst().orElseThrow();
        assertFalse(nanofluid.foundInNature());

        Material rtg = materials.stream().filter(m -> m.id().equals("radioisotopic_thermoelectric_matrix")).findFirst().orElseThrow();
        assertFalse(rtg.foundInNature());

        Material metallicH2 = materials.stream().filter(m -> m.id().equals("stabilized_metallic_hydrogen")).findFirst().orElseThrow();
        assertFalse(metallicH2.foundInNature());
        assertEquals(2500.0, metallicH2.strength());
    }

    @Test
    public void testLoadTechnologies() throws IOException {
        List<Technology> technologies = DataModelLoader.loadTechnologies();
        assertNotNull(technologies);
        assertEquals(26, technologies.size());

        Technology electricity = technologies.stream().filter(t -> t.id().equals("electricity")).findFirst().orElseThrow();
        assertEquals("Electricity", electricity.name());
        assertEquals(1, electricity.complexity());
        assertTrue(electricity.requiredTechnologies().isEmpty());
        assertEquals(10, electricity.applications().size());
        assertTrue(electricity.applications().stream().anyMatch(a -> a.id().equals("combustion_power")));

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

        Technology megaTech = technologies.stream().filter(t -> t.id().equals("stellar_megastructures")).findFirst().orElseThrow();
        assertEquals("Stellar megastructures", megaTech.name());
        assertEquals(9, megaTech.complexity());
        assertTrue(megaTech.requiredTechnologies().contains("gravitational_engineering"));
        assertTrue(megaTech.requiredTechnologies().contains("surface_to_orbit_infrastructure"));
        assertEquals(2, megaTech.applications().size());

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
        assertEquals(12, professions.size());

        List<Race> races = DataModelLoader.loadRaces();
        Race human = races.stream().filter(r -> r.id().equals("human")).findFirst().orElseThrow();
        Race vulkan = races.stream().filter(r -> r.id().equals("vulkan")).findFirst().orElseThrow();
        Race silicon = races.stream().filter(r -> r.id().equals("silicon_core")).findFirst().orElseThrow();
        Race synthetic = races.stream().filter(r -> r.id().equals("synthetic_machine")).findFirst().orElseThrow();

        // 1. Soldier test (Species.md: Human Soldier: 85 * 0.55 = 46.75 -> 47)
        Profession soldier = professions.stream().filter(p -> p.id().equals("soldier")).findFirst().orElseThrow();
        assertEquals("Soldier", soldier.name());
        assertEquals("soldier", soldier.type());
        assertEquals(3, soldier.minimumIntelligence());
        assertEquals(7, soldier.minimumStrength());
        assertEquals(4, soldier.complexity());
        assertEquals(0.55, soldier.retirementLifespanPercentage(), 0.001);
        assertEquals(0.55, soldier.retirementAge(), 0.001);
        assertEquals(46.75, soldier.calculateExactRetirementAge(human.naturalLifespan()), 0.001);
        assertEquals(47, soldier.calculateRetirementAge(human));
        assertEquals(110, soldier.calculateRetirementAge(vulkan));
        assertEquals(440, soldier.calculateRetirementAge(silicon));
        assertFalse(soldier.qualifies(human)); // Human strength score 5.0 < 7
        assertTrue(soldier.qualifies(silicon)); // Silicon strength score 9.0 >= 7 and int score 6.0 >= 3

        // 2. Scientist test (Species.md: Human Scientist: 85 * 0.85 = 72.25 -> 72)
        Profession scientist = professions.stream().filter(p -> p.id().equals("scientist")).findFirst().orElseThrow();
        assertEquals("Scientist", scientist.name());
        assertEquals("scientist", scientist.type());
        assertEquals(8, scientist.minimumIntelligence());
        assertEquals(1, scientist.minimumStrength());
        assertEquals(9, scientist.complexity());
        assertEquals(0.85, scientist.retirementLifespanPercentage(), 0.001);
        assertEquals(72.25, scientist.calculateExactRetirementAge(human.naturalLifespan()), 0.001);
        assertEquals(72, scientist.calculateRetirementAge(human));
        assertEquals(170, scientist.calculateRetirementAge(vulkan));
        assertEquals(680, scientist.calculateRetirementAge(silicon));

        // 3. Miner test (Species.md: Silicon Core Miner: 800 * 0.65 = 520)
        Profession miner = professions.stream().filter(p -> p.id().equals("miner")).findFirst().orElseThrow();
        assertEquals("Miner", miner.name());
        assertEquals("miner", miner.type());
        assertEquals(2, miner.minimumIntelligence());
        assertEquals(6, miner.minimumStrength());
        assertEquals(3, miner.complexity());
        assertEquals(0.65, miner.retirementLifespanPercentage(), 0.001);
        assertEquals(55.25, miner.calculateExactRetirementAge(human.naturalLifespan()), 0.001);
        assertEquals(55, miner.calculateRetirementAge(human));
        assertEquals(130, miner.calculateRetirementAge(vulkan));
        assertEquals(520, miner.calculateRetirementAge(silicon));
        assertTrue(miner.qualifies(silicon));

        // 4. Farmer test
        Profession farmer = professions.stream().filter(p -> p.id().equals("farmer")).findFirst().orElseThrow();
        assertEquals(0.76, farmer.retirementLifespanPercentage(), 0.001);
        assertEquals(65, farmer.calculateRetirementAge(human));
        assertEquals(152, farmer.calculateRetirementAge(vulkan));
        assertEquals(608, farmer.calculateRetirementAge(silicon));

        // 5. Technician and Police qualification checks (Species.md: Humans 5.0 clears tech/police req 4-5)
        Profession technician = professions.stream().filter(p -> p.id().equals("technician")).findFirst().orElseThrow();
        assertTrue(technician.qualifies(human)); // req: int 5, str 4 -> human has 5.0, 5.0
        Profession police = professions.stream().filter(p -> p.id().equals("police")).findFirst().orElseThrow();
        assertFalse(police.qualifies(human)); // req: int 4, str 6 -> human str is 5.0

        // 6. Technology-adjusted baseline natural lifespan testing (e.g. Gene Therapy pushes human lifespan to 100)
        int techAdjustedHumanLifespan = 100;
        assertEquals(76, farmer.calculateRetirementAge(techAdjustedHumanLifespan));
        assertEquals(85, scientist.calculateRetirementAge(techAdjustedHumanLifespan));
        assertEquals(55, soldier.calculateRetirementAge(techAdjustedHumanLifespan));
        assertEquals(65, miner.calculateRetirementAge(techAdjustedHumanLifespan));

        // Verify all profession IDs are unique
        assertEquals(professions.size(), professions.stream().map(Profession::id).distinct().count());
    }

    @Test
    public void testLoadEmpires() throws IOException {
        List<Empire> empires = DataModelLoader.loadEmpires();
        assertNotNull(empires);
        assertEquals(4, empires.size());

        Empire terran = DataModelLoader.loadEmpire("terran_confederation");
        assertEquals("Terran Confederation", terran.name());
        assertEquals("human", terran.raceId());
        assertEquals("Individualist", terran.societyStructure());
        assertEquals(50000.0, terran.treasuryCredits());
        assertEquals(0.15, terran.corporateTaxRate(), 0.001);
        assertEquals(List.of("sol"), terran.controlledSystemIds());
        assertEquals(5, terran.ministries().size());
        assertEquals("gov_sol_human", terran.systemGovernorAssignments().get("sol"));

        Empire vulkan = DataModelLoader.loadEmpire("vulkan_forge");
        assertEquals("Vulkan High Command", vulkan.name());
        assertEquals("vulkan", vulkan.raceId());
        assertEquals("Individualist", vulkan.societyStructure());
        assertEquals(60000.0, vulkan.treasuryCredits());
        assertEquals(0.10, vulkan.corporateTaxRate(), 0.001);
        assertEquals(List.of("vulcan-system"), vulkan.controlledSystemIds());
        assertEquals(3, vulkan.ministries().size());
        assertEquals("gov_vulcan", vulkan.systemGovernorAssignments().get("vulcan-system"));

        Empire silicon = DataModelLoader.loadEmpire("silicon_hegemony");
        assertEquals("Collectivist", silicon.societyStructure());
        assertEquals(40000.0, silicon.treasuryCredits());

        Empire hive = DataModelLoader.loadEmpire("plasma_convergence");
        assertEquals("Hive mind", hive.societyStructure());
        assertEquals(0.0, hive.treasuryCredits());
        assertTrue(hive.ministries().isEmpty());
    }

    @Test
    public void testLoadCorporations() throws IOException {
        List<Corporation> corporations = DataModelLoader.loadCorporations();
        assertNotNull(corporations);
        assertEquals(5, corporations.size());

        Corporation extraction = DataModelLoader.loadCorporation("corp_sol_extraction");
        assertEquals("Asteroid Mining Syndicate", extraction.name());
        assertEquals("EXTRACTION", extraction.marketOrientation());
        assertEquals("terran_confederation", extraction.empireId());
        assertEquals(25000.0, extraction.liquidCapitalReserves());

        Corporation transport = DataModelLoader.loadCorporation("corp_terran_transport");
        assertEquals("TRANSPORT", transport.marketOrientation());
        assertEquals(2, transport.ownedShipIds().size());
    }

    @Test
    public void testLoadMinistries() throws IOException {
        List<MinistryPortfolio> ministries = DataModelLoader.loadMinistries();
        assertNotNull(ministries);
        assertEquals(5, ministries.size());

        MinistryPortfolio industry = DataModelLoader.loadMinistry("ministry_industry_refining");
        assertEquals("Ministry of industry and refining", industry.name());
        assertTrue(industry.optimalProfessionIds().contains("industrial_worker"));
        assertTrue(industry.optimalProfessionIds().contains("miner"));
        assertEquals(1.10, industry.baseEfficiencyModifier(), 0.001);
        assertEquals(1.25, industry.synergyEfficiencyModifier(), 0.001);

        MinistryPortfolio defense = DataModelLoader.loadMinistry("ministry_defense_logistics");
        assertTrue(defense.targetModifiers().contains("GROUND_COMBAT_STRENGTH"));
        assertTrue(defense.targetModifiers().contains("SPACEFRAME_BUILD_TIME"));
    }

    @Test
    public void testDataIsCached() throws IOException {
        assertSame(DataModelLoader.loadMaterials(), DataModelLoader.loadMaterials());
        assertSame(DataModelLoader.loadEmpires(), DataModelLoader.loadEmpires());
        assertSame(DataModelLoader.loadCorporations(), DataModelLoader.loadCorporations());
        assertSame(DataModelLoader.loadMinistries(), DataModelLoader.loadMinistries());
    }
}
