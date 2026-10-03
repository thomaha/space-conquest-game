package com.spaceconquest.control;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spaceconquest.control.command.ResolveApplicationResearchCommand;
import com.spaceconquest.control.command.SelectOptimizationPathCommand;
import com.spaceconquest.control.command.StartResearchCommand;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.SaveGameManager;
import com.spaceconquest.engine.SpaceConquestEngine;
import com.spaceconquest.engine.economy.SystemEconomy;
import com.spaceconquest.engine.technology.ApplicationOptimization;
import com.spaceconquest.engine.technology.ApplicationProduction;
import com.spaceconquest.engine.technology.ApplicationRefinement;
import com.spaceconquest.engine.technology.ResearchProcessor;
import com.spaceconquest.engine.technology.ResearchProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationRefinementIntegrationTest {
    @TempDir Path tempDir;

    @Test
    void refinementRequiresRealScientistsAndRejectsOverAllocation() {
        var start = new StartResearchCommand("empire", "solar_power", true, 5);
        assertFalse(start.validate(state().withSystemEconomies(List.of())));
        assertFalse(new StartResearchCommand("empire", "solar_power", true, 31).validate(state()));
        assertFalse(new StartResearchCommand("empire", "electricity", false, 5).validate(state()));
        assertFalse(new StartResearchCommand("empire", "unknown_app", true, 5).validate(state()));
        assertFalse(new StartResearchCommand("empire", "fission_reactors", true, 5).validate(state()));
        assertFalse(new StartResearchCommand("empire", "nuclear_fusion", false, 5).validate(state()));
    }

    @Test
    void successivePaidRefinementsAccumulateAndCannotReuseACompletedOutcome() {
        GameState state = state();
        for (int cycle = 1; cycle <= 3; cycle++) {
            var start = new StartResearchCommand("empire", "solar_power", true, 5);
            assertTrue(start.validate(state));
            GameState researching = start.apply(state);
            assertEquals(500.0, researching.researchProjects().getFirst().requiredPoints());
            state = complete(researching.withResearchProjects(List.of()), 0.1);
            assertFalse(start.validate(state));
            assertFalse(new ResolveApplicationResearchCommand("empire", "solar_power", true).validate(state));
            var choose = new SelectOptimizationPathCommand("empire", "solar_power", "PATH_B");
            assertTrue(choose.validate(state));
            state = choose.apply(state);
            assertFalse(choose.validate(state));
            assertEquals(cycle, state.applicationOptimizations().getFirst().completedRefinements());
            assertEquals(Math.pow(0.85, cycle), ApplicationProduction.modifiers(state, "empire", "solar_power")
                    .costMultiplier(), 0.000001);
            assertEquals(Math.max(-2, -cycle), ApplicationProduction.modifiers(state, "empire", "solar_power").complexityShift());
        }
        state = new SelectOptimizationPathCommand("empire", "solar_power", "PATH_A").apply(complete(state, 0.1));
        assertEquals(-1, ApplicationProduction.modifiers(state, "empire", "solar_power").complexityShift(),
                "Performance must raise complexity after miniaturization reaches its floor");
    }

    @Test
    void nonOptimizedOutcomesRequireAcceptanceAndFlawedPrototypesCanBeDiscarded() {
        GameState pending = complete(state(), 0.95);
        assertFalse(new SelectOptimizationPathCommand("empire", "solar_power", "PATH_A").validate(pending));
        assertEquals(1.0, ApplicationProduction.modifiers(pending, "empire", "solar_power").effectMultiplier());
        var accept = new ResolveApplicationResearchCommand("empire", "solar_power", true);
        GameState accepted = accept.apply(pending);
        assertEquals(1.05, ApplicationProduction.modifiers(accepted, "empire", "solar_power").effectMultiplier());
        assertEquals(1.25, ApplicationProduction.modifiers(accepted, "empire", "solar_power").costMultiplier());
        assertSame(accepted, accept.apply(accepted));
        GameState another = complete(accepted, 0.95);
        GameState discarded = new ResolveApplicationResearchCommand("empire", "solar_power", false).apply(another);
        assertEquals(accepted.applicationOptimizations().getFirst().result(), discarded.applicationOptimizations().getFirst().result());
        assertNull(discarded.applicationOptimizations().getFirst().pendingOutcome());
        assertTrue(new StartResearchCommand("empire", "solar_power", true, 5).validate(discarded));
    }

    @Test
    void pendingOutcomeAndAccumulatedModifiersSurviveSaveLoadAndLegacyJson() throws Exception {
        GameState first = new ResolveApplicationResearchCommand("empire", "solar_power", true)
                .apply(complete(state(), 0.01));
        GameState pending = complete(first, 0.8);
        var file = tempDir.resolve("research.scsave").toFile();
        SaveGameManager manager = new SaveGameManager();
        manager.save(file, pending, 1, "2026-01-01T00:00:00");
        GameState loaded = manager.load(file).toGameState(0, "RUNNING");
        assertEquals(pending.applicationOptimizations(), loaded.applicationOptimizations());
        GameState accepted = new ResolveApplicationResearchCommand("empire", "solar_power", true).apply(loaded);
        assertEquals(1.20 * 1.10, accepted.applicationOptimizations().getFirst().result().effectMultiplier(), 0.000001);
        ObjectMapper mapper = new ObjectMapper();
        var legacy = mapper.valueToTree(new ApplicationOptimization("empire", "solar_power", "PATH_B",
                new ResearchProcessor().evaluateOptimizationPath("PATH_B", 1.0)));
        ((com.fasterxml.jackson.databind.node.ObjectNode) legacy).remove(List.of("pendingOutcome", "completedRefinements"));
        var restored = mapper.treeToValue(legacy, ApplicationOptimization.class);
        assertTrue(restored.canChoosePath());
        assertEquals(0.85, restored.result().costMultiplier());
    }

    @Test
    void liveTickStoresApplicationOutcomeAndUnlocksTheApplicationOnlyOnce() {
        GameState state = state().withResearchProjects(List.of(
                new ResearchProject("project", "empire", "solar_power", true, 500, 500, 5, 1)));
        SpaceConquestEngine engine = new SpaceConquestEngine();
        engine.applyGameState(state);
        engine.stepTurn();
        GameState completed = engine.getGameState();
        assertTrue(completed.researchProjects().isEmpty());
        assertNotNull(completed.applicationOptimizations().getFirst().pendingOutcome());
        engine.stepTurn();
        assertEquals(completed.applicationOptimizations(), engine.getGameState().applicationOptimizations());
        assertEquals(1, engine.getGameState().empires().getFirst().unlockedTechIds().stream()
                .filter("solar_power"::equals).count());
    }

    private GameState complete(GameState state, double roll) {
        return state.withApplicationOptimizations(ApplicationRefinement.completed(state.applicationOptimizations(),
                new ResearchProject("project", "empire", "solar_power", true, 500, 500, 5, 1),
                new ResearchProcessor().rollBreakthrough(roll)));
    }

    private GameState state() {
        return GameState.builder().empires(List.of(new Empire("empire", "Empire", "human", "Individualist",
                100_000, 0, List.of(), List.of(), Map.of(), List.of("electricity", "solar_power"), List.of())))
                .systemEconomies(List.of(SystemEconomy.createDefault("sol", "empire", 100_000))).build();
    }
}
