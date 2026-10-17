package com.spaceconquest.control.ai;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import java.util.*;

/** Periodically reviews existing economic sliders as well as creating missing system policies. */
public final class EmpireEconomicPlanner {
    private final Map<String, Long> nextReviews = new HashMap<>();
    private long lastTurn = -1;
    public List<GameCommand> plan(GameState state, Empire empire) {
        if (state.turn() < lastTurn) nextReviews.clear(); lastTurn = state.turn();
        nextReviews.keySet().retainAll(empire.controlledSystemIds());
        var commands = new ArrayList<GameCommand>(); var policy = EconomicPolicy.current();
        for (String systemId : empire.controlledSystemIds().stream().sorted().toList()) {
            if (state.turn() < nextReviews.getOrDefault(systemId, -1L)) continue;
            nextReviews.put(systemId, state.turn() + policy.reviewIntervalTurns());
            var quote = AISystemEconomyPlan.quote(state, empire.id(), systemId, policy);
            if (quote != null) commands.add(new AdjustAISystemEconomyCommand(empire.id(), systemId, quote.expected()));
        }
        return List.copyOf(commands);
    }
}
