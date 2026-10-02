package com.spaceconquest.engine.terraforming;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.MarketOrder;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.industry.ConstructionMaterialCatalog;

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
    public void materialShortagePausesTerraformingAfterPartialWork() {
        Map<String, Double> bill = ConstructionMaterialCatalog.terraforming(
                GeoengineeringProject.TYPE_GREENHOUSE_FACTORY);
        GeoengineeringProject project = new GeoengineeringProject("terra", "mars", "emp",
                GeoengineeringProject.TYPE_GREENHOUSE_FACTORY, 0.0, 10.0,
                1.0, 288.0, Map.of(), bill, Map.of(), false);
        Planet mars = new Planet("mars", "Mars", "", 1, 1, 1, 0, 0,
                "terrestrial", "none", false, 1, List.of(), List.of(), List.of());
        SolarSystem sol = new SolarSystem("sol", "Sol", "", 0, 0, 0, 1, 1,
                "yellow", List.of(mars), List.of());
        Empire owner = new Empire("emp", "Empire", "human", "Individualist", 10_000.0,
                0.1, List.of("sol"), List.of(), Map.of(), List.of(), List.of());
        CommercialHub hub = new CommercialHub("hub", "mars", 0.0, 10_000.0, 260.0, 1.0,
                Map.of("refined_iron", new MarketOrder("refined_iron", 260.0, 0, 1, 0)));
        GameState state = GameState.builder().solarSystems(List.of(sol)).empires(List.of(owner))
                .commercialHubs(List.of(hub)).terraformingProjects(List.of(project)).build();

        GameState first = processor.processConstruction(state);
        assertEquals(1.0, first.terraformingProjects().getFirst().accumulatedProgress(), 0.00001);
        assertFalse(first.terraformingProjects().getFirst().isCompleted());
        GameState stalled = processor.processConstruction(first);
        assertEquals(1.0, stalled.terraformingProjects().getFirst().accumulatedProgress(), 0.00001);
        assertEquals(260.0, stalled.terraformingProjects().getFirst().consumedMaterialsKg()
                .get("refined_iron"), 0.00001);
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
        assertEquals(1.0, result.updatedProjects().getFirst().accumulatedProgress(), 0.01);
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
