package com.spaceconquest.control.ai;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.logistics.*;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Discarded paid shipment execution: cargo, travel, buyer funding and protected return use the ordinary route preview. */
public record FacilitySupplyShipment(GameState state, TradeRoute route, String fleetId, double departureCostCredits, double operatingDays, double estimatedOperatingProfitCredits) {
    public static boolean available(GameState state, Empire empire, Fleet fleet) {
        if (state.supplyDevelopmentPlans().stream().anyMatch(p -> !p.closed() && p.fleetId().equals(fleet.id()))) return false;
        if (ShipRefitPlan.reserved(state, fleet) || fleet.ships().stream().anyMatch(ship -> state.shipyardLedger().deployments().containsKey(ship.id()))) return false;
        return com.spaceconquest.engine.economy.EconomicOwnership.transportOperator(state, empire, fleet.ownerEntityId()) && fleet.ships().size() == 1 && "PASSIVE".equalsIgnoreCase(fleet.fleetStance())
                && !fleet.hasInterstellarOrder() && !fleet.location().inTransit() && fleet.escortOrder() == null
                && fleet.guardOrder() == null && fleet.interceptOrder() == null && fleet.ships().getFirst().currentHullHealth() > 0
                && state.shipDesigns().stream().anyMatch(design -> design.id().equals(fleet.ships().getFirst().designId())
                        && fleet.ownerEntityId().equals(design.ownerEntityId()) && ShipRole.CARGO_TRANSPORT.equals(design.role()))
                && state.tradeRoutes().stream().noneMatch(route -> route.carrierCommitted() && route.assignedFreighterIds().contains(fleet.ships().getFirst().id()));
    }
    public static FacilitySupplyShipment quote(GameState state, Empire empire, FacilitySupplyNeeds.Need need, String shipId, String routeId, LogisticsPolicy policy) {
        var fleet = state.fleets().stream().filter(value -> value.ships().stream().anyMatch(ship -> ship.id().equals(shipId))).findFirst().orElse(null);
        if (fleet == null || !available(state, empire, fleet)) return null;
        var ship = fleet.ships().getFirst(); if (!fleet.ownerEntityId().equals(ship.ownerEntityId()) || ship.passengerCount() > 0 || !ship.storedCargoKg().isEmpty()) return null;
        var design = state.shipDesigns().stream().filter(value -> ship.designId().equals(value.id()) && fleet.ownerEntityId().equals(value.ownerEntityId())
                && ShipRole.CARGO_TRANSPORT.equals(value.role())).findFirst().orElse(null);
        if (design == null) return null;
        var source = state.commercialHubs().stream().filter(hub -> FleetPositioning.atHub(state, fleet, hub)
                && empire.controlledSystemIds().contains(FleetPositioning.systemForHub(state, hub))).findFirst().orElse(null);
        var destination = state.commercialHubs().stream().filter(hub -> need.hubId().equals(hub.id())).findFirst().orElse(null);
        if (source == null || destination == null || source.id().equals(destination.id())) return null;
        var stock = source.activeOrders().get(need.materialId()); if (stock == null) return null;
        double keep = Math.max(0, stock.demandKg() - SupplyDevelopmentDemand.requests(state, source.id()).getOrDefault(need.materialId(), 0.0)) * policy.sourceReserveDays();
        var configuredSource = source.stockTargets().get(need.materialId()); if (configuredSource != null) keep = Math.max(keep, configuredSource.targetStockKg());
        keep += FacilitySupplyStaging.claimedSourceKg(state, source.id(), need.materialId());
        double amount = Math.min(design.maxCargoMassKg(), Math.min(stock.supplyKg() - keep, need.targetKg() - need.positionKg()));
        if (!Double.isFinite(amount) || amount < need.dailyKg() * policy.minimumDeliveryDays()) return null;
        var prepared = state;
        if (!destination.stockTargets().containsKey(need.materialId())) {
            var target = new SetHubStockTargetCommand(empire.id(), destination.id(), need.materialId(), need.dailyKg() * policy.reorderDays(), need.targetKg());
            if (!target.validate(state)) return null; prepared = target.apply(state);
        }
        var proposal = new CreateTradeRouteCommand(fleet.ownerEntityId(), "AI facility supply", source.id(), destination.id(), need.materialId(), amount, keep, need.targetKg(), List.of(shipId));
        if (!proposal.validate(prepared)) return null;
        var route = new TradeRoute(routeId, "AI facility supply", fleet.ownerEntityId(), source.id(), destination.id(), need.materialId(), amount, keep, need.targetKg(), List.of(shipId), 0, true);
        var preview = new LogisticsProcessor().previewShipment(prepared, route, amount, true);
        if (preview == null || preview.route().onboardKg() < need.dailyKg() * policy.minimumDeliveryDays()) return null;
        double remaining = com.spaceconquest.engine.economy.EconomicOwnership.cash(preview.state(), fleet.ownerEntityId());
        double cost = com.spaceconquest.engine.economy.EconomicOwnership.cash(state, fleet.ownerEntityId()) - remaining;
        double reserve = empire.id().equals(fleet.ownerEntityId()) ? policy.treasuryReserveCredits()
                : Math.max(policy.treasuryReserveCredits(), TransportConstructionPolicy.current().corporateReserveCredits());
        if (remaining < reserve || cost > policy.maximumDepartureCostCredits()) return null;
        double profit = empire.id().equals(fleet.ownerEntityId()) ? Double.NaN : TradeOperatingProfit.estimate(preview, source, destination);
        if (!empire.id().equals(fleet.ownerEntityId()) && (!Double.isFinite(profit) || profit < TransportConstructionPolicy.current().minimumOperatingProfitCredits())) return null;
        return new FacilitySupplyShipment(preview.state(), preview.route().withActive(false), fleet.id(), cost, preview.route().onboardKg() / need.dailyKg(), profit);
    }
}
