package com.spaceconquest.engine.technology;

import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Race;
import com.spaceconquest.engine.DataModelLoader;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Advances research and retains outcomes during the coordinated simulation tick. */
public final class ResearchTickProcessor {
    private static final Logger logger = LogManager.getLogger(ResearchTickProcessor.class);

    public record Result(List<Empire> empires, List<ResearchProject> projects,
                         List<ApplicationOptimization> optimizations, List<ResearchEvent> history) {
        public Result {
            empires = List.copyOf(empires);
            projects = List.copyOf(projects);
            optimizations = List.copyOf(optimizations);
            history = List.copyOf(history);
        }
    }

    public Result process(GameState state, List<Race> races, ResearchProcessor processor) {
        List<ResearchProject> projects = new ArrayList<>();
        List<ApplicationOptimization> optimizations = state.applicationOptimizations();
        List<ResearchEvent> history = state.researchHistory();
        Map<String, List<String>> unlocks = new HashMap<>();
        List<com.spaceconquest.engine.Technology> catalog;
        try { catalog = DataModelLoader.loadTechnologies(); } catch (IOException failure) { throw new UncheckedIOException(failure); }
        var funding = new HashMap<String, ResearchFunding>();
        for (var empire : state.empires()) funding.put(empire.id(), ResearchFunding.quote(state, empire, races, catalog, processor));
        for (ResearchProject project : state.researchProjects()) {
            Empire empire = state.empires().stream().filter(value -> value.id().equals(project.empireId()))
                    .findFirst().orElse(null);
            if (empire == null) continue;
            if (!ApplicationProduction.canResearch(empire, project.targetTechOrAppId(), project.isApplication(), catalog)) {
                boolean known = catalog.stream().anyMatch(technology -> project.targetTechOrAppId().equals(technology.id()))
                        || catalog.stream().flatMap(technology -> technology.applications().stream())
                        .anyMatch(application -> project.targetTechOrAppId().equals(application.id()));
                if (known) projects.add(project);
                else logger.warn("Discarding unknown research target {}", project.targetTechOrAppId());
                continue;
            }
            var quote = funding.get(empire.id());
            ResearchProject advanced = processor.advanceFundedProject(project, quote.basePoints(project), quote.routes());
            if (!advanced.isComplete()) {
                projects.add(advanced);
                continue;
            }
            ResearchVarianceResult outcome = processor.rollBreakthrough();
            history = ResearchHistory.append(history, new ResearchEvent(state.turn(), empire.id(), project.id(), project.targetTechOrAppId(),
                    ResearchEvent.Kind.COMPLETED, new ResearchDecision(ResearchDecision.Actor.SIMULATION,
                    "Research completed." + (project.decision() == null ? "" : " Original choice: " + project.decision().reason()),
                    project.decision() == null ? null : project.decision().goalApplicationId()), outcome));
            if (project.isApplication()) optimizations = ApplicationRefinement.completed(optimizations, project, outcome);
            unlocks.computeIfAbsent(empire.id(), ignored -> new ArrayList<>()).add(project.targetTechOrAppId());
            logger.info("Research complete for empire {} on {}: outcome {}", empire.id(),
                    project.targetTechOrAppId(), outcome.outcomeType());
        }
        List<Empire> empires = state.empires().stream().map(empire -> unlock(empire, unlocks.get(empire.id()))).toList();
        return new Result(empires, projects, optimizations, history);
    }

    private Empire unlock(Empire empire, List<String> targets) {
        if (targets == null || targets.isEmpty()) return empire;
        List<String> combined = new ArrayList<>(empire.unlockedTechIds());
        for (String target : targets) if (!combined.contains(target)) combined.add(target);
        return new Empire(empire.id(), empire.name(), empire.raceId(), empire.societyStructure(),
                empire.treasuryCredits(), empire.corporateTaxRate(), empire.controlledSystemIds(),
                empire.ministries(), empire.systemGovernorAssignments(), combined, empire.activeShipDesignIds());
    }
}
