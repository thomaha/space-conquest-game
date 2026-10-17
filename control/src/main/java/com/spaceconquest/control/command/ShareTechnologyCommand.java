package com.spaceconquest.control.command;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.technology.*;
import java.io.IOException;
import java.util.*;

/** Source-authorized insights under an existing alliance; grants no immediate unlock. */
public record ShareTechnologyCommand(String senderEmpireId, String receiverEmpireId, String targetId) implements GameCommand {
    private TechnologyExchangeRoute route() {
        var policy = ResearchPolicy.current();
        return new TechnologyExchangeRoute("exchange/" + senderEmpireId + "/" + receiverEmpireId + "/" + targetId,
                senderEmpireId, receiverEmpireId, targetId, policy.exchangeProgressFraction(), policy.exchangeSpeedBonus());
    }
    public boolean validate(GameState state) {
        if (state == null || senderEmpireId == null || receiverEmpireId == null || targetId == null) return false;
        if (state.technologyExchangeRoutes().stream().anyMatch(value -> senderEmpireId.equals(value.senderEmpireId())
                && receiverEmpireId.equals(value.receiverEmpireId()) && targetId.equals(value.technologyId()))) return false;
        try { return ResearchExchange.eligible(state, route(), DataModelLoader.loadTechnologies()); }
        catch (IOException failure) { return false; }
    }
    public GameState apply(GameState state) {
        if (!validate(state)) return state;
        var routes = new ArrayList<>(state.technologyExchangeRoutes()); routes.add(route());
        return state.withTechnologyExchangeRoutes(routes);
    }
}
