package com.spaceconquest.control.command;

import com.spaceconquest.control.ai.*;
import com.spaceconquest.engine.GameState;
import java.util.Objects;

/** Recomputes a slider decision on the command tick while protecting intervening settings changes. */
public record AdjustAISystemEconomyCommand(String empireId, String systemId,
        SetSystemEconomyBudgetCommand expectedSettings) implements GameCommand {
    @Override public boolean validate(GameState state) {
        if (state == null || empireId == null || systemId == null) return false;
        var current = state.systemEconomies().stream().filter(value -> systemId.equals(value.systemId())).findFirst().orElse(null);
        if (!Objects.equals(expectedSettings, AISystemEconomyPlan.settings(current))) return false;
        var quote = AISystemEconomyPlan.quote(state, empireId, systemId, EconomicPolicy.current());
        return quote != null && quote.proposed().validate(state);
    }
    @Override public GameState apply(GameState state) {
        if (!validate(state)) return state;
        return AISystemEconomyPlan.quote(state, empireId, systemId, EconomicPolicy.current()).proposed().apply(state);
    }
}
