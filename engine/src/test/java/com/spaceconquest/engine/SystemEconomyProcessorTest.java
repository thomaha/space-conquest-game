package com.spaceconquest.engine;

import com.spaceconquest.engine.economy.SystemEconomy;
import com.spaceconquest.engine.economy.SystemEconomyProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SystemEconomyProcessorTest {

    private SystemEconomyProcessor processor;

    @BeforeEach
    public void setUp() {
        processor = new SystemEconomyProcessor();
    }

    @Test
    public void testDefaultSystemEconomyCreation() {
        SystemEconomy economy = SystemEconomy.createDefault("sol", "terran_confederation", 10_000_000_000L);
        assertNotNull(economy);
        assertEquals("sol", economy.systemId());
        assertEquals("terran_confederation", economy.empireId());
        assertEquals(0.20, economy.educationAllocation(), 0.001);
        assertEquals(0.20, economy.lawAndOrderAllocation(), 0.001);
        assertEquals(0.20, economy.healthAndWelfareAllocation(), 0.001);
        assertEquals(0.20, economy.infrastructureAllocation(), 0.001);
        assertEquals(0.20, economy.planetaryMilitiasAllocation(), 0.001);
        assertTrue(economy.totalBudgetCredits() > 0);
        assertEquals(5000.0, economy.accumulatedMilitiaInvestment(), 0.001);
        assertTrue(economy.employedTeachers() > 0);
        assertTrue(economy.employedPolice() > 0);
        assertTrue(economy.employedSoldiers() > 0);
    }

    @Test
    public void testSectorEfficiencyIndexCalculations() {
        long pop = 1_000_000L;
        // Standard budget = pop * 0.002 = 2000 credits
        // 20% sector allocation = 400 credits -> per capita = 400 / 1,000,000 = 0.0004 -> index = 1.0
        double standardIndex = processor.calculateSectorEfficiency(2000.0, 0.20, pop);
        assertEquals(1.0, standardIndex, 0.001);

        // Austerity: half budget (1000 credits) -> index = 0.5
        double austerityIndex = processor.calculateSectorEfficiency(1000.0, 0.20, pop);
        assertEquals(0.5, austerityIndex, 0.001);

        // High investment: double allocation (40%) -> index = 2.0
        double highIndex = processor.calculateSectorEfficiency(2000.0, 0.40, pop);
        assertEquals(2.0, highIndex, 0.001);

        // Clamping to maximum 3.0
        double extremeIndex = processor.calculateSectorEfficiency(50000.0, 0.80, pop);
        assertEquals(3.0, extremeIndex, 0.001);
    }

    @Test
    public void testMilitiaCombatEfficiency() {
        // Zero investment -> 0.30 floor
        assertEquals(0.30, processor.calculateMilitiaCombatEfficiency(0.0), 0.001);

        // 5000 investment -> 0.60
        assertEquals(0.60, processor.calculateMilitiaCombatEfficiency(5000.0), 0.001);

        // 10000+ investment -> 0.90 cap
        assertEquals(0.90, processor.calculateMilitiaCombatEfficiency(10000.0), 0.001);
        assertEquals(0.90, processor.calculateMilitiaCombatEfficiency(25000.0), 0.001);
    }

    @Test
    public void testTurnProcessingAndMilitiaInvestmentDecay() {
        long pop = 5_000_000L;
        SystemEconomy initial = new SystemEconomy(
                "sol", "terran_confederation",
                0.20, 0.20, 0.20, 0.20, 0.20,
                10000.0, // standard budget
                6000.0,  // initial militia investment
                1.0, 1.0, 1.0, 1.0, 1.0,
                2500, 1500, 4000, 2000, 5000, 7500, 6000, 25000, 0.10
        );

        SystemEconomy updated = processor.processSystemEconomy(initial, pop, false);
        assertNotNull(updated);

        // 6000 * 0.95 + (10000 * 0.20) = 5700 + 2000 = 7700
        assertEquals(7700.0, updated.accumulatedMilitiaInvestment(), 0.001);
        assertEquals(1.0, updated.educationLevel(), 0.001);
        assertEquals(1.0, updated.planetaryMilitiaLevel(), 0.001);

        assertTrue(updated.employedTeachers() >= 2500);
        assertTrue(updated.employedPolice() >= 4000);
        assertTrue(updated.employedSoldiers() >= 6000);
    }

    @Test
    public void testHappinessModifierCalculation() {
        // Balanced baseline 1.0 across all 5 sectors -> net 0.0 happiness modifier
        SystemEconomy balanced = SystemEconomy.createDefault("sol", "terran_confederation", 1_000_000L);
        assertEquals(0.0, processor.calculateHappinessModifier(balanced), 0.001);

        // Elevated funding (all sectors at 1.5) -> (0.5 * 5) * 0.05 = +0.125 (+12.5% happiness)
        SystemEconomy elevated = new SystemEconomy(
                "sol", "terran_confederation",
                0.20, 0.20, 0.20, 0.20, 0.20,
                3000.0, 5000.0,
                1.5, 1.5, 1.5, 1.5, 1.5,
                100, 100, 100, 100, 100, 100, 100, 500, 0.10
        );
        assertEquals(0.125, processor.calculateHappinessModifier(elevated), 0.001);

        // Underfunded austerity (all sectors at 0.5) -> (-0.5 * 5) * 0.05 = -0.125 (-12.5% happiness)
        SystemEconomy austerity = new SystemEconomy(
                "sol", "terran_confederation",
                0.20, 0.20, 0.20, 0.20, 0.20,
                1000.0, 5000.0,
                0.5, 0.5, 0.5, 0.5, 0.5,
                100, 100, 100, 100, 100, 100, 100, 500, 0.10
        );
        assertEquals(-0.125, processor.calculateHappinessModifier(austerity), 0.001);
    }

    @Test
    public void testHiveMindSocietyExemption() {
        SystemEconomy hiveEco = new SystemEconomy(
                "hive_cluster", "hive_mind_empire",
                0.50, 0.10, 0.10, 0.10, 0.20,
                10000.0, 5000.0,
                1.5, 0.5, 0.5, 0.5, 1.0,
                100, 100, 100, 100, 100, 100, 100, 500, 0.0
        );

        SystemEconomy result = processor.processSystemEconomy(hiveEco, 2_000_000L, true);
        assertNotNull(result);
        assertEquals(0.0, result.totalBudgetCredits(), 0.001, "Hive Minds bypass currency budgets");
        assertEquals(1.0, result.educationLevel(), 0.001, "Hive Minds maintain uniform 1.0 baseline efficiency");
        assertEquals(1.0, result.lawAndOrderLevel(), 0.001);
        assertEquals(1.0, result.healthAndWelfareLevel(), 0.001);
        assertEquals(1.0, result.infrastructureLevel(), 0.001);
        assertEquals(1.0, result.planetaryMilitiaLevel(), 0.001);
    }

    @Test
    public void testProcessSystemEconomiesGameStateIntegration() {
        Planet earth = new Planet(
                "earth", "Earth", "Homeworld", 5.97e24, 9.81, 1.0, 0.0, 12742.0, "TERRESTRIAL",
                "BREATHABLE", true, 0.70, List.of("iron_ore"), List.of(),
                List.of(new Population("human", Map.of(25, 1_000_000L)))
        );
        SolarSystem sol = new SolarSystem("sol", "Sol", "G-Type", 0.0, 0.0, 0.0, 1.0, 1392700.0, "#fff5f0", List.of(earth), List.of());

        Empire empire = new Empire(
                "terran_confederation", "Terran Confederation", "human", "Individualist",
                50_000.0, 0.15, List.of("sol"), List.of(), Map.of(), List.of(), List.of()
        );

        SystemEconomy economy = SystemEconomy.createDefault("sol", "terran_confederation", 1_000_000L);

        GameState state = GameState.builder()
                .turn(1)
                .status("RUNNING")
                .solarSystems(List.of(sol))
                .empires(List.of(empire))
                .systemEconomies(List.of(economy))
                .build();

        SystemEconomyProcessor.SystemEconomyTurnResult result = processor.processSystemEconomies(state);
        assertNotNull(result);
        assertEquals(1, result.updatedEconomies().size());
        assertEquals(1, result.updatedEmpires().size());

        Empire updatedEmpire = result.updatedEmpires().get(0);
        assertEquals(50_000.0, updatedEmpire.treasuryCredits(), 0.001,
                "Budget spending is recorded by municipal balance sheets, not deducted from the empire here");
    }
}
