package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;

/** Either participant may end a sharing route without refunding already earned progress. */
public record RevokeTechnologyExchangeCommand(String empireId, String routeId) implements GameCommand {
    public boolean validate(GameState state) {
        return state != null && empireId != null && routeId != null && state.empires().stream().anyMatch(empire -> empireId.equals(empire.id()))
                && state.technologyExchangeRoutes().stream().anyMatch(route -> routeId.equals(route.routeId())
                && (empireId.equals(route.senderEmpireId()) || empireId.equals(route.receiverEmpireId())));
    }
    public GameState apply(GameState state) {
        return !validate(state) ? state : state.withTechnologyExchangeRoutes(state.technologyExchangeRoutes().stream()
                .filter(route -> !routeId.equals(route.routeId())).toList());
    }
}
