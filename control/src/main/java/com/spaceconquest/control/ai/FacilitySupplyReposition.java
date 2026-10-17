package com.spaceconquest.control.ai;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.logistics.*;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Commits only empty travel; the future paid shipment is a discarded feasibility check. */
public final class FacilitySupplyReposition {
    private FacilitySupplyReposition() {}
    public record Quote(GameState state, TradeRoute route, String fleetId) {}
    public static Quote quote(GameState state, Empire empire, FacilitySupplyNeeds.Need need, String shipId, String sourceId, String routeId, LogisticsPolicy policy) {
        var fleet = state.fleets().stream().filter(item -> item.ships().stream().anyMatch(ship -> ship.id().equals(shipId))).findFirst().orElse(null);
        if (fleet == null || !FacilitySupplyShipment.available(state, empire, fleet)) return null;
        var ship = fleet.ships().getFirst();
        if (!fleet.ownerEntityId().equals(ship.ownerEntityId()) || ship.passengerCount() > 0 || !ship.storedCargoKg().isEmpty()) return null;
        var current = state.commercialHubs().stream().filter(hub -> FleetPositioning.atHub(state, fleet, hub)
                && empire.controlledSystemIds().contains(FleetPositioning.systemForHub(state, hub))).findFirst().orElse(null);
        var source = state.commercialHubs().stream().filter(hub -> sourceId.equals(hub.id())
                && !need.hubId().equals(hub.id()) && empire.controlledSystemIds().contains(FleetPositioning.systemForHub(state, hub))).findFirst().orElse(null);
        if (current == null || source == null || current.id().equals(source.id())) return null;
        var order = source.activeOrders().get(need.materialId()); if (order == null) return null;
        double keep = Math.max(0, order.demandKg() - SupplyDevelopmentDemand.requests(state, source.id()).getOrDefault(need.materialId(), 0.0)) * policy.sourceReserveDays();
        var target = source.stockTargets().get(need.materialId()); if (target != null) keep = Math.max(keep, target.targetStockKg());
        var design = state.shipDesigns().stream().filter(item -> ship.designId().equals(item.id())).findFirst().orElseThrow();
        double amount = Math.min(design.maxCargoMassKg(), Math.min(need.targetKg() - need.positionKg(),
                order.supplyKg() - keep - FacilitySupplyStaging.claimedSourceKg(state, source.id(), need.materialId())));
        if (!Double.isFinite(amount) || amount < need.dailyKg() * policy.minimumDeliveryDays()) return null;
        var travel = FacilitySupplyStaging.preview(state, fleet, source);
        if (travel == null || travel.days() > policy.maximumRepositionDays()) return null;
        var futureEmpire = travel.arrival().empires().stream().filter(item -> empire.id().equals(item.id())).findFirst().orElseThrow();
        var shipment = FacilitySupplyShipment.quote(travel.arrival(), futureEmpire, need, shipId, routeId, policy);
        if (shipment == null || travel.costCredits() + shipment.departureCostCredits() > policy.maximumDepartureCostCredits()) return null;
        if (!empire.id().equals(fleet.ownerEntityId()) && shipment.estimatedOperatingProfitCredits() - Math.max(travel.costCredits(), travel.replacementCostCredits())
                < TransportConstructionPolicy.current().minimumOperatingProfitCredits()) return null;
        double cash = com.spaceconquest.engine.economy.EconomicOwnership.cash(travel.departure(), fleet.ownerEntityId());
        double reserve = empire.id().equals(fleet.ownerEntityId()) ? policy.treasuryReserveCredits()
                : Math.max(policy.treasuryReserveCredits(), TransportConstructionPolicy.current().corporateReserveCredits());
        if (cash < reserve) return null;
        amount = Math.min(amount, shipment.route().onboardKg());
        var reservation = new FacilitySupplyReservation(state.turn() + policy.maximumRepositionDays(), travel.costCredits(),
                policy.maximumDepartureCostCredits(), reserve);
        var route = new TradeRoute(routeId, "AI facility supply", fleet.ownerEntityId(), source.id(), need.hubId(), need.materialId(), amount, keep,
                need.targetKg(), List.of(shipId), 0, false).withPhase(TradeRoute.SUPPLY_REPOSITIONING).withSupplyReservation(reservation)
                .withOperatingCost(travel.costCredits()).withStatus("Repositioning to supply source; goods remain unpurchased until arrival.");
        return new Quote(travel.departure(), route, fleet.id());
    }
}
