package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.technology.ResearchProcessor;
import com.spaceconquest.engine.technology.ResearchVarianceResult;

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
        boolean empireValid = state.empires().stream().anyMatch(e -> e.id().equals(empireId));
        boolean pathValid = "PATH_A".equalsIgnoreCase(pathChoice)
                || "PATH_B".equalsIgnoreCase(pathChoice)
                || "PERFORMANCE".equalsIgnoreCase(pathChoice)
                || "MINIATURIZATION".equalsIgnoreCase(pathChoice);
        return empireValid && pathValid;
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        ResearchProcessor processor = new ResearchProcessor();
        ResearchVarianceResult result = processor.evaluateOptimizationPath(pathChoice, 1.0);

        // Record the optimization outcome in imperial research log or state if needed
        return state;
    }
}
