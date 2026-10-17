package com.spaceconquest.control.command;

import com.spaceconquest.control.ai.ConstructionPriorityPlanner;
import com.spaceconquest.engine.GameState;

/** Recalculates queue decisions at the tick rather than accepting stale UI scores. */
public record PrioritizeAIShipConstructionCommand(String empireId) implements GameCommand {
    @Override public boolean validate(GameState state) {
        return state != null && empireId != null && state.empires().stream().anyMatch(empire -> empireId.equals(empire.id()));
    }
    @Override public GameState apply(GameState state) {
        if (!validate(state)) return state;
        var empire = state.empires().stream().filter(item -> empireId.equals(item.id())).findFirst().orElseThrow();
        var ranked = ConstructionPriorityPlanner.rank(state, empire).stream().map(order -> {
            if (order.deployment() != null || order.priority() == null || !empireId.equals(order.priority().empireId())) return order;
            var design = state.shipDesigns().stream().filter(item -> order.designId().equals(item.id()) && order.ownerEntityId().equals(item.ownerEntityId())).findFirst().orElse(null);
            var home = com.spaceconquest.engine.economy.EconomicOwnership.empireFor(state, order.ownerEntityId());
            if (home == null || !empireId.equals(home.id())) return order;
            return design == null ? order : order.withDeployment(com.spaceconquest.control.ai.AIShipDeploymentIntent.choose(state, design, order.systemId(), order.yardBodyId()));
        }).toList();
        return ranked.equals(state.shipConstructionOrders()) ? state : state.withShipConstructionOrders(ranked);
    }
}
