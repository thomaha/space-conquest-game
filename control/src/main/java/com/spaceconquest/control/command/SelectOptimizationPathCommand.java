package com.spaceconquest.control.command;

import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.technology.ApplicationOptimization;
import com.spaceconquest.engine.technology.ApplicationProduction;
import com.spaceconquest.engine.technology.ApplicationRefinement;
import com.spaceconquest.engine.technology.ResearchProcessor;
import com.spaceconquest.engine.technology.ResearchVarianceResult;

import java.io.IOException;

/**
 * Command to select the optimization development path (Path A: Performance vs Path B: Miniaturization)
 * for a researched technical application.
 */
public record SelectOptimizationPathCommand(
        String empireId,
        String applicationId,
        String pathChoice
) implements GameCommand {

    public static final String PATH_A_PERFORMANCE = "PATH_A";
    public static final String PATH_B_MINIATURIZATION = "PATH_B";

    @Override
    public boolean validate(GameState state) {
        if (state == null || empireId == null || applicationId == null || pathChoice == null) {
            return false;
        }
        Empire empire = state.empires().stream().filter(e -> e.id().equals(empireId)).findFirst().orElse(null);
        boolean pathValid = "PATH_A".equalsIgnoreCase(pathChoice)
                || "PATH_B".equalsIgnoreCase(pathChoice)
                || "PERFORMANCE".equalsIgnoreCase(pathChoice)
                || "MINIATURIZATION".equalsIgnoreCase(pathChoice);
        if (empire == null || !pathValid || applicationId.isBlank()) return false;
        ApplicationOptimization previous = ApplicationRefinement.find(state.applicationOptimizations(), empireId, applicationId);
        if (previous != null && !previous.canChoosePath()) return false;
        try {
            return ApplicationProduction.canOptimize(empire, applicationId, DataModelLoader.loadTechnologies());
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        ResearchProcessor processor = new ResearchProcessor();
        ResearchVarianceResult result = processor.evaluateOptimizationPath(pathChoice, 1.0);
        String canonicalPath = "PATH_A".equalsIgnoreCase(pathChoice)
                || "PERFORMANCE".equalsIgnoreCase(pathChoice)
                ? PATH_A_PERFORMANCE : PATH_B_MINIATURIZATION;
        ApplicationOptimization previous = ApplicationRefinement.find(state.applicationOptimizations(), empireId, applicationId);
        ApplicationOptimization selection = previous == null
                ? new ApplicationOptimization(empireId, applicationId, canonicalPath, result)
                : ApplicationRefinement.resolve(previous, result, canonicalPath);
        return state.withApplicationOptimizations(ApplicationRefinement.replace(state.applicationOptimizations(), selection));
    }
}
