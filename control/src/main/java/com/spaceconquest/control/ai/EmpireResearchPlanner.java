package com.spaceconquest.control.ai;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.technology.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

/** Develop useful available applications before expanding their foundational prerequisites. */
public final class EmpireResearchPlanner {
    private EmpireResearchPlanner() {}
    public static List<GameCommand> plan(GameState state, Empire empire) {
        List<Technology> catalog;
        try { catalog = DataModelLoader.loadTechnologies(); } catch (IOException failure) { throw new UncheckedIOException(failure); }
        var commands = new ArrayList<GameCommand>();
        var needs = new EmpireResearchNeeds(state, empire);
        for (var development : state.applicationOptimizations()) {
            if (!empire.id().equals(development.empireId()) || development.pendingOutcome() == null) continue;
            if (!ApplicationProduction.canOptimize(empire, development.applicationId(), catalog)) continue;
            var outcome = development.pendingOutcome();
            if (ResearchVarianceResult.OPTIMIZED_SUCCESS.equals(outcome.outcomeType()))
                catalog.stream().flatMap(technology -> technology.applications().stream()).filter(app -> app.id().equals(development.applicationId())).findFirst()
                        .ifPresent(app -> {
                            String path = needs.optimizationPath(app);
                            commands.add(new SelectOptimizationPathCommand(empire.id(), app.id(), path, new ResearchDecision(ResearchDecision.Actor.AI,
                                    "PATH_A".equals(path) ? "Performance meets an urgent need and fits current factory and yard capacity. " + needs.reason(app)
                                            : "Miniaturization suits general development or avoids exceeding factory or yard capacity. " + needs.reason(app), app.id())));
                        });
            else catalog.stream().flatMap(technology -> technology.applications().stream()).filter(app -> app.id().equals(development.applicationId())).findFirst()
                    .ifPresent(app -> {
                        boolean accept = !ResearchVarianceResult.FLAWED_SETBACK.equals(outcome.outcomeType()) && needs.canApply(app, outcome.complexityShift());
                        String reason = accept ? "Accept improvement within current factory capacity." : ResearchVarianceResult.FLAWED_SETBACK.equals(outcome.outcomeType())
                                ? "Discard flawed prototype." : "Discard improvement that would exceed existing factory capacity.";
                        commands.add(new ResolveApplicationResearchCommand(empire.id(), app.id(), accept, new ResearchDecision(ResearchDecision.Actor.AI, reason, app.id())));
                    });
        }
        long assigned = state.researchProjects().stream().filter(project -> empire.id().equals(project.empireId()) && !project.isComplete())
                .mapToLong(project -> Math.max(0, project.assignedScientists())).sum();
        int count = (int) Math.min(5, Math.max(0, ResearchWorkforce.capacity(state, empire) - assigned));
        if (count == 0) return List.copyOf(commands);
        var paused = state.researchProjects().stream().filter(project -> empire.id().equals(project.empireId()) && project.assignedScientists() == 0
                && !project.isComplete() && ApplicationProduction.canResearch(empire, project.targetTechOrAppId(), project.isApplication(), catalog)).findFirst();
        if (paused.isPresent()) {
            var project = paused.get(); commands.add(new StartResearchCommand(empire.id(), project.targetTechOrAppId(), project.isApplication(), count,
                    new ResearchDecision(ResearchDecision.Actor.AI, "Resume paused work with funded scientists.", project.decision() == null ? null : project.decision().goalApplicationId())));
            return List.copyOf(commands);
        }
        var activeTargets = state.researchProjects().stream().filter(project -> empire.id().equals(project.empireId()))
                .map(ResearchProject::targetTechOrAppId).collect(java.util.stream.Collectors.toSet());
        ResearchOpportunities.choose(state, empire, catalog, activeTargets, needs).ifPresent(candidate ->
                commands.add(new StartResearchCommand(empire.id(), candidate.target(), candidate.application(), count,
                        new ResearchDecision(ResearchDecision.Actor.AI, candidate.reason(), candidate.goalApplicationId()))));
        return List.copyOf(commands);
    }
}
