package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.IndustryMarketProcessor;
import com.spaceconquest.engine.market.*;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Discarded paid previews of finite cargo, actual route fuel and the secured empty return. */
public final class TradeSupplyPricing {
    private TradeSupplyPricing() {}
    public static SupplyOffer quote(GameState state, Fleet fleet, CommercialHub source, CommercialHub target, String material) {
        if (fleet.ships().size() != 1 || fleet.hasInterstellarOrder() || fleet.location().inTransit()
                || !FleetPositioning.atHub(state, fleet, source) || source.id().equals(target.id())) return null;
        var ship = fleet.ships().getFirst(); var design = FleetSupplySimulation.design(state, ship);
        var seller = source.activeOrders().get(material); var buyer = target.activeOrders().get(material);
        if (design == null || seller == null || buyer == null || buyer.demandKg() <= 0 || seller.supplyKg() <= 0
                || state.tradeRoutes().stream().anyMatch(r -> r.assignedFreighterIds().contains(ship.id()) && (r.onboardKg() > 0 || r.supplyReservation() != null))) return null;
        double occupied = ship.storedCargoKg().values().stream().mapToDouble(Double::doubleValue).sum();
        double quantity = Math.min(Math.min(seller.supplyKg(), buyer.demandKg()), design.maxCargoMassKg() - occupied);
        quantity = Math.min(quantity, HubReplenishment.requestedKg(state, target, material, null));
        if (quantity <= 1e-6) return null;
        var route = new TradeRoute("offer-" + ship.id(), "Supply offer", fleet.ownerEntityId(), source.id(), target.id(),
                material, quantity, 0, target.storageCapacityKg(), List.of(ship.id()), 0, false);
        var preview = new LogisticsProcessor().previewBasket(state, route, Map.of(material, quantity));
        if (preview == null) return null;
        var outgoing = RoamingTradeCost.quote(preview.state(), preview.preparedFleet(), source, target);
        var arrival = TradeReturnFuel.arrival(preview.state(), preview.preparedFleet(), target);
        if (outgoing == null || arrival == null) return null;
        var projected = preview.state().withFleets(preview.state().fleets().stream().map(f -> f.id().equals(arrival.id()) ? arrival : f).toList())
                .withTurn(state.turn() + (long) Math.ceil(outgoing.days()));
        projected = new LogisticsProcessor().fundedDelivery(projected, preview.route());
        if (projected == null) return null;
        var returning = projected.fleets().stream().filter(f -> f.id().equals(fleet.id())).findFirst().orElseThrow();
        var back = RoamingTradeCost.quote(projected, returning, target, source);
        if (back == null) return null;
        double operating = preview.route().onboardCostCredits() + outgoing.fuelCredits() + outgoing.launchCredits()
                + back.fuelCredits() + back.launchCredits();
        double capital = ShipConstructionRequirements.estimate(design).materialsKg().entrySet().stream()
                .mapToDouble(e -> e.getValue() * (source.activeOrders().containsKey(e.getKey())
                        ? source.activeOrders().get(e.getKey()).pricePerKg() : MarketProcessor.basePricePerKg(e.getKey()))).sum();
        var policy = SupplyPricingPolicy.current(); double days = outgoing.days() + back.days();
        operating += days * (capital * policy.transportMaintenanceDailyFraction() + policy.transportOperatingCreditsPerDay());
        double reserve = days * capital / policy.capitalRecoveryDays();
        double tax = SupplyPricingPolicy.taxRate(state, fleet.ownerEntityId());
        double receipts = policy.requiredReceipts(operating, reserve, tax);
        String controller = state.empires().stream().filter(e -> e.controlledSystemIds().contains(FleetPositioning.systemForHub(state, target)))
                .map(Empire::id).findFirst().orElse(null);
        double tariff = controller == null || controller.equals(fleet.ownerEntityId()) ? 0 : Math.clamp(target.transactionTariffRate(), 0, 1);
        double price = receipts / quantity / IndustryMarketProcessor.WHOLESALE_SHARE / (1 - tariff);
        double buyerCash = HubReplenishment.buyerCashAvailable(state, target, null);
        if (!Double.isFinite(price) || price <= 0 || price * quantity * IndustryMarketProcessor.WHOLESALE_SHARE > buyerCash) return null;
        return new SupplyOffer(fleet.ownerEntityId(), material, quantity / days, price, outgoing.days(), operating / days, reserve / days, tax);
    }
}
