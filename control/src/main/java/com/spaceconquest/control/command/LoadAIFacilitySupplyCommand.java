package com.spaceconquest.control.command;

import com.spaceconquest.control.ai.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.logistics.*;
import com.spaceconquest.engine.ship.FleetPositioning;
import java.util.*;

/** Resolves a saved source reservation at arrival. A stale or unaffordable shipment releases the carrier. */
public record LoadAIFacilitySupplyCommand(String empireId, String routeId) implements GameCommand {
    @Override public boolean validate(GameState state) {
        if (state == null || empireId == null || routeId == null) return false;
        var route = state.tradeRoutes().stream().filter(item -> routeId.equals(item.id()) && empireId.equals(item.ownerEntityId())
                && TradeRoute.SUPPLY_REPOSITIONING.equals(item.phase()) && item.supplyReservation() != null).findFirst().orElse(null);
        if (route == null) return false;
        var hub = state.commercialHubs().stream().filter(item -> route.originEntityId().equals(item.id())).findFirst().orElse(null);
        return hub != null && state.fleets().stream().anyMatch(fleet -> fleet.ships().stream().anyMatch(ship -> route.assignedFreighterIds().contains(ship.id()))
                && FleetPositioning.atHub(state, fleet, hub));
    }
    @Override public GameState apply(GameState state) {
        if (!validate(state)) return state;
        var route = state.tradeRoutes().stream().filter(item -> routeId.equals(item.id())).findFirst().orElseThrow();
        var prepared = state.withTradeRoutes(state.tradeRoutes().stream().filter(item -> !routeId.equals(item.id())).toList());
        var empire = com.spaceconquest.engine.economy.EconomicOwnership.empireFor(prepared, empireId);
        var policy = LogisticsPolicy.current();
        var need = empire == null ? null : FacilitySupplyNeeds.shortages(prepared, empire, policy).stream()
                .filter(item -> route.destinationEntityId().equals(item.hubId()) && route.materialId().equals(item.materialId())).findFirst().orElse(null);
        if (need != null) need = new FacilitySupplyNeeds.Need(need.hubId(), need.materialId(), need.dailyKg(), need.positionKg(),
                Math.min(need.targetKg(), need.positionKg() + route.transferAmountPerTurnKg()), need.generationFuel());
        var shipment = need == null || !route.reservesSupply(state.turn()) ? null : FacilitySupplyShipment.quote(prepared, empire, need,
                route.assignedFreighterIds().getFirst(), routeId, policy);
        var reservation = route.supplyReservation();
        double remaining = shipment == null ? 0 : com.spaceconquest.engine.economy.EconomicOwnership.cash(shipment.state(), empireId);
        if (shipment == null || remaining < Math.max(policy.treasuryReserveCredits(), reservation.treasuryFloorCredits())
                || shipment.departureCostCredits() + reservation.spentCredits() > Math.min(policy.maximumDepartureCostCredits(), reservation.maximumCostCredits())) {
            var released = FacilitySupplyStaging.release(route, "Supply claim released: arrival stock, demand, control or funding changed.");
            return state.withTradeRoutes(state.tradeRoutes().stream().map(item -> routeId.equals(item.id()) ? released : item).toList());
        }
        var committed = shipment.route().withOperatingCost(reservation.spentCredits());
        var routes = new ArrayList<>(shipment.state().tradeRoutes()); routes.add(committed);
        return shipment.state().withTradeRoutes(routes);
    }
}
