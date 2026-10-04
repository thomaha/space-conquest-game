package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.MarketOrder;
import com.spaceconquest.engine.market.MarketStockpilePolicy;
import com.spaceconquest.engine.industry.IndustryMarketProcessor;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetPositioning;
import com.spaceconquest.engine.ship.FleetSupplySimulation;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Provisional greedy mixed basket for one receiving port, with a shared paid voyage budget. */
final class TradeBasketPlanner {
    private TradeBasketPlanner() {}

    static TradeShipmentSizing.Selection choose(GameState state, TradeRoute route, Fleet fleet, CommercialHub target) {
        var source = state.commercialHubs().stream().filter(hub -> hub.id().equals(route.originEntityId())).findFirst().orElse(null);
        if (source == null || source.id().equals(target.id())) return null;
        var ship = fleet.ships().stream().filter(item -> route.assignedFreighterIds().contains(item.id())).findFirst().orElse(null);
        var design = ship == null ? null : FleetSupplySimulation.design(state, ship);
        if (design == null) return null;
        double room = Math.min(route.transferAmountPerTurnKg(), Math.min(target.storageCapacityKg() - target.currentStoredWeightKg(),
                design.maxCargoMassKg() - ship.storedCargoKg().values().stream().mapToDouble(Double::doubleValue).sum()
                        - ship.passengerCount() * 80));
        double buyerCash = state.marketAccounts().stream().filter(account -> account.hubId().equals(target.id()))
                .mapToDouble(account -> account.unsettledSalesCredits()).findFirst().orElse(0);
        var requested = new LinkedHashMap<String, Double>();
        var sorted = source.activeOrders().values().stream().filter(item -> margin(item, target) > 0)
                .sorted(Comparator.<MarketOrder>comparingDouble(item -> margin(item, target)).reversed()
                        .thenComparing(MarketOrder::resourceId)).toList();
        for (var seller : sorted) {
            var buyer = target.activeOrders().get(seller.resourceId());
            double bid = buyer.pricePerKg() * IndustryMarketProcessor.WHOLESALE_SHARE;
            double mass = Math.min(room, Math.min(buyerCash / bid, Math.min(buyer.demandKg(),
                    Math.min(Math.max(0, seller.supplyKg() - route.minSourceInventoryThresholdKg()),
                            Math.min(route.maxDestinationCapacityKg() - buyer.supplyKg(),
                                    MarketStockpilePolicy.targetStockKg(buyer) - buyer.supplyKg())))));
            if (!Double.isFinite(mass) || mass <= 1e-6) continue;
            requested.put(seller.resourceId(), mass); room -= mass; buyerCash -= mass * bid;
        }
        if (requested.size() < 2) return null;
        double high = 1, low = 1;
        var best = evaluate(state, route.withDestination(target.id()), requested, 1);
        if (best != null && best.profit() > 1e-6) return best;
        for (int attempt = 0; attempt < 12; attempt++) {
            high = low; low /= 2;
            best = evaluate(state, route.withDestination(target.id()), requested, low);
            if (best != null) break;
        }
        if (best == null) return null;
        for (int attempt = 0; attempt < 8; attempt++) {
            double middle = (low + high) / 2;
            var candidate = evaluate(state, route.withDestination(target.id()), requested, middle);
            if (candidate == null) high = middle;
            else {
                low = middle;
                if (candidate.profit() / candidate.days() > best.profit() / best.days()) best = candidate;
            }
        }
        return best.profit() > 1e-6 ? best : null;
    }

    private static double margin(MarketOrder seller, CommercialHub target) {
        var buyer = target.activeOrders().get(seller.resourceId());
        if (buyer == null || buyer.demandKg() <= 0 || !Double.isFinite(seller.pricePerKg()) || seller.pricePerKg() < 0
                || !Double.isFinite(buyer.pricePerKg())) return -1;
        return buyer.pricePerKg() * IndustryMarketProcessor.WHOLESALE_SHARE - seller.pricePerKg();
    }

    private static TradeShipmentSizing.Selection evaluate(GameState state, TradeRoute route, Map<String, Double> requested, double scale) {
        var quantities = new HashMap<String, Double>();
        requested.forEach((id, mass) -> quantities.put(id, mass * scale));
        var shipment = new LogisticsProcessor().previewBasket(state, route, quantities);
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
