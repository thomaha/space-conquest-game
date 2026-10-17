package com.spaceconquest.control.ai;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.logistics.*;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** One revalidated paid action per review. Forecasted production never becomes cargo. */
public final class SupplyDevelopmentProgress {
    public record Step(GameState state, String reason, boolean changed) {}
    private SupplyDevelopmentProgress() {}
    private static Step blocked(GameState state, String reason) { return new Step(state, reason, false); }
    public static Step next(GameState state, SupplyDevelopmentPlan plan) {
        var oldRoute = state.tradeRoutes().stream().filter(r -> r.id().equals(plan.routeId())).findFirst().orElse(null);
        if (plan.closed()) return blocked(state, "Plan is closed");
        if (!SupplyDevelopmentDemand.valid(state, plan) || oldRoute != null && oldRoute.totalVolumeMovedKg() + 1e-6 >= plan.quantityKg())
            return new Step(replace(state, plan.finish()), "Plan completed or ports lost control; paid assets are retained", true);
        var source = state.commercialHubs().stream().filter(h -> h.id().equals(plan.sourceHubId())).findFirst().orElseThrow();
        var destination = state.commercialHubs().stream().filter(h -> h.id().equals(plan.destinationHubId())).findFirst().orElseThrow();
        var owner = state.empires().stream().filter(e -> e.id().equals(plan.empireId())).findFirst().orElseThrow();
        var buyer = destination.activeOrders().get(plan.materialId());
        boolean requested = buyer != null && (destination.stockTargets().containsKey(plan.materialId())
                ? HubReplenishment.requestedKg(state, destination, plan.materialId(), plan.routeId()) > 1e-6 : buyer.demandKg() > buyer.supplyKg());
        if ((oldRoute == null || !oldRoute.carrierCommitted()) && !requested)
            return new Step(replace(state, plan.finish()), "Recipient no longer requests this material", true);
        if (plan.fleetId().isBlank()) {
            var available = state.fleets().stream().filter(f -> plan.empireId().equals(f.ownerEntityId()) && FacilitySupplyShipment.available(state, owner, f))
                    .sorted(Comparator.comparing((Fleet f) -> !FleetPositioning.atHub(state, f, source)).thenComparing(Fleet::id)).findFirst().orElse(null);
            return available == null ? blocked(state, "Waiting for an idle owned cargo carrier")
                    : new Step(replace(state, plan.withFleet(available.id())), "Reserved an existing carrier", true);
        }
        var fleet = state.fleets().stream().filter(f -> f.id().equals(plan.fleetId()) && f.ownerEntityId().equals(plan.empireId())).findFirst().orElse(null);
        if (fleet == null || fleet.ships().isEmpty() || fleet.ships().getFirst().currentHullHealth() <= 0)
            return new Step(replace(state, plan.withFleet("")), "Carrier unavailable; release its reservation", true);
        if (fleet.location().inTransit() || fleet.hasInterstellarOrder() || oldRoute != null && oldRoute.carrierCommitted()) return blocked(state, "Paid transport is in progress");
        if (fleet.guardOrder() != null || fleet.escortOrder() != null || fleet.interceptOrder() != null || ShipRefitPlan.reserved(state, fleet))
            return blocked(state, "Carrier has another committed task");
        if (state.tradeRoutes().stream().anyMatch(r -> !r.id().equals(plan.routeId()) && r.carrierCommitted() && r.assignedFreighterIds().contains(fleet.ships().getFirst().id())))
            return blocked(state, "Carrier is committed to another route");
        double stock = SupplyChainNeeds.stock(source, plan.materialId());
        if (stock <= 1e-6) return blocked(state, "Waiting for actual source production and funded marketplace stock");
        boolean atSource = FleetPositioning.atHub(state, fleet, source) || !fleet.location().inTransit()
                && fleet.location().current().kind() == FleetLocation.Kind.ORBIT && fleet.location().current().entityId().equals(source.entityId());
        var policy = LogisticsPolicy.current();
        if (!atSource) {
            var travel = FacilitySupplyStaging.preview(state, fleet, source);
            if (travel == null || travel.days() > policy.maximumRepositionDays() || travel.costCredits() > policy.maximumDepartureCostCredits()
                    || travel.departure().empires().stream().filter(e -> e.id().equals(owner.id())).findFirst().orElseThrow().treasuryCredits() < policy.treasuryReserveCredits())
                return blocked(state, "Source approach lacks a funded safe route within transport limits");
            return new Step(travel.departure(), "Paid empty approach to the developed source", true);
        }
        var ship = fleet.ships().getFirst();
        var design = state.shipDesigns().stream().filter(d -> d.id().equals(ship.designId())).findFirst().orElse(null);
        if (design == null || ship.storedCargoKg().values().stream().anyMatch(kg -> kg > 1e-6)) return blocked(state, "Carrier design or empty cargo hold is unavailable");
        var sourceOrder = source.activeOrders().get(plan.materialId());
        double localDemand = Math.max(0, sourceOrder.demandKg() - SupplyDevelopmentDemand.requests(state, source.id()).getOrDefault(plan.materialId(), 0.0));
        double keep = Math.max(localDemand * policy.sourceReserveDays(), source.stockTargets().containsKey(plan.materialId()) ? source.stockTargets().get(plan.materialId()).targetStockKg() : 0);
        double amount = Math.min(Math.max(0, stock - keep), Math.min(design.maxCargoMassKg(), plan.quantityKg() - (oldRoute == null ? 0 : oldRoute.totalVolumeMovedKg())));
        if (amount <= 1e-6) return blocked(state, "Source stock is reserved for local consumption");
        if (amount + 1e-6 < Math.min(design.maxCargoMassKg(), plan.quantityKg() - (oldRoute == null ? 0 : oldRoute.totalVolumeMovedKg())))
            return blocked(state, "Waiting for the next planned cargo lot");
        if (FleetPositioning.hubSite(state, destination).kind() == FleetLocation.Kind.SURFACE
                && LocalTravel.surfaceLaunchPlan(state, fleet.withLocation(FleetLocation.at(FleetLocation.Site.surface(destination.entityId())))) == null)
            return blocked(state, "Recipient needs a paid launch service for the carrier's return");
        var route = new TradeRoute(plan.routeId(), "Developed source supply", owner.id(), source.id(), destination.id(), plan.materialId(), amount,
                keep, plan.quantityKg() + (buyer == null ? 0 : buyer.supplyKg()), List.of(ship.id()), oldRoute == null ? 0 : oldRoute.totalVolumeMovedKg(), false);
        if (oldRoute != null) route = new TradeRoute(route.id(), route.name(), route.ownerEntityId(), route.originEntityId(), route.destinationEntityId(),
                route.materialId(), route.transferAmountPerTurnKg(), route.minSourceInventoryThresholdKg(), route.maxDestinationCapacityKg(),
                route.assignedFreighterIds(), route.totalVolumeMovedKg(), false, TradeRoute.LOADING, 0, 0, 0,
                oldRoute.cumulativeOperatingResultCredits(), false, "Continuing the saved procurement quantity", Map.of());
        var quote = new LogisticsProcessor().previewBasket(state, route, Map.of(plan.materialId(), amount), true);
        if (quote == null) return blocked(state, "Shipment needs launch work, fuel, buyer money and a safe funded return");
        double remaining = quote.state().empires().stream().filter(e -> e.id().equals(owner.id())).findFirst().orElseThrow().treasuryCredits();
        if (remaining < policy.treasuryReserveCredits() || owner.treasuryCredits() - remaining > policy.maximumDepartureCostCredits())
            return blocked(state, "Shipment exceeds the transport budget or treasury floor");
        var routes = new ArrayList<>(quote.state().tradeRoutes()); routes.removeIf(r -> r.id().equals(plan.routeId())); routes.add(quote.route());
        return new Step(quote.state().withTradeRoutes(routes), "Purchased real cargo and committed paid delivery", true);
    }
    private static GameState replace(GameState state, SupplyDevelopmentPlan plan) {
        return state.toBuilder().supplyDevelopmentPlans(state.supplyDevelopmentPlans().stream().map(p -> p.id().equals(plan.id()) ? plan : p).toList()).build();
    }
}
