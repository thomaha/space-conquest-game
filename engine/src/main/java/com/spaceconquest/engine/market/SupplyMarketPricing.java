package com.spaceconquest.engine.market;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.GridSupplyPricing;
import com.spaceconquest.engine.logistics.*;
import com.spaceconquest.engine.ship.FleetPositioning;
import java.util.*;

/** Finite supply offers influence scarcity prices without manufacturing goods or buyer cash. */
public final class SupplyMarketPricing {
    private SupplyMarketPricing() {}
    public static List<CommercialHub> refresh(GameState state) {
        var result = new ArrayList<CommercialHub>(); var base = new MarketProcessor();
        var forecast = GridSupplyPricing.forecast(state); var tariffs = forecast.tariffs();
        for (var hub : state.commercialHubs()) {
            var leadTimes = SupplyLeadTimes.local(state, hub);
            double energy = tariffs.getOrDefault(hub.entityId(), com.spaceconquest.engine.industry.PowerBillingProcessor.PRICE_PER_KWH);
            var offers = new ArrayList<>(FacilitySupplyPricing.offers(state, hub, energy, forecast.productionEnergyKwh()));
            var orders = new HashMap<String, MarketOrder>();
            int candidates = 0;
            for (var order : hub.activeOrders().values().stream().sorted(Comparator.comparingDouble(MarketOrder::shortcomingScore)
                    .reversed().thenComparing(MarketOrder::resourceId)).toList()) {
                double leadDays = offers.stream().filter(o -> o.materialId().equals(order.resourceId()))
                        .mapToDouble(SupplyOffer::deliveryDays).max().orElse(0);
                double buffer = order.demandKg() * (30 + Math.max(leadDays, leadTimes.getOrDefault(order.resourceId(), 0.0)));
                double wanted = Math.max(0, buffer - order.supplyKg());
                if (order.supplyKg() < order.demandKg()) for (var fleet : state.fleets()) {
                    if (candidates >= SupplyPricingPolicy.current().maximumImportCandidates()) break;
                    var source = state.commercialHubs().stream().filter(h -> FleetPositioning.atHub(state, fleet, h)).findFirst().orElse(null);
                    if (source == null || source.id().equals(hub.id()) || !source.activeOrders().containsKey(order.resourceId())
                            || source.activeOrders().get(order.resourceId()).supplyKg() <= 0
                            || !FleetPositioning.systemForHub(state, source).equals(FleetPositioning.systemForHub(state, hub))) continue;
                    candidates++;
                    var offer = TradeSupplyPricing.quote(state, fleet, source, hub, order.resourceId());
                    if (offer != null) offers.add(offer);
                }
                double price = base.calculateSpotPrice(MarketProcessor.basePricePerKg(order.resourceId()), order.supplyKg(), order.demandKg());
                // Floating-point dust below a filled buffer is not a new replenishment order.
                if (wanted > buffer * 1e-9) {
                    var ordered = offers.stream().filter(o -> o.materialId().equals(order.resourceId()))
                            .sorted(Comparator.comparingDouble(SupplyOffer::retailPricePerKg).thenComparing(SupplyOffer::supplierId)).toList();
                    double remaining = Math.min(order.demandKg(), wanted); double marginal = 0;
                    for (var offer : ordered) {
                        marginal = offer.retailPricePerKg(); remaining -= offer.quantityKg();
                        if (remaining <= 0) break;
                    }
                    if (marginal > 0) price = Math.max(price, marginal);
                }
                orders.put(order.resourceId(), new MarketOrder(order.resourceId(), order.supplyKg(), order.demandKg(), price,
                        base.calculateShortcomingScore(order.supplyKg(), order.demandKg(), price / MarketProcessor.basePricePerKg(order.resourceId()))));
            }
            result.add(new CommercialHub(hub.id(), hub.entityId(), hub.transactionTariffRate(), hub.storageCapacityKg(),
                    hub.currentStoredWeightKg(), hub.logisticsRangeUnits(), orders, hub.stockTargets(), hub.stockHistory(), hub.reservedTradeFuel()));
        }
        return List.copyOf(result);
    }
}
