package com.spaceconquest.engine.technology;

import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.MinistryAssignment;
import com.spaceconquest.engine.Race;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class ResearchProcessorTest {

    private ResearchProcessor processor;

    @BeforeEach
    public void setUp() {
        processor = new ResearchProcessor(new Random(42));
    }

    @Test
    public void testCalculateTurnResearchPoints() {
        Race race = new Race(
                "terran", "Human", "Description",
                1.2, 1.0, "Individualist",
                1.0, 293.15, "Carbon based", "Oxygen based",
                18, 50, "Organic", "Diverse", 80
        );

        MinistryAssignment scienceMinistry = new MinistryAssignment(
                "ministry_technology_application",
                "scientist",
                1.35
        );

        Empire empire = new Empire(
                "emp_terran", "Terran Federation", "terran", "Individualist",
                50000.0, 0.05, List.of("sol"),
                List.of(scienceMinistry), Map.of(), List.of(), List.of()
        );

        // 10 scientists * 1.0 training * 1.2 intelligence * 1.35 ministry * 1.0 speed
        // = 10 * 1.2 * 1.35 = 16.2
        double points = processor.calculateTurnResearchPoints(10, 1.0, race, empire, 1.0);
        assertEquals(16.2, points, 0.001);
    }

    @Test
    public void testAdvanceProjectAndCompletion() {
        ResearchProject project = new ResearchProject(
                "proj_1", "emp_terran", "nuclear_fission", false,
                90.0, 100.0, 10, 1.0
        );

        Race race = new Race(
                "terran", "Human", "Description",
                1.0, 1.0, "Individualist",
                1.0, 293.15, "Carbon based", "Oxygen based",
                18, 50, "Organic", "Diverse", 80
        );

        Empire empire = new Empire(
                "emp_terran", "Terran Federation", "terran", "Individualist",
                50000.0, 0.05, List.of("sol"),
                List.of(), Map.of(), List.of(), List.of()
        );

        ResearchProject advanced = processor.advanceProject(project, race, empire, List.of());
        assertEquals(100.0, advanced.accumulatedPoints(), 0.001);
        assertTrue(advanced.isComplete());
        assertEquals(100.0, advanced.getProgressPercentage(), 0.001);
    }

    @Test
    public void testTechnologyExchangeRouteBonus() {
        ResearchProject project = new ResearchProject(
                "proj_1", "emp_terran", "nuclear_fission", false,
                0.0, 100.0, 10, 1.0
        );

        TechnologyExchangeRoute route = new TechnologyExchangeRoute(
                "route_1", "emp_alien", "emp_terran", "nuclear_fission", 0.40, 0.50
        );

        Race race = new Race(
                "terran", "Human", "Description",
                1.0, 1.0, "Individualist",
                1.0, 293.15, "Carbon based", "Oxygen based",
                18, 50, "Organic", "Diverse", 80
        );

        Empire empire = new Empire(
                "emp_terran", "Terran Federation", "terran", "Individualist",
                50000.0, 0.05, List.of("sol"),
                List.of(), Map.of(), List.of(), List.of()
        );

        ResearchProject advanced = processor.advanceProject(project, race, empire, List.of(route));
        // 10 base * 1.0 * 1.0 * (1.0 * (1.0 + 0.50)) = 15.0
        assertEquals(15.0, advanced.accumulatedPoints(), 0.001);
    }

    @Test
    public void testWeightedVarianceBreakthroughRolls() {
        // Critical breakthrough (< 0.05)
        ResearchVarianceResult crit = processor.rollBreakthrough(0.02);
        assertEquals(ResearchVarianceResult.CRITICAL_BREAKTHROUGH, crit.outcomeType());
        assertEquals(1.20, crit.effectMultiplier(), 0.001);
        assertEquals(0.85, crit.costMultiplier(), 0.001);
        assertEquals(0, crit.complexityShift());

        // Optimized success (0.05 to 0.50)
        ResearchVarianceResult opt = processor.rollBreakthrough(0.25);
        assertEquals(ResearchVarianceResult.OPTIMIZED_SUCCESS, opt.outcomeType());

        // Incremental gain (0.50 to 0.90)
        ResearchVarianceResult inc = processor.rollBreakthrough(0.70);
        assertEquals(ResearchVarianceResult.INCREMENTAL_GAIN, inc.outcomeType());
        assertEquals(1.10, inc.effectMultiplier(), 0.001);
        assertEquals(1.05, inc.costMultiplier(), 0.001);
        assertEquals(1, inc.complexityShift());

        // Flawed setback (0.90 to 1.00)
        ResearchVarianceResult flaw = processor.rollBreakthrough(0.95);
        assertEquals(ResearchVarianceResult.FLAWED_SETBACK, flaw.outcomeType());
        assertEquals(1.05, flaw.effectMultiplier(), 0.001);
        assertEquals(1.25, flaw.costMultiplier(), 0.001);
        assertEquals(2, flaw.complexityShift());
    }

    @Test
    public void testDualPathOptimizationEvaluation() {
        // Path A: Performance up-scaling
        ResearchVarianceResult pathA = processor.evaluateOptimizationPath("PATH_A", 1.0);
        assertEquals(1.15, pathA.effectMultiplier(), 0.001);
        assertEquals(1.20, pathA.costMultiplier(), 0.001);
        assertEquals(1, pathA.complexityShift());

        // Path B: Miniaturization
        ResearchVarianceResult pathB = processor.evaluateOptimizationPath("PATH_B", 1.0);
        assertEquals(1.00, pathB.effectMultiplier(), 0.001);
        assertEquals(0.85, pathB.costMultiplier(), 0.001);
        assertEquals(-1, pathB.complexityShift());
    }

    @Test
    public void testSalvageReverseEngineeringBiochemicalTranslation() {
        Race human = new Race(
                "terran", "Human", "Description",
                1.0, 1.0, "Individualist",
                1.0, 293.15, "Carbon based", "Oxygen based",
                18, 50, "Organic", "Diverse", 80
        );

        Race silicon = new Race(
                "silicon_litho", "Silicoid", "Description",
                1.0, 1.0, "Collectivist",
                1.5, 350.0, "Silicon based", "Nitrogen based",
                10, 80, "Rock", "Simple", 150
        );

        // Same chemistry (carbon vs carbon)
        double pointsCarbon = processor.calculateSalvageProgressPoints(true, 1, human, human, 1000.0);
        // 1000 * 0.35 * 1.0 * 1.0 = 350.0
        assertEquals(350.0, pointsCarbon, 0.001);

        // Cross-chemistry penalty (carbon vs silicon = 0.50 modifier)
        double pointsSilicon = processor.calculateSalvageProgressPoints(true, 1, human, silicon, 1000.0);
        assertEquals(175.0, pointsSilicon, 0.001);

        // Shattered wreckage (0.10 fraction) with volume multiplier (5 components)
        double pointsShattered = processor.calculateSalvageProgressPoints(false, 5, human, human, 1000.0);
        // 1000 * 0.10 * (1.0 + 0.15 * 4) * 1.0 = 100 * 1.6 = 160.0
        assertEquals(160.0, pointsShattered, 0.001);
    }
}
