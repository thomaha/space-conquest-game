package com.spaceconquest.control;

import com.spaceconquest.control.ai.EmpireResearchPlanner;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.technology.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ResearchEraProgressionTest {
    @TempDir Path directory;
    @Test void everyStartingEraRetainsUsableDiscoveriesAndMakesFundedProgressAcrossReload() throws Exception {
        var catalog = DataModelLoader.loadTechnologies(); var report = new StringBuilder("# Research era progression\n\n"
                + "Bounded 130-day runs use generated one-system campaigns, normal daily payroll and research commands. "
                + "Generation and breakthrough rolls retain ordinary randomness; this verifies progression rather than balance. "
                + "A version 1 disk checkpoint is reloaded on day 60. Research does not build facilities or retrofit existing ships.\n\n"
                + "| Starting era | Unknown usable applications | Immediately researchable | New discoveries | Resolved improvements |\n| --- | --- | --- | --- | --- |\n");
        for (var era : GameStartScenario.values()) {
            var initial = new GalaxyGenerator().generateGameState(1, 0, era); var empire = initial.empires().getFirst();
            var known = new HashSet<>(empire.unlockedTechIds());
            var remaining = catalog.stream().flatMap(technology -> technology.applications().stream())
                    .filter(app -> !known.contains(app.id()) && ApplicationIntegration.describe(app).status() != ApplicationIntegration.Status.PLANNED).toList();
            long eligible = remaining.stream().filter(app -> ApplicationProduction.canResearch(empire, app.id(), true, catalog)).count();
            assertFalse(remaining.isEmpty(), era.name()); assertTrue(eligible > 0, era.name());
            var engine = new SpaceConquestEngine(42); engine.applyGameState(initial); var queue = new CommandQueue();
            boolean fundedProgress = false;
            for (int day = 0; day < 130; day++) {
                var before = engine.getGameState(); var owner = before.empires().getFirst();
                var commands = EmpireResearchPlanner.plan(before, owner);
                for (var command : commands) {
                    if (command instanceof StartResearchCommand start && start.isApplication()) {
                        var app = catalog.stream().flatMap(technology -> technology.applications().stream())
                                .filter(value -> start.targetTechOrAppId().equals(value.id())).findFirst().orElseThrow();
                        assertNotEquals(ApplicationIntegration.Status.PLANNED, ApplicationIntegration.describe(app).status());
                    }
                    queue.submit(command);
                }
                var queued = queue.drainAndExecute(before); engine.applyGameState(queued); engine.stepTurn();
                var after = engine.getGameState();
                if (after.researchProjects().stream().anyMatch(project -> project.accumulatedPoints() > 0)) fundedProgress = true;
                if (day == 59) {
                    var manager = new SaveGameManager(directory); var file = directory.resolve(era.name() + ".scsave").toFile();
                    manager.save(file, after, 1, era.startTime().plusDays(after.turn()).toString(), era.startTime().toString());
                    var loaded = manager.load(file).toGameState(after.turn(), after.status());
                    assertEquals(after.researchProjects(), loaded.researchProjects()); engine.applyGameState(loaded);
                }
            }
            var end = engine.getGameState();
            long discoveries = remaining.stream().filter(app -> end.empires().getFirst().unlockedTechIds().contains(app.id())).count();
            long improvements = end.applicationOptimizations().stream().filter(value -> value.completedRefinements() > 0
                    && (value.result().effectMultiplier() != 1 || value.result().costMultiplier() != 1 || value.result().complexityShift() != 0)).count();
            assertTrue(fundedProgress, era.name()); assertTrue(discoveries + improvements > 0, era.name() + " must produce usable research");
            report.append("| ").append(era.displayName()).append(" | ").append(remaining.size()).append(" | ").append(eligible)
                    .append(" | ").append(discoveries).append(" | ").append(improvements).append(" |\n");
        }
        Files.createDirectories(Path.of("target")); Files.writeString(Path.of("target/research-era-progression.md"), report);
    }
}
