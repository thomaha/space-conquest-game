package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.*;
import java.util.*;

/** Bounded purchase requests backed by the source market funding transferred at plan creation. */
public final class SupplyDevelopmentDemand {
    private SupplyDevelopmentDemand() {}
    public static boolean valid(GameState state, SupplyDevelopmentPlan plan) {
        var owner = state.empires().stream().filter(e -> e.id().equals(plan.empireId())).findFirst().orElse(null);
        if (owner == null || plan.closed()) return false;
        return java.util.stream.Stream.of(plan.sourceHubId(), plan.destinationHubId()).allMatch(id ->
                state.commercialHubs().stream().anyMatch(h -> h.id().equals(id) && owner.controlledSystemIds().contains(
                        com.spaceconquest.engine.ship.FleetPositioning.systemForHub(state, h))));
    }
    public static Map<String, Double> requests(GameState state, String hubId) {
        var result = new HashMap<String, Double>();
        for (var plan : state.supplyDevelopmentPlans()) if (plan.sourceHubId().equals(hubId) && valid(state, plan)
                && state.tradeRoutes().stream().noneMatch(r -> r.id().equals(plan.routeId()) && (r.onboardKg() > 0 || r.totalVolumeMovedKg() >= plan.quantityKg())))
            result.merge(plan.materialId(), plan.quantityKg(), Double::sum);
        return Map.copyOf(result);
    }
    public static GameState refresh(GameState state, GameState previous) {
        return state.withCommercialHubs(state.commercialHubs().stream().map(h -> {
            var orders = new HashMap<>(h.activeOrders());
            requests(state, h.id()).forEach((id, kg) -> {
                var old = orders.get(id);
                double local = Math.max(0, (old == null ? 0 : old.demandKg()) - requests(previous, h.id()).getOrDefault(id, 0.0));
                orders.put(id, new MarketOrder(id, old == null ? 0 : old.supplyKg(), local + kg,
                        old == null ? com.spaceconquest.engine.market.MarketProcessor.basePricePerKg(id) : old.pricePerKg(), old == null ? 1 : old.shortcomingScore()));
            });
            return new CommercialHub(h.id(), h.entityId(), h.transactionTariffRate(), h.storageCapacityKg(), h.currentStoredWeightKg(), h.logisticsRangeUnits(),
                    orders, h.stockTargets(), h.stockHistory(), h.reservedTradeFuel());
        }).toList());
    }
}
