package com.spaceconquest.control.ai;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.EconomicOwnership;
import java.util.*;

public final class AIShipDeploymentPlanner {
    private AIShipDeploymentPlanner() {}
    public static List<GameCommand> plan(GameState state, Empire empire) {
        var entries = state.shipyardLedger().deployments().entrySet().stream().filter(entry -> {
            var home = EconomicOwnership.empireFor(state, entry.getValue().ownerId()); return home != null && empire.id().equals(home.id());
        }).sorted(Map.Entry.comparingByKey()).toList();
        if (entries.isEmpty()) return List.of();
        var result = new ArrayList<GameCommand>(); int start = (int) (state.turn() % entries.size());
        for (int offset = 0; offset < Math.min(entries.size(), DeploymentPolicy.current().maximumRequestsPerTurn()); offset++) {
            var entry = entries.get((start + offset) % entries.size()); var command = new DeployAIShipCommand(entry.getValue().ownerId(), entry.getKey());
            if (command.validate(state)) result.add(command);
        }
        return List.copyOf(result);
    }
}
