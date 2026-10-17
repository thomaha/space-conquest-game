package com.spaceconquest.control.command;

import com.spaceconquest.control.ai.ShipyardInvestmentQuote;
import com.spaceconquest.engine.GameState;

/** Rechecks paid demand, ownership, reserves and saved pending projects at the simulation tick. */
public record RequestAIShipyardExpansionCommand(String ownerEntityId, String systemId, String yardId) implements GameCommand {
    @Override public boolean validate(GameState state) {
        return state != null && ownerEntityId != null && systemId != null && yardId != null
                && ShipyardInvestmentQuote.evaluate(state, ownerEntityId, systemId, yardId) != null;
    }
    @Override public GameState apply(GameState state) {
        if (state == null || ownerEntityId == null || systemId == null || yardId == null) return state;
        var quote = ShipyardInvestmentQuote.evaluate(state, ownerEntityId, systemId, yardId);
        return quote == null ? state : quote.construction().apply(state);
    }
}
