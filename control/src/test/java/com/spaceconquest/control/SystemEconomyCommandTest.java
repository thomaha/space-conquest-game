package com.spaceconquest.control;

import com.spaceconquest.control.command.SetSystemEconomyBudgetCommand;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.economy.SystemEconomy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SystemEconomyCommandTest {

    private GameState baseState;
    private SolarSystem sol;
    private Empire empire;

    @BeforeEach
    public void setUp() {
        Planet earth = new Planet(
                "earth", "Earth", "Homeworld", 5.97e24, 9.81, 1.0, 0.0, 12742.0, "TERRESTRIAL",
                "BREATHABLE", true, 0.70, List.of("iron_ore"), List.of(),
                List.of(new Population("human", Map.of(25, 2_000_000L)))
        );
        sol = new SolarSystem("sol", "Sol", "G-Type", 0.0, 0.0, 0.0, 1.0, 1392700.0, "#fff5f0", List.of(earth), List.of());

        empire = new Empire(
                "terran_confederation", "Terran Confederation", "human", "Individualist",
                50_000.0, 0.15, List.of("sol"), List.of(), Map.of(), List.of(), List.of()
        );

        SystemEconomy economy = SystemEconomy.createDefault("sol", "terran_confederation", 2_000_000L);

        baseState = new GameState(
                1, "RUNNING", List.of(sol), List.of(empire), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null,
                List.of(), List.of(), List.of(economy), List.of()
        );
    }

    @Test
    public void testCommandValidation() {
        SetSystemEconomyBudgetCommand valid = new SetSystemEconomyBudgetCommand(
                "terran_confederation", "sol", 0.40, 0.15, 0.15, 0.15, 0.15, 5000.0, 0.12
        );
        assertTrue(valid.validate(baseState));

        // Unknown empire
        SetSystemEconomyBudgetCommand invalidEmpire = new SetSystemEconomyBudgetCommand(
                "unknown_empire", "sol", 0.20, 0.20, 0.20, 0.20, 0.20, 2000.0, 0.10
        );
        assertFalse(invalidEmpire.validate(baseState));

        // Negative budget
        SetSystemEconomyBudgetCommand negativeBudget = new SetSystemEconomyBudgetCommand(
                "terran_confederation", "sol", 0.20, 0.20, 0.20, 0.20, 0.20, -500.0, 0.10
        );
        assertFalse(negativeBudget.validate(baseState));

        // System not owned by empire
        SetSystemEconomyBudgetCommand unownedSystem = new SetSystemEconomyBudgetCommand(
                "terran_confederation", "alpha_centauri", 0.20, 0.20, 0.20, 0.20, 0.20, 2000.0, 0.10
        );
        assertFalse(unownedSystem.validate(baseState));
    }

    @Test
    public void testCommandApplyUpdatesAllocationsAndIndices() {
        // Education and Science focus: 40% Edu, 15% Law, 15% Health, 15% Infra, 15% Militia
        SetSystemEconomyBudgetCommand cmd = new SetSystemEconomyBudgetCommand(
                "terran_confederation", "sol", 40.0, 15.0, 15.0, 15.0, 15.0, 4000.0, 0.25
        );

        GameState updatedState = cmd.apply(baseState);
        assertNotNull(updatedState);

        SystemEconomy updatedEconomy = updatedState.systemEconomies().stream()
                .filter(se -> se.systemId().equals("sol"))
                .findFirst()
                .orElse(null);

        assertNotNull(updatedEconomy);
        assertEquals(0.40, updatedEconomy.educationAllocation(), 0.001);
        assertEquals(0.15, updatedEconomy.lawAndOrderAllocation(), 0.001);
        assertEquals(0.15, updatedEconomy.healthAndWelfareAllocation(), 0.001);
        assertEquals(0.15, updatedEconomy.infrastructureAllocation(), 0.001);
        assertEquals(0.15, updatedEconomy.planetaryMilitiasAllocation(), 0.001);
        assertEquals(4000.0, updatedEconomy.totalBudgetCredits(), 0.001);
        assertEquals(0.25, updatedEconomy.taxRate(), 0.001);

        // Indices: Pop = 2,000,000. Budget = 4000. Edu = 4000 * 0.40 = 1600. Per capita = 1600 / 2,000,000 = 0.0008. Baseline = 0.0004 -> index = 2.0
        assertEquals(2.0, updatedEconomy.educationLevel(), 0.001);
        // Law = 4000 * 0.15 = 600. Per capita = 600 / 2,000,000 = 0.0003 -> index = 0.75
        assertEquals(0.75, updatedEconomy.lawAndOrderLevel(), 0.001);
    }
}
