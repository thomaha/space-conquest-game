package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.IndustryMarketProcessor;
import com.spaceconquest.engine.ship.*;
import com.spaceconquest.engine.market.*;
import java.util.*;

/** Surplus after actual route costs, tax, capital/operating reserves and the target net margin. */
public final class TradeOperatingProfit {
    private TradeOperatingProfit() {}
    public static double estimate(LogisticsProcessor.ShipmentPreview preview, CommercialHub source, CommercialHub target) {
        var state = preview.state(); var ready = preview.preparedFleet(); if (ready == null) return Double.NEGATIVE_INFINITY;
        var outgoing = RoamingTradeCost.quote(state, ready, source, target); var arrival = TradeReturnFuel.arrival(state, ready, target);
        if (outgoing == null || arrival == null) return Double.NEGATIVE_INFINITY;
        var projected = state.withFleets(state.fleets().stream().map(fleet -> fleet.id().equals(arrival.id()) ? arrival : fleet).toList())
                .withTurn(state.turn() + (long) Math.ceil(outgoing.days()));
        projected = new LogisticsProcessor().fundedDelivery(projected, preview.route().withRoaming(false));
        if (projected == null) return Double.NEGATIVE_INFINITY;
        var returning = projected.fleets().stream().filter(fleet -> fleet.id().equals(arrival.id())).findFirst().orElseThrow();
        var back = RoamingTradeCost.quote(projected, returning, target, source); if (back == null) return Double.NEGATIVE_INFINITY;
        double replacement = preview.route().onboardCostCredits() + outgoing.fuelCredits() + outgoing.launchCredits() + back.fuelCredits() + back.launchCredits();
        double revenue = preview.route().cargoManifest().entrySet().stream().mapToDouble(entry -> {
            var order = target.activeOrders().get(entry.getKey()); return order == null ? 0 : order.pricePerKg() * IndustryMarketProcessor.WHOLESALE_SHARE * entry.getValue().massKg();
        }).sum();
        var design = FleetSupplySimulation.design(state, ready.ships().getFirst());
        if (design == null) return Double.NEGATIVE_INFINITY;
        double capital = ShipConstructionRequirements.estimate(design).materialsKg().entrySet().stream()
                .mapToDouble(e -> e.getValue() * (source.activeOrders().containsKey(e.getKey())
                        ? source.activeOrders().get(e.getKey()).pricePerKg() : MarketProcessor.basePricePerKg(e.getKey()))).sum();
        var policy = SupplyPricingPolicy.current();
        replacement += (outgoing.days() + back.days()) * (capital * policy.transportMaintenanceDailyFraction() + policy.transportOperatingCreditsPerDay());
        double reserve = (outgoing.days() + back.days()) * capital / policy.capitalRecoveryDays();
        String controller = state.empires().stream().filter(e -> e.controlledSystemIds().contains(FleetPositioning.systemForHub(state, target)))
                .map(Empire::id).findFirst().orElse(null);
        double tariff = controller == null || controller.equals(ready.ownerEntityId()) ? 0 : Math.clamp(target.transactionTariffRate(), 0, 1);
        return revenue * (1 - tariff) - policy.requiredReceipts(replacement, reserve, SupplyPricingPolicy.taxRate(state, ready.ownerEntityId()));
    }
}
