package com.spaceconquest.control.command;

import com.spaceconquest.control.ai.*;
import com.spaceconquest.engine.*;
import java.util.*;

/** Commits a freshly revalidated paid supply shipment, with a protected return and no automatic second cargo load. */
public record RequestAIFacilitySupplyCommand(String empireId, String hubId, String materialId, String shipId, long firstShortageTurn) implements GameCommand {
    private FacilitySupplyShipment quote(GameState state, String routeId) {
        if (state == null || empireId == null || hubId == null || materialId == null || shipId == null || firstShortageTurn < 0) return null;
        var policy = LogisticsPolicy.current();
        if (state.turn() < firstShortageTurn || state.turn() - firstShortageTurn + 1 < policy.shortageObservationTurns()) return null;
        var empire = com.spaceconquest.engine.economy.EconomicOwnership.empireFor(state, empireId); if (empire == null || !com.spaceconquest.engine.economy.EconomicOwnership.transportOperator(state, empire, empireId)) return null;
        if (state.fleets().stream().noneMatch(fleet -> empireId.equals(fleet.ownerEntityId()) && fleet.ships().stream().anyMatch(ship -> shipId.equals(ship.id())))) return null;
        var need = FacilitySupplyNeeds.shortages(state, empire, policy).stream().filter(value -> hubId.equals(value.hubId()) && materialId.equals(value.materialId())).findFirst().orElse(null);
        return need == null ? null : FacilitySupplyShipment.quote(state, empire, need, shipId, routeId, policy);
    }
    @Override public boolean validate(GameState state) { return quote(state, "ai_supply_preview") != null; }
    @Override public GameState apply(GameState state) {
        var shipment = quote(state, "ai_supply_" + UUID.randomUUID()); if (shipment == null) return state;
        var routes = new ArrayList<>(shipment.state().tradeRoutes()); routes.add(shipment.route());
        return shipment.state().withTradeRoutes(routes);
    }
}
