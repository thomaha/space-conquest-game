package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.technology.ApplicationProduction;
import com.spaceconquest.engine.technology.ApplicationOptimization;
import com.spaceconquest.engine.technology.ApplicationRefinement;
import com.spaceconquest.engine.technology.ResearchVarianceResult;

import java.io.IOException;

/** Accepts a completed application improvement or discards its prototype. */
public record ResolveApplicationResearchCommand(String empireId, String applicationId, boolean accept)
        implements GameCommand {
    @Override
    public boolean validate(GameState state) {
        if (state == null || empireId == null || applicationId == null) return false;
        var empire = state.empires().stream().filter(value -> empireId.equals(value.id())).findFirst().orElse(null);
        try {
            if (!ApplicationProduction.canOptimize(empire, applicationId, DataModelLoader.loadTechnologies())) return false;
        } catch (IOException e) {
            return false;
        }
        ApplicationOptimization development = ApplicationRefinement.find(state.applicationOptimizations(), empireId, applicationId);
        return development != null && development.pendingOutcome() != null
                && (!accept || !ResearchVarianceResult.OPTIMIZED_SUCCESS.equals(development.pendingOutcome().outcomeType()));
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) return state;
        ApplicationOptimization development = ApplicationRefinement.find(state.applicationOptimizations(), empireId, applicationId);
        ApplicationOptimization resolved = accept ? ApplicationRefinement.resolve(development, development.pendingOutcome(), "RESEARCH")
                : new ApplicationOptimization(empireId, applicationId, development.pathChoice(), development.result(),
                        null, development.completedRefinements() + 1);
        return state.withApplicationOptimizations(ApplicationRefinement.replace(state.applicationOptimizations(), resolved));
    }
}
