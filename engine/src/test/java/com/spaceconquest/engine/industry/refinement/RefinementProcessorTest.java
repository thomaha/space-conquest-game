package com.spaceconquest.engine.industry.refinement;

import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.Race;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RefinementProcessorTest {

    private RefinementProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new RefinementProcessor();
    }

    @Test
    void testPyrometallurgicalSmeltingGravityConstraint() {
        RefinementRecipe pyroSmelting = RefinementProcessor.STANDARD_RECIPES.stream()
                .filter(r -> r.id().equals("pyro_iron_smelting"))
                .findFirst()
                .orElse(null);
        assertNotNull(pyroSmelting);

        Map<String, Double> mats = Map.of("iron_ore", 1000.0, "carbon_monoxide_ice", 200.0);

        // Fails in microgravity (0.01g < 0.1g)
        RefinementProcessor.RefinementExecutionResult failRes = processor.processRecipeExecution(
                pyroSmelting, mats, 5000.0, 0.01, 1
        );
        assertFalse(failRes.isSuccessful());

        // Succeeds on planet (1.0g > 0.1g)
        RefinementProcessor.RefinementExecutionResult successRes = processor.processRecipeExecution(
                pyroSmelting, mats, 5000.0, 1.0, 1
        );
        assertTrue(successRes.isSuccessful());
        assertEquals(700.0, successRes.producedOutputsKg().get("refined_iron"));
        assertEquals(300.0, successRes.producedByproductsKg().get("oxygen_gas"));
    }

    @Test
    void testZeroGMagneticRefiningConstraint() {
        RefinementRecipe zeroGRecipe = RefinementProcessor.STANDARD_RECIPES.stream()
                .filter(r -> r.id().equals("zero_g_platinum"))
                .findFirst()
                .orElse(null);
        assertNotNull(zeroGRecipe);

        Map<String, Double> mats = Map.of("platinum_ore", 1000.0);

        // Fails on high gravity world (1.0g > 0.05g)
        RefinementProcessor.RefinementExecutionResult failRes = processor.processRecipeExecution(
                zeroGRecipe, mats, 15000.0, 1.0, 2
        );
        assertFalse(failRes.isSuccessful());

        // Succeeds in microgravity (0.02g < 0.05g)
        RefinementProcessor.RefinementExecutionResult successRes = processor.processRecipeExecution(
                zeroGRecipe, mats, 15000.0, 0.02, 2
        );
        assertTrue(successRes.isSuccessful());
        assertEquals(500.0, successRes.producedOutputsKg().get("refined_platinum"));
    }

    @Test
    void testStandardOfLivingSatisfactionAndHiveMindImmunity() {
        Race human = new Race(
                "human", "Human", "Carbon-based humanoid", 1.0, 1.0, "Individualist",
                1.0, 293.15, "Carbon", "Oxygen", 18, 50, "Organic", "Diverse", 80
        );
        Population pop = new Population("human", Map.of(30, 100000L)); // 100k pop

        // 100k pop needs 5,000 kg consumer goods (50kg per 1,000 citizens)
        RefinementProcessor.StandardOfLivingResult goodResult = processor.evaluateStandardOfLiving(pop, human, 5000.0);
        assertTrue(goodResult.satisfactionIndex() >= 1.0);
        assertTrue(goodResult.happinessModifier() > 0.0);
        assertTrue(goodResult.crimeModifier() < 0.0);

        // Hive mind society has 0 consumer goods demand and neutral modifiers
        Race hiveRace = new Race(
                "hive_insect", "Chitinous Hive", "Insectoid hive mind", 0.8, 1.5, "Hive mind",
                1.0, 298.15, "Carbon", "Oxygen", 1, 100, "Organic", "Simple", 50
        );
        RefinementProcessor.StandardOfLivingResult hiveResult = processor.evaluateStandardOfLiving(pop, hiveRace, 0.0);
        assertEquals(1.0, hiveResult.satisfactionIndex());
        assertEquals(0.0, hiveResult.happinessModifier());
        assertEquals(0.0, hiveResult.crimeModifier());
    }
}
