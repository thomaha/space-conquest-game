package com.spaceconquest.engine.terraforming;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TerraformingProcessorTest {

    private TerraformingProcessor processor;

    @BeforeEach
    public void setup() {
        processor = new TerraformingProcessor();
    }

    @Test
    public void testSolarMirrorWarmsFrozenPlanet() {
        AtmosphericComposition frozen = new AtmosphericComposition(
                "mars_frozen",
                Map.of("nitrogen_gas", 0.90, "carbon_dioxide", 0.10),
                0.6,
                220.0,
                1.0,
                15.0,
                AtmosphericComposition.BIOME_FROZEN,
                false
        );

        GeoengineeringProject mirror = new GeoengineeringProject(
                "proj_mirror_1", "mars_frozen", "terran_confederation",
                GeoengineeringProject.TYPE_SOLAR_MIRROR, 0.0, 5.0,
                0.6, 280.0, Map.of(), false
        );

        TerraformingProcessor.TerraformingTurnResult result = processor.processPlanetTerraforming(frozen, List.of(mirror));

        assertNotNull(result);
        assertTrue(result.updatedAtmosphere().surfaceTemperatureK() > 220.0);
        assertEquals(1.0, result.updatedProjects().get(0).accumulatedProgress(), 0.01);
    }

    @Test
    public void testCyanobacteriaSeedingProducesOxygenAndHabitability() {
        AtmosphericComposition toxic = new AtmosphericComposition(
                "venus_proto",
                Map.of("nitrogen_gas", 0.70, "carbon_dioxide", 0.25, "toxic_aerosols", 0.05),
                1.0,
                295.0,
                1.1,
                5.0,
                AtmosphericComposition.BIOME_TOXIC,
                false
        );

        GeoengineeringProject cyano = new GeoengineeringProject(
                "proj_cyano_1", "venus_proto", "terran_confederation",
                GeoengineeringProject.TYPE_CYANOBACTERIA_SEEDING, 0.0, 10.0,
                1.0, 295.0, Map.of("oxygen_gas", 0.20), false
        );

        AtmosphericComposition current = toxic;
        List<GeoengineeringProject> currentProjects = List.of(cyano);

        for (int turn = 0; turn < 10; turn++) {
            TerraformingProcessor.TerraformingTurnResult res = processor.processPlanetTerraforming(current, currentProjects);
            current = res.updatedAtmosphere();
            currentProjects = res.updatedProjects();
        }

        assertTrue(current.getGasRatio("oxygen_gas") > 0.15);
        assertTrue(current.getGasRatio("carbon_dioxide") < 0.15);
    }

    @Test
    public void testBreathableBiomeTransformation() {
        AtmosphericComposition nearBreathable = new AtmosphericComposition(
                "new_earth",
                Map.of("nitrogen_gas", 0.78, "oxygen_gas", 0.16, "carbon_dioxide", 0.05, "toxic_aerosols", 0.01),
                1.0,
                290.0,
                1.0,
                5.0,
                AtmosphericComposition.BIOME_BARREN,
                false
        );

        GeoengineeringProject cyano = new GeoengineeringProject(
                "proj_cyano_finish", "new_earth", "terran_confederation",
                GeoengineeringProject.TYPE_CYANOBACTERIA_SEEDING, 0.0, 2.0,
                1.0, 290.0, Map.of(), false
        );

        TerraformingProcessor.TerraformingTurnResult result = processor.processPlanetTerraforming(nearBreathable, List.of(cyano));

        assertNotNull(result);
        assertTrue(result.updatedAtmosphere().isBreathable());
        assertEquals(AtmosphericComposition.BIOME_BREATHABLE_TERRESTRIAL, result.updatedAtmosphere().biomeType());
    }
}
