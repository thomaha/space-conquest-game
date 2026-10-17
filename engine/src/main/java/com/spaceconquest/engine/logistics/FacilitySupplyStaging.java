package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;

/** Paid empty travel and saved claims. Forecast arrivals are discarded and never committed as movement. */
public final class FacilitySupplyStaging {
    private FacilitySupplyStaging() {}
    public record Preview(GameState departure, GameState arrival, double days, double costCredits, double replacementCostCredits) {}
    public record Step(GameState state, TradeRoute route) {}
    public static double claimedSourceKg(GameState state, String hub, String material) {
        return state.tradeRoutes().stream().filter(route -> route.reservesSupply(state.turn())
                && hub.equals(route.originEntityId()) && material.equals(route.materialId())).mapToDouble(TradeRoute::transferAmountPerTurnKg).sum();
    }
    public static double claimedDestinationKg(GameState state, String hub, String material) {
        return state.tradeRoutes().stream().filter(route -> route.reservesSupply(state.turn())
                && hub.equals(route.destinationEntityId()) && material.equals(route.materialId())).mapToDouble(TradeRoute::transferAmountPerTurnKg).sum();
    }
    public static Preview preview(GameState state, Fleet fleet, CommercialHub target) {
        var source = state.commercialHubs().stream().filter(hub -> FleetPositioning.atHub(state, fleet, hub)).findFirst().orElse(null);
        if (source == null || source.id().equals(target.id())) return null;
        var paid = TradeLegReadiness.prepare(state, fleet, target); var ready = fleet(paid, fleet.id());
        if (!TradeLegReadiness.inspect(paid, ready, target).ready()) return null;
        var cost = RoamingTradeCost.quote(paid, ready, source, target); var arrival = TradeReturnFuel.arrival(paid, ready, target);
        if (cost == null || arrival == null) return null;
        var departure = new LogisticsProcessor().moveToward(paid, fleet.id(), target); var moving = fleet(departure, fleet.id());
        if (!moving.hasInterstellarOrder() && !moving.location().inTransit()) return null;
        double spending = Math.max(0, cash(state, fleet.ownerEntityId()) - cash(departure, fleet.ownerEntityId()));
        var projected = departure.withFleets(departure.fleets().stream().map(item -> item.id().equals(arrival.id()) ? arrival : item).toList())
                .withTurn(state.turn() + (long) Math.ceil(cost.days()));
        return new Preview(departure, projected, cost.days(), spending, cost.fuelCredits() + cost.launchCredits());
    }
    static Step advance(GameState state, TradeRoute route, Fleet fleet, LogisticsProcessor processor) {
        var source = state.commercialHubs().stream().filter(hub -> hub.id().equals(route.originEntityId())).findFirst().orElse(null);
        var target = state.commercialHubs().stream().filter(hub -> hub.id().equals(route.destinationEntityId())).findFirst().orElse(null);
        var empire = com.spaceconquest.engine.economy.EconomicOwnership.empireFor(state, route.ownerEntityId());
        if (!route.reservesSupply(state.turn()) || empire == null || source == null || target == null
                || !com.spaceconquest.engine.economy.EconomicOwnership.transportOperator(state, empire, route.ownerEntityId())
                || !empire.controlledSystemIds().contains(FleetPositioning.systemForHub(state, source))
                || !empire.controlledSystemIds().contains(FleetPositioning.systemForHub(state, target)))
            return new Step(state, release(route, "Supply reservation expired or its ports lost control; paid movement is retained."));
        if (FleetPositioning.atHub(state, fleet, source)) return new Step(state, route.withStatus("At supply source; awaiting a fresh loading quote."));
        var moved = processor.moveToward(state, fleet.id(), source);
        double cost = Math.max(0, cash(state, route.ownerEntityId()) - cash(moved, route.ownerEntityId()));
        var reservation = route.supplyReservation().withSpending(cost);
        if (reservation.spentCredits() > reservation.maximumCostCredits() || cash(moved, route.ownerEntityId()) < reservation.treasuryFloorCredits())
            return new Step(state, release(route, "Supply staging cannot fund its next leg within the saved limits."));
        return new Step(moved, route.withSupplyReservation(reservation).withOperatingCost(cost));
    }
    public static TradeRoute release(TradeRoute route, String message) {
        return route.withSupplyReservation(null).withPhase(TradeRoute.LOADING).withActive(false).withStatus(message);
    }
    private static Fleet fleet(GameState state, String id) { return state.fleets().stream().filter(item -> id.equals(item.id())).findFirst().orElseThrow(); }
    private static double cash(GameState state, String owner) { return com.spaceconquest.engine.economy.EconomicOwnership.cash(state, owner); }
}
