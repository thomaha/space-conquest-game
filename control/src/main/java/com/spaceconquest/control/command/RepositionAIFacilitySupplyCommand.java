package com.spaceconquest.control.command;

import com.spaceconquest.control.ai.*;
import com.spaceconquest.engine.*;
import java.util.*;

/** Revalidates shortage, source claims, paid empty travel and prospective supply before departure. */
public record RepositionAIFacilitySupplyCommand(String empireId, String hubId, String materialId, String shipId, String sourceId, long firstShortageTurn) implements GameCommand {
    private FacilitySupplyReposition.Quote quote(GameState state, String routeId) {
        if (state == null || empireId == null || hubId == null || materialId == null || shipId == null || sourceId == null || firstShortageTurn < 0) return null;
        var policy = LogisticsPolicy.current();
        if (state.turn() < firstShortageTurn || state.turn() - firstShortageTurn + 1 < policy.shortageObservationTurns()) return null;
        var empire = com.spaceconquest.engine.economy.EconomicOwnership.empireFor(state, empireId); if (empire == null || !com.spaceconquest.engine.economy.EconomicOwnership.transportOperator(state, empire, empireId)) return null;
        if (state.fleets().stream().noneMatch(fleet -> empireId.equals(fleet.ownerEntityId()) && fleet.ships().stream().anyMatch(ship -> shipId.equals(ship.id())))) return null;
        var need = FacilitySupplyNeeds.shortages(state, empire, policy).stream().filter(item -> hubId.equals(item.hubId()) && materialId.equals(item.materialId())).findFirst().orElse(null);
        return need == null ? null : FacilitySupplyReposition.quote(state, empire, need, shipId, sourceId, routeId, policy);
    }
    @Override public boolean validate(GameState state) { return quote(state, "ai_supply_preview") != null; }
    @Override public GameState apply(GameState state) {
        var preview = quote(state, "ai_supply_" + UUID.randomUUID()); if (preview == null) return state;
        var routes = new ArrayList<>(preview.state().tradeRoutes()); routes.add(preview.route());
        return preview.state().withTradeRoutes(routes);
    }
}
