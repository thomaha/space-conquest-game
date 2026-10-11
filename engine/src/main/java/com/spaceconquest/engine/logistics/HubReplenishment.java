package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.market.MarketStockpilePolicy;
import java.util.HashMap;
import java.util.HashSet;

/** Pure inventory-position calculations; only paid cargo already assigned to a destination is inbound. */
public final class HubReplenishment {
    private HubReplenishment() {}

    public static double inboundKg(GameState state, CommercialHub hub, String materialId, String excludedRouteId) {
        double total = 0;
        var countedShips = new HashSet<String>();
        for (var route : state.tradeRoutes()) {
            if (route.id().equals(excludedRouteId) || !hub.id().equals(route.destinationEntityId())
                    || !TradeRoute.DELIVERING.equals(route.phase())) continue;
            var ship = state.fleets().stream().flatMap(fleet -> fleet.ships().stream())
                    .filter(item -> route.assignedFreighterIds().contains(item.id())).findFirst().orElse(null);
            if (ship == null || !countedShips.add(ship.id())) continue;
            for (var lot : route.cargoManifest().entrySet()) {
                if (materialId != null && !materialId.equals(lot.getKey())) continue;
                total += Math.max(0, Math.min(lot.getValue().massKg(),
                        ship.storedCargoKg().getOrDefault(lot.getKey(), 0.0)));
            }
        }
        return total;
    }

    public static double requestedKg(GameState state, CommercialHub hub, String materialId, String routeId) {
        var order = hub.activeOrders().get(materialId);
        if (order == null) return 0;
        var target = hub.stockTargets().get(materialId);
        if (target == null) return Math.max(0, order.demandKg());
        double position = order.supplyKg() + inboundKg(state, hub, materialId, routeId);
        if (!target.refillRequested() && position >= target.reorderThresholdKg()) return 0;
        return Math.max(0, target.targetStockKg() - position);
    }

    public static double targetStockKg(CommercialHub hub, String materialId) {
        var target = hub.stockTargets().get(materialId);
        return target == null ? MarketStockpilePolicy.targetStockKg(hub.activeOrders().get(materialId))
                : target.targetStockKg();
    }

    public static double storageRoomKg(GameState state, CommercialHub hub, String routeId) {
        return Math.max(0, hub.storageCapacityKg() - hub.currentStoredWeightKg()
                - (hub.stockTargets().isEmpty() ? 0 : inboundKg(state, hub, null, routeId)));
    }

    public static double buyerCashAvailable(GameState state, CommercialHub hub, String routeId) {
        double cash = state.marketAccounts().stream().filter(account -> account.hubId().equals(hub.id()))
                .mapToDouble(account -> account.unsettledSalesCredits()).findFirst().orElse(0);
        if (hub.stockTargets().isEmpty()) return cash;
        double committed = hub.activeOrders().values().stream().mapToDouble(order ->
                inboundKg(state, hub, order.resourceId(), routeId) * Math.max(0, order.pricePerKg())
                        * com.spaceconquest.engine.industry.IndustryMarketProcessor.WHOLESALE_SHARE).sum();
        return Math.max(0, cash - committed);
    }

    public static GameState refreshRequests(GameState state) {
        return state.withCommercialHubs(state.commercialHubs().stream().map(hub -> {
            if (hub.stockTargets().isEmpty()) return hub;
            var targets = new HashMap<>(hub.stockTargets());
            targets.replaceAll((material, target) -> {
                var order = hub.activeOrders().get(material);
                double stock = order == null ? 0 : order.supplyKg();
                boolean requested = stock >= target.targetStockKg() ? false
                        : target.refillRequested() || stock + inboundKg(state, hub, material, null) < target.reorderThresholdKg();
                return target.withRefillRequested(requested);
            });
            return hub.withStockTargets(targets);
        }).toList());
    }
}
