package com.spaceconquest.control;

import com.spaceconquest.control.ai.EmpireResearchPlanner;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.technology.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ResearchHistoryTest {
    @TempDir Path directory;
    private GameState world() {
        return new ResearchLifecycleTest().world().withIndustrialFacilities(List.of(new IndustrialFacility("solar", "research-world", "solar_power", "research",
                IndustrialFacility.PUBLIC_STATE, 2, 100, "technician", false, 0)))
                .withPowerGrids(List.of(new PowerGridState("research-world", 6000, 7000, -1000, 0, 0, true)));
    }
    @Test void acceptedAIChoicesRetainTheirReasonAcrossReassignmentAndRejectedCommandsWriteNothing() {
        var state = world(); var commands = EmpireResearchPlanner.plan(state, state.empires().getFirst());
        assertTrue(state.researchHistory().isEmpty()); var queue = new CommandQueue(); commands.forEach(queue::submit);
        state = queue.drainAndExecute(state); var project = state.researchProjects().getFirst();
        assertEquals(ResearchDecision.Actor.AI, project.decision().actor()); assertTrue(project.decision().reason().contains("power deficit"));
        assertEquals(ResearchEvent.Kind.STARTED, state.researchHistory().getFirst().kind());
        var rejected = new StartResearchCommand("research", "unknown", true, 5);
        assertSame(state, rejected.apply(state));
        state = new StartResearchCommand("research", "solar_power", true, 0).apply(state);
        assertEquals(project.decision(), state.researchProjects().getFirst().decision());
        assertEquals(ResearchEvent.Kind.PAUSED, state.researchHistory().getLast().kind());
        state = new StartResearchCommand("research", "solar_power", true, 3).apply(state);
        assertEquals(ResearchEvent.Kind.RESUMED, state.researchHistory().getLast().kind());
        assertSame(state, new StartResearchCommand("research", "solar_power", true, 3).apply(state));
        state = new CancelResearchCommand("research", state.researchProjects().getFirst().id()).apply(state);
        assertEquals(ResearchEvent.Kind.CANCELLED, state.researchHistory().getLast().kind());
    }
    @Test void completionAndOutcomeDecisionsAreActualOnceOnlyReceiptsAndSurviveDiskReload() throws Exception {
        var state = world(); var queue = new CommandQueue(); EmpireResearchPlanner.plan(state, state.empires().getFirst()).forEach(queue::submit);
        state = queue.drainAndExecute(state); var original = state.researchProjects().getFirst();
        // Restore a fixture with previously earned points immediately before completion.
        state = state.withResearchProjects(List.of(new ResearchProject(original.id(), "research", "solar_power", true, 499, 500, 5, 1, original.decision())));
        var tick = new ResearchTickProcessor().process(state, List.of(), new ResearchProcessor(new Random() { @Override public double nextDouble() { return .1; } }));
        state = state.toBuilder().empires(tick.empires()).researchProjects(tick.projects()).applicationOptimizations(tick.optimizations()).researchHistory(tick.history()).build();
        assertTrue(state.researchProjects().isEmpty()); assertEquals(ResearchEvent.Kind.COMPLETED, state.researchHistory().getLast().kind());
        EmpireResearchPlanner.plan(state, state.empires().getFirst()).forEach(queue::submit); state = queue.drainAndExecute(state);
        assertEquals(1, state.researchHistory().stream().filter(event -> event.kind() == ResearchEvent.Kind.OPTIMIZED).count());
        var optimized = state.researchHistory().stream().filter(event -> event.kind() == ResearchEvent.Kind.OPTIMIZED).findFirst().orElseThrow();
        assertEquals(ResearchDecision.Actor.AI, optimized.decision().actor()); assertTrue(optimized.decision().reason().contains("capacity"));
        assertFalse(new SelectOptimizationPathCommand("research", "solar_power", "PATH_A").validate(state));
        var manager = new SaveGameManager(directory); var file = directory.resolve("research-history.scsave").toFile(); manager.save(file, state, 1, GameClock.START_TIME.toString());
        var loaded = manager.load(file).toGameState(state.turn(), state.status()); assertEquals(state.researchHistory(), loaded.researchHistory());
        assertEquals(state.researchProjects(), loaded.researchProjects());
        var engine = new SpaceConquestEngine(); engine.applyGameState(loaded); assertEquals(loaded.researchHistory(), engine.getGameState().researchHistory());
    }
    @Test void historyIsBoundedPerEmpireImmutableAndPreservesActiveDecisionEvenAfterEviction() throws Exception {
        var state = new StartResearchCommand("research", "solar_power", true, 5, new ResearchDecision(ResearchDecision.Actor.AI, "Original power choice.", "solar_power")).apply(world());
        var other = new ResearchEvent(0, "other", null, "solar_power", ResearchEvent.Kind.ACCEPTED, new ResearchDecision(ResearchDecision.Actor.PLAYER, "Other empire.", null), null);
        state = ResearchHistory.record(state, other);
        for (int index = 0; index < 40; index++) state = ResearchHistory.record(state, new ResearchEvent(index, "research", null, "solar_power", ResearchEvent.Kind.ACCEPTED,
                new ResearchDecision(ResearchDecision.Actor.PLAYER, "Decision " + index, "solar_power"), null));
        assertEquals(33, state.researchHistory().size()); assertTrue(state.researchHistory().contains(other));
        assertEquals(32, state.researchHistory().stream().filter(event -> event.empireId().equals("research")).count());
        final var immutable = state; assertThrows(UnsupportedOperationException.class, () -> immutable.researchHistory().clear());
        var manager = new SaveGameManager(directory); var file = directory.resolve("bounded.scsave").toFile(); manager.save(file, state, 1, GameClock.START_TIME.toString());
        assertEquals("Original power choice.", manager.load(file).researchProjects().getFirst().decision().reason());
    }
    @Test void actualAcceptedAndDiscardedOutcomesCannotBeRecordedTwice() {
        var state = world(); var project = new ResearchProject("done", "research", "solar_power", true, 500, 500, 5, 1);
        state = state.withApplicationOptimizations(ApplicationRefinement.completed(List.of(), project, new ResearchProcessor().rollBreakthrough(.01)));
        var accept = (ResolveApplicationResearchCommand) EmpireResearchPlanner.plan(state, state.empires().getFirst()).stream()
                .filter(command -> command instanceof ResolveApplicationResearchCommand).findFirst().orElseThrow();
        state = accept.apply(state); assertEquals(ResearchEvent.Kind.ACCEPTED, state.researchHistory().getLast().kind());
        assertSame(state, accept.apply(state)); var accepted = state.applicationOptimizations().getFirst().result();
        state = state.withApplicationOptimizations(ApplicationRefinement.completed(state.applicationOptimizations(), project, new ResearchProcessor().rollBreakthrough(.95)));
        var discard = (ResolveApplicationResearchCommand) EmpireResearchPlanner.plan(state, state.empires().getFirst()).stream()
                .filter(command -> command instanceof ResolveApplicationResearchCommand).findFirst().orElseThrow();
        state = discard.apply(state); assertEquals(ResearchEvent.Kind.DISCARDED, state.researchHistory().getLast().kind());
        assertTrue(state.researchHistory().getLast().decision().reason().contains("flawed"));
        assertEquals(accepted, state.applicationOptimizations().getFirst().result()); assertSame(state, discard.apply(state));
    }
}
