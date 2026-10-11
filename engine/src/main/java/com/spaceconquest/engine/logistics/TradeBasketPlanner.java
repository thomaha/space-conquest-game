package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.MarketOrder;
import com.spaceconquest.engine.industry.IndustryMarketProcessor;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetPositioning;
import com.spaceconquest.engine.ship.FleetSupplySimulation;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/** Bounded alternative mixed baskets for one receiving port, sharing a paid voyage budget. */
final class TradeBasketPlanner {
    private TradeBasketPlanner() {}

    static TradeShipmentSizing.Selection choose(GameState state, TradeRoute route, Fleet fleet, CommercialHub target) {
        var source = state.commercialHubs().stream().filter(hub -> hub.id().equals(route.originEntityId())).findFirst().orElse(null);
        if (source == null || source.id().equals(target.id())) return null;
        var ship = fleet.ships().stream().filter(item -> route.assignedFreighterIds().contains(item.id())).findFirst().orElse(null);
        var design = ship == null ? null : FleetSupplySimulation.design(state, ship);
        if (design == null) return null;
        double room = Math.min(route.transferAmountPerTurnKg(), Math.min(HubReplenishment.storageRoomKg(state, target, route.id()),
                design.maxCargoMassKg() - ship.storedCargoKg().values().stream().mapToDouble(Double::doubleValue).sum()
                        - ship.passengerCount() * 80));
        double buyerCash = state.marketAccounts().stream().filter(account -> account.hubId().equals(target.id()))
                .mapToDouble(account -> account.unsettledSalesCredits()).findFirst().orElse(0);
        buyerCash = Math.min(buyerCash, HubReplenishment.buyerCashAvailable(state, target, route.id()));
        var sorted = source.activeOrders().values().stream().filter(item -> margin(item, target) > 0
                        && HubReplenishment.requestedKg(state, target, item.resourceId(), route.id()) > 0)
                .sorted(Comparator.<MarketOrder>comparingDouble(item ->
                                TradeFuelPriority.urgency(state, target, item.resourceId(), route.id())).reversed()
                        .thenComparing(Comparator.<MarketOrder>comparingDouble(item -> margin(item, target)).reversed())
                        .thenComparing(MarketOrder::resourceId)).toList();
        var requested = basket(state, route, target, sorted, room, buyerCash, Map.of());
        var greedy = sized(state, route.withDestination(target.id()), requested);
        if (greedy != null) return greedy;
        TradeShipmentSizing.Selection best = null;
        var goods = sorted.stream().filter(item -> TradeFuelPriority.urgency(state, target, item.resourceId(), route.id()) <= 0).toList();
        for (var fuel : sorted.stream().filter(item ->
                TradeFuelPriority.urgency(state, target, item.resourceId(), route.id()) > 0).limit(8).toList()) {
            var order = new ArrayList<MarketOrder>();
            order.add(fuel); order.addAll(goods); order.add(fuel);
            var mixed = basket(state, route, target, order, room, buyerCash, Map.of(fuel.resourceId(), room / 2));
            var selected = sized(state, route.withDestination(target.id()), mixed);
            if (selected == null) selected = sized(state, route.withDestination(target.id()),
                    basket(state, route, target, List.of(fuel), room, buyerCash, Map.of()));
            if (TradeFuelPriority.better(state, selected, best)) best = selected;
        }
        return best;
    }

    /** Seed a partial fuel load, add goods, then offer the remaining capacity to that fuel. */
    private static Map<String, Double> basket(GameState state, TradeRoute route, CommercialHub target,
                                              List<MarketOrder> sellers, double room, double buyerCash,
                                              Map<String, Double> firstLimits) {
        var requested = new LinkedHashMap<String, Double>();
        for (var seller : sellers) {
            var buyer = target.activeOrders().get(seller.resourceId());
            double allocated = requested.getOrDefault(seller.resourceId(), 0.0);
            double bid = buyer.pricePerKg() * IndustryMarketProcessor.WHOLESALE_SHARE;
            double mass = Math.min(room, Math.min(buyerCash / bid, Math.min(HubReplenishment.requestedKg(state, target, seller.resourceId(), route.id()) - allocated,
                    Math.min(Math.max(0, seller.supplyKg() - route.minSourceInventoryThresholdKg() - allocated),
                            Math.min(route.maxDestinationCapacityKg() - buyer.supplyKg() - allocated,
                                    HubReplenishment.targetStockKg(target, seller.resourceId()) - buyer.supplyKg() - allocated)))));
            if (!requested.containsKey(seller.resourceId())) mass = Math.min(mass, firstLimits.getOrDefault(seller.resourceId(), Double.POSITIVE_INFINITY));
            if (!Double.isFinite(mass) || mass <= 1e-6) continue;
            requested.merge(seller.resourceId(), mass, Double::sum); room -= mass; buyerCash -= mass * bid;
        }
        return requested;
    }

    private static TradeShipmentSizing.Selection sized(GameState state, TradeRoute route, Map<String, Double> requested) {
        var target = state.commercialHubs().stream().filter(hub -> hub.id().equals(route.destinationEntityId())).findFirst().orElseThrow();
        if (requested.isEmpty() || requested.size() == 1
                && TradeFuelPriority.urgency(state, target, requested.keySet().iterator().next(), route.id()) <= 0) return null;
        double high = 1, low = 1;
        var best = evaluate(state, route, requested, 1);
        if (best != null && best.profit() > 1e-6) {
            if (TradeFuelPriority.consumesUrgentStock(state, best.shipment())) {
                for (int attempt = 1; attempt <= 3; attempt++) {
                    var candidate = evaluate(state, route, requested, 1.0 / (1 << attempt));
                    if (TradeFuelPriority.better(state, candidate, best)) best = candidate;
                }
            }
            return best;
        }
        for (int attempt = 0; attempt < 12; attempt++) {
            high = low; low /= 2;
            best = evaluate(state, route, requested, low);
            if (best != null) break;
        }
        if (best == null) return null;
        for (int attempt = 0; attempt < 8; attempt++) {
            double middle = (low + high) / 2;
            var candidate = evaluate(state, route, requested, middle);
            if (candidate == null) high = middle;
            else {
                low = middle;
                if (TradeFuelPriority.better(state, candidate, best)) best = candidate;
            }
        }
        return best.profit() > 1e-6 ? best : null;
    }

    private static double margin(MarketOrder seller, CommercialHub target) {
        var buyer = target.activeOrders().get(seller.resourceId());
        if (buyer == null || !Double.isFinite(seller.pricePerKg()) || seller.pricePerKg() < 0
                || !Double.isFinite(buyer.pricePerKg())) return -1;
        return buyer.pricePerKg() * IndustryMarketProcessor.WHOLESALE_SHARE - seller.pricePerKg();
    }

    private static TradeShipmentSizing.Selection evaluate(GameState state, TradeRoute route, Map<String, Double> requested, double scale) {
        var quantities = new HashMap<String, Double>();
        requested.forEach((id, mass) -> quantities.put(id, mass * scale));
        var shipment = new LogisticsProcessor().previewBasket(state, route, quantities, true);
        if (shipment == null) return null;
        var source = state.commercialHubs().stream().filter(hub -> hub.id().equals(route.originEntityId())).findFirst().orElseThrow();
        var target = state.commercialHubs().stream().filter(hub -> hub.id().equals(route.destinationEntityId())).findFirst().orElseThrow();
        var quote = RoamingTradeCost.quote(state, shipment.preparedFleet(), source, target);
        if (quote == null) return null;
        double sale = quantities.entrySet().stream().mapToDouble(item -> item.getValue()
                * target.activeOrders().get(item.getKey()).pricePerKg() * IndustryMarketProcessor.WHOLESALE_SHARE).sum();
        double tax = state.empires().stream().anyMatch(empire -> empire.controlledSystemIds()
                .contains(FleetPositioning.systemForHub(state, target))) ? Math.clamp(target.transactionTariffRate(), 0, 1) : 0;
        double profit = sale * (1 - tax) - shipment.route().onboardCostCredits() - quote.fuelCredits() - quote.launchCredits();
        return Double.isFinite(profit) ? new TradeShipmentSizing.Selection(shipment, profit, quote.days()) : null;
    }
}
