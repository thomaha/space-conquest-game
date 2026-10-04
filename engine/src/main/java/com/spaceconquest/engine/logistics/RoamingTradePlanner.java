package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.IndustryMarketProcessor;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetPortReadiness;
import java.util.Comparator;

/** Pure next-trade selection. Candidate purchases and departures remain isolated until the tick selects one. */
public final class RoamingTradePlanner {
    public record Selection(LogisticsProcessor.ShipmentPreview shipment, double profitCredits, double days, String explanation) {}
    private RoamingTradePlanner() {}

    public static Selection choose(GameState state, TradeRoute route, Fleet fleet) {
        CommercialHub source = state.commercialHubs().stream().filter(hub -> hub.id().equals(route.originEntityId())).findFirst().orElse(null);
        if (source == null) return new Selection(null, 0, 0, "Waiting: current trading port is unavailable.");
        Selection best = null;
        boolean bestResupply = false;
        var processor = new LogisticsProcessor();
        for (var item : source.activeOrders().values().stream().sorted(Comparator.comparing(order -> order.resourceId())).toList()) {
            if (!Double.isFinite(item.pricePerKg()) || item.pricePerKg() < 0
                    || item.supplyKg() <= route.minSourceInventoryThresholdKg()) continue;
            for (var target : state.commercialHubs().stream().sorted(Comparator.comparing(CommercialHub::id)).toList()) {
                var buyer = target.activeOrders().get(item.resourceId());
                if (target.id().equals(source.id()) || buyer == null || buyer.demandKg() <= 0
                        || !Double.isFinite(buyer.pricePerKg()) || buyer.pricePerKg() <= 0
                        || buyer.pricePerKg() * IndustryMarketProcessor.WHOLESALE_SHARE <= item.pricePerKg()) continue;
                var candidate = route.withMarketChoice(item.resourceId(), target.id());
                double buyerCash = state.marketAccounts().stream().filter(account -> account.hubId().equals(target.id()))
                        .mapToDouble(account -> account.unsettledSalesCredits()).findFirst().orElse(0);
                double maximumKg = Math.min(buyer.demandKg(), buyerCash / (buyer.pricePerKg() * IndustryMarketProcessor.WHOLESALE_SHARE));
                if (!Double.isFinite(maximumKg) || maximumKg <= 1e-6) continue;
                var shipment = processor.previewShipment(state, candidate, maximumKg);
                if (shipment == null || shipment.preparedFleet() == null) continue;
                var quote = RoamingTradeCost.quote(state, shipment.preparedFleet(), source, target);
                if (quote == null) continue;
                double tax = state.empires().stream().anyMatch(empire -> empire.controlledSystemIds()
                        .contains(com.spaceconquest.engine.ship.FleetPositioning.systemForHub(state, target)))
                        ? Math.clamp(target.transactionTariffRate(), 0, 1) : 0;
                double sale = shipment.route().onboardKg() * buyer.pricePerKg() * IndustryMarketProcessor.WHOLESALE_SHARE;
                double profit = sale * (1 - tax) - shipment.route().onboardKg() * item.pricePerKg()
                        - quote.fuelCredits() - quote.launchCredits();
                if (!Double.isFinite(profit) || profit <= 1e-6) continue;
                boolean resupply = FleetPortReadiness.inspect(state, shipment.preparedFleet(), target).available();
                if (best == null || resupply && !bestResupply || resupply == bestResupply
                        && profit / quote.days() > best.profitCredits() / best.days()) {
                    bestResupply = resupply;
                    best = new Selection(shipment, profit, quote.days(), String.format(
                            "Selected %s to %s: expected profit %,.1f credits over %,.0f days after fuel, electricity, launch and tariff costs.",
                            item.resourceId(), target.id(), profit, quote.days()));
                }
            }
        }
        return best == null ? new Selection(null, 0, 0,
                "Waiting at port: no profitable loaded shipment has affordable supplies and a safe next leg.") : best;
    }
}
