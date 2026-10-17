package com.spaceconquest.control.command;

import com.spaceconquest.control.ai.SupplyDevelopmentProgress;
import com.spaceconquest.engine.GameState;

/** The simulation tick owns source-plan advancement and every real purchase or movement it commits. */
public record AdvanceSupplyDevelopmentCommand(String empireId, String planId) implements GameCommand {
    private SupplyDevelopmentProgress.Step quote(GameState state) {
        if (state == null || empireId == null || planId == null) return null;
        var plan = state.supplyDevelopmentPlans().stream().filter(p -> p.id().equals(planId) && p.empireId().equals(empireId)).findFirst().orElse(null);
        return plan == null ? null : SupplyDevelopmentProgress.next(state, plan);
    }
    @Override public boolean validate(GameState state) { var step = quote(state); return step != null && step.changed(); }
    @Override public GameState apply(GameState state) { var step = quote(state); return step == null || !step.changed() ? state : step.state(); }
}
