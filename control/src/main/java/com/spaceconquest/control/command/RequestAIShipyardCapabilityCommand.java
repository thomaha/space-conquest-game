package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import java.util.ArrayList;

/** Stages paid hardware and its waiting ship atomically after fresh demand and research checks. */
public record RequestAIShipyardCapabilityCommand(String providerOwnerId, String designId, String systemId, String yardId) implements GameCommand {
    @Override public boolean validate(GameState state) { return ShipyardCapabilityQuote.evaluate(state, providerOwnerId, designId, systemId, yardId) != null; }
    @Override public GameState apply(GameState state) {
        var quote = ShipyardCapabilityQuote.evaluate(state, providerOwnerId, designId, systemId, yardId);
        if (quote == null) return state;
        var staged = quote.upgrade().apply(state);
        if (staged == state) return state;
        if (quote.newOrder() == null) return staged;
        staged = AIShipResearchRevision.register(staged, quote.design());
        var orders = new ArrayList<>(staged.shipConstructionOrders()); orders.add(quote.newOrder());
        return com.spaceconquest.control.ai.AIShipDeploymentIntent.capture(staged.withShipConstructionOrders(orders), quote.design().ownerEntityId());
    }
}
