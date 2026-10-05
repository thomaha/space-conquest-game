package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.IndustryMarketProcessor;
import com.spaceconquest.engine.ship.FleetPositioning;
import com.spaceconquest.engine.market.MarketStockpilePolicy;

/** Bounded paid previews; unsuccessful quantities never change the live snapshot. */
final class TradeShipmentSizing {
    record Selection(LogisticsProcessor.ShipmentPreview shipment, double profit, double days) {}
    private static final int REDUCTIONS = 12;
    private static final int REFINEMENTS = 8;
    private TradeShipmentSizing() {}

    static Selection choose(GameState state, TradeRoute route, double maximumKg) {
        double high = quantityLimit(state, route, maximumKg);
        if (!Double.isFinite(high) || high <= 1e-6) return null;
        Selection best = evaluate(state, route, high);
        if (best != null && best.profit() > 1e-6) return best;
        double low = high;
        for (int attempt = 0; attempt < REDUCTIONS; attempt++) {
            high = low;
            low /= 2;
            best = evaluate(state, route, low);
            if (best != null) break;
        }
        if (best == null) return null;
        for (int attempt = 0; attempt < REFINEMENTS; attempt++) {
            double middle = (low + high) / 2;
            var candidate = evaluate(state, route, middle);
            if (candidate == null) high = middle;
            else {
                low = middle;
                if (candidate.profit() / candidate.days() > best.profit() / best.days()) best = candidate;
            }
        }
        return best.profit() > 1e-6 ? best : null;
    }

    private static double quantityLimit(GameState state, TradeRoute route, double maximumKg) {
        var source = state.commercialHubs().stream().filter(hub -> hub.id().equals(route.originEntityId())).findFirst().orElse(null);
        var target = state.commercialHubs().stream().filter(hub -> hub.id().equals(route.destinationEntityId())).findFirst().orElse(null);
        var ship = state.fleets().stream().flatMap(fleet -> fleet.ships().stream())
                .filter(item -> route.assignedFreighterIds().contains(item.id())).findFirst().orElse(null);
        if (source == null || target == null || ship == null) return 0;
        var seller = source.activeOrders().get(route.materialId()); var buyer = target.activeOrders().get(route.materialId());
        var design = state.shipDesigns().stream().filter(item -> item.id().equals(ship.designId())).findFirst().orElse(null);
        if (seller == null || buyer == null || design == null) return 0;
        double bid = buyer.pricePerKg() * IndustryMarketProcessor.WHOLESALE_SHARE;
        if (!Double.isFinite(bid) || bid <= 0 || !Double.isFinite(seller.pricePerKg()) || seller.pricePerKg() < 0) return 0;
        double buyerCash = state.marketAccounts().stream().filter(account -> account.hubId().equals(target.id()))
                .mapToDouble(account -> account.unsettledSalesCredits()).findFirst().orElse(0);
        double ownerCash = ownerCash(state, route.ownerEntityId());
        double marketLimit = Math.min(buyer.demandKg(), Math.min(buyerCash / bid,
                seller.pricePerKg() == 0 ? Double.POSITIVE_INFINITY : ownerCash / seller.pricePerKg()));
        double capacity = design.maxCargoMassKg() - ship.passengerCount() * 80
                - ship.storedCargoKg().values().stream().mapToDouble(Double::doubleValue).sum();
        double room = Math.min(target.storageCapacityKg() - target.currentStoredWeightKg(),
                Math.min(route.maxDestinationCapacityKg() - buyer.supplyKg(), MarketStockpilePolicy.targetStockKg(buyer) - buyer.supplyKg()));
        return Math.min(marketLimit, Math.min(maximumKg, Math.min(route.transferAmountPerTurnKg(),
                Math.min(seller.supplyKg() - route.minSourceInventoryThresholdKg(), Math.min(capacity, room)))));
    }

    static double ownerCash(GameState state, String ownerId) {
        return state.corporations().stream().filter(owner -> owner.id().equals(ownerId))
                .mapToDouble(owner -> owner.liquidCapitalReserves()).findFirst().orElseGet(() -> state.empires().stream()
                        .filter(owner -> owner.id().equals(ownerId)).mapToDouble(owner -> owner.treasuryCredits()).findFirst().orElse(0));
    }

    private static Selection evaluate(GameState state, TradeRoute route, double maximumKg) {
        var source = state.commercialHubs().stream().filter(hub -> hub.id().equals(route.originEntityId())).findFirst().orElse(null);
        var target = state.commercialHubs().stream().filter(hub -> hub.id().equals(route.destinationEntityId())).findFirst().orElse(null);
        if (source == null || target == null) return null;
        var seller = source.activeOrders().get(route.materialId());
        var buyer = target.activeOrders().get(route.materialId());
        if (seller == null || buyer == null || buyer.demandKg() <= 0) return null;
        double bid = buyer.pricePerKg() * IndustryMarketProcessor.WHOLESALE_SHARE;
        double cash = state.marketAccounts().stream().filter(account -> account.hubId().equals(target.id()))
                .mapToDouble(account -> account.unsettledSalesCredits()).findFirst().orElse(0);
        if (!Double.isFinite(bid) || bid <= 0) return null;
        double limit = Math.min(maximumKg, Math.min(buyer.demandKg(), cash / bid));
        if (!Double.isFinite(limit) || limit <= 1e-6) return null;
        var shipment = new LogisticsProcessor().previewShipment(state, route, limit);
        if (shipment == null || shipment.preparedFleet() == null) return null;
        var quote = RoamingTradeCost.quote(state, shipment.preparedFleet(), source, target);
        if (quote == null) return null;
        double tax = state.empires().stream().anyMatch(empire -> empire.controlledSystemIds()
                .contains(FleetPositioning.systemForHub(state, target))) ? Math.clamp(target.transactionTariffRate(), 0, 1) : 0;
        double profit = shipment.route().onboardKg() * (bid * (1 - tax) - seller.pricePerKg())
                - quote.fuelCredits() - quote.launchCredits();
        return Double.isFinite(profit) ? new Selection(shipment, profit, quote.days()) : null;
    }
}
