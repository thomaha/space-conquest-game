package com.spaceconquest.engine.industry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class ProspectingProcessorTest {

    private ProspectingProcessor prospectingProcessor;

    @BeforeEach
    public void setUp() {
        prospectingProcessor = new ProspectingProcessor(new Random(42));
    }

    @Test
    public void testDiscoveryProbabilityDecaysWithVeinExtractionScarcity() {
        // High initial probability with zero discovered veins
        double initialProb = prospectingProcessor.calculateDiscoveryProbability(
                0.50, 1.0, List.of(), 10_000_000.0, 1.5
        );
        assertEquals(0.50, initialProb, 0.001);

        // Substantially lower probability when 8,000,000 kg out of 10,000,000 kg are already discovered
        GeologicalDeposit largeDiscovered = new GeologicalDeposit(
                "dep_1", "earth", "iron_ore", 8_000_000.0, 4_000_000.0, 1.0, true, "emp_terran"
        );
        double depletedProb = prospectingProcessor.calculateDiscoveryProbability(
                0.50, 1.0, List.of(largeDiscovered), 10_000_000.0, 1.5
        );

        // Depletion ratio = 0.8, (1 - 0.8)^1.5 = 0.2^1.5 = 0.0894
        // Expected prob = 0.50 * 1.0 * 0.0894 = 0.0447
        assertTrue(depletedProb < initialProb, "Probability should decay with discovered vein volume");
        assertEquals(0.0447, depletedProb, 0.005);
    }

    @Test
    public void testExecuteProspectingSurveyUncoversVein() {
        GeologicalDeposit hiddenVein = new GeologicalDeposit(
                "dep_hidden", "mars", "silicon_crystals", 100_000.0, 100_000.0, 1.5, false, ""
        );

        // Always discover with 1.0 efficiency
        ProspectingProcessor forcedDiscoverProcessor = new ProspectingProcessor(new Random(10) {
            @Override
            public double nextDouble() {
                return 0.01; // Force pass
            }
        });

        List<GeologicalDeposit> results = forcedDiscoverProcessor.executeProspectingSurvey(
                "mars", "emp_terran", 0.90, 1.0, List.of(hiddenVein)
        );

        assertEquals(1, results.size());
        assertTrue(results.getFirst().isDiscovered(), "Hidden vein should become discovered");
        assertEquals("emp_terran", results.getFirst().ownerEntityId());
    }

    @Test
    public void testCrustBackgroundMiningCostCalculation() {
        // Requested 1000 kg of rare material (rarity = 0.10)
        ProspectingProcessor.BackgroundMiningCost cost = prospectingProcessor.calculateBackgroundMiningCost(1000.0, 0.10);

        // inverse rarity = 10.0
        // workHours = 1000 * 10.0 * 0.1 = 1000.0
        // electricity = 1000 * 10.0 * 0.5 = 5000.0
        assertEquals(1000.0, cost.requiredWorkHours(), 0.001);
        assertEquals(5000.0, cost.electricityKw(), 0.001);
    }
}
