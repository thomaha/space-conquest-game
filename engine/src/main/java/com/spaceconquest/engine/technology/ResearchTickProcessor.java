package com.spaceconquest.engine.technology;

import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Race;
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
                         List<ApplicationOptimization> optimizations) {
        public Result {
            empires = List.copyOf(empires);
            projects = List.copyOf(projects);
            optimizations = List.copyOf(optimizations);
        }
    }

    public Result process(GameState state, List<Race> races, ResearchProcessor processor) {
        List<ResearchProject> projects = new ArrayList<>();
        List<ApplicationOptimization> optimizations = state.applicationOptimizations();
        Map<String, List<String>> unlocks = new HashMap<>();
        for (ResearchProject project : state.researchProjects()) {
            Empire empire = state.empires().stream().filter(value -> value.id().equals(project.empireId()))
                    .findFirst().orElse(null);
            if (empire == null) continue;
            Race race = races.stream().filter(value -> value.id().equalsIgnoreCase(empire.raceId()))
                    .findFirst().orElse(null);
            ResearchProject advanced = processor.advanceProject(project, race, empire, state.technologyExchangeRoutes());
            if (!advanced.isComplete()) {
                projects.add(advanced);
                continue;
            }
            ResearchVarianceResult outcome = processor.rollBreakthrough();
            if (project.isApplication()) optimizations = ApplicationRefinement.completed(optimizations, project, outcome);
            unlocks.computeIfAbsent(empire.id(), ignored -> new ArrayList<>()).add(project.targetTechOrAppId());
            logger.info("Research complete for empire {} on {}: outcome {}", empire.id(),
                    project.targetTechOrAppId(), outcome.outcomeType());
        }
        List<Empire> empires = state.empires().stream().map(empire -> unlock(empire, unlocks.get(empire.id()))).toList();
        return new Result(empires, projects, optimizations);
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
