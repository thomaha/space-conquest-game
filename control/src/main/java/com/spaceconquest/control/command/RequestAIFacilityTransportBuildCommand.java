package com.spaceconquest.control.command;

import com.spaceconquest.control.ai.TransportConstructionPolicy;
import com.spaceconquest.engine.GameState;

/** Rechecks current unmet need, affiliated capacity, researched ownership and funded construction on the command tick. */
public record RequestAIFacilityTransportBuildCommand(String ownerEntityId, String designId, String systemId,
        String hubId, String materialId, long firstShortageTurn) implements GameCommand {
    private FacilityTransportBuildQuote quote(GameState state) {
        if (state == null || ownerEntityId == null || designId == null || systemId == null || hubId == null || materialId == null
                || firstShortageTurn < 0 || state.turn() < firstShortageTurn
                || state.turn() - firstShortageTurn + 1 < TransportConstructionPolicy.current().observationTurns()) return null;
        return FacilityTransportBuildQuote.quote(state, ownerEntityId, designId, systemId, hubId, materialId);
    }
    @Override public boolean validate(GameState state) { return quote(state) != null; }
    @Override public GameState apply(GameState state) {
        var quote = quote(state);
        if (quote == null) return state;
        var built = quote.build().apply(quote.registered());
        var orders = new java.util.ArrayList<>(built.shipConstructionOrders()); var order = orders.getLast();
        orders.set(orders.size() - 1, order.withDeployment(new com.spaceconquest.engine.ship.ShipDeploymentIntent(ownerEntityId,
                com.spaceconquest.engine.ship.ShipDeploymentIntent.Kind.SUPPLY, hubId, materialId, firstShortageTurn, "Planned facility supply")));
        return built.withShipConstructionOrders(orders);
    }
}
