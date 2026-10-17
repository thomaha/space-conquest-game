package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import java.util.Map;

/** Saved existing-ship work. The order holds the frozen physical bill and target blueprint. */
public record ShipRefitPlan(String shipId, String sourceDesignId, Map<String, String> retainedIdentities) {
    public ShipRefitPlan { retainedIdentities = Map.copyOf(retainedIdentities); }
    public String problem(GameState state, ShipConstructionOrder order, ShipDesign target) {
        var fleet = state.fleets().stream().filter(item -> item.ships().stream().anyMatch(ship -> shipId.equals(ship.id()))).findFirst().orElse(null);
        if (fleet == null) return "Refit ship unavailable";
        var ship = fleet.ships().stream().filter(item -> shipId.equals(item.id())).findFirst().orElseThrow();
        if (!sourceDesignId.equals(ship.designId()) || ship.currentHullHealth() <= 0
                || !order.ownerEntityId().equals(ship.ownerEntityId()) || !order.ownerEntityId().equals(fleet.ownerEntityId())) return "Refit ship or reservation unavailable";
        if (!order.systemId().equals(fleet.currentSystemId()) || !order.yardBodyId().equals(fleet.location().current().entityId())
                || fleet.location().inTransit() || fleet.hasInterstellarOrder() || fleet.isInWarp()) return "Refit ship must remain at its yard";
        var source = FleetSupplySimulation.design(state, ship);
        if (source == null) return "Source blueprint unavailable";
        var changes = ShipModelComparison.compare(source, target);
        var damage = ship.moduleDamage().remap(source, target, retainedIdentities);
        var blockers = ShipRefitStores.blockers(state, ship, source, target, changes, damage);
        return blockers.isEmpty() ? null : blockers.getFirst();
    }
    public Fleet complete(GameState state, Fleet fleet, ShipDesign target) {
        return fleet.withShips(fleet.ships().stream().map(ship -> {
            if (!shipId.equals(ship.id())) return ship;
            var source = FleetSupplySimulation.design(state, ship);
            return new ShipInstance(ship.id(), target.id(), ship.ownerEntityId(), ship.currentHullHealth(), ship.currentShieldHealth(),
                    ship.currentFuelKg(), ship.storedCargoKg(), ship.passengerCount(), ship.passengerRaceId(), ship.transitMode(),
                    ship.powerState(), ship.supplyState(), ship.moduleDamage().remap(source, target, retainedIdentities));
        }).toList());
    }
    public static boolean reserved(GameState state, String shipId) {
        return state != null && state.shipConstructionOrders().stream().anyMatch(order -> order.refit() != null && shipId.equals(order.refit().shipId())
                || order.retirement() != null && shipId.equals(order.retirement().shipId()));
    }
    public static boolean reserved(GameState state, Fleet fleet) {
        return fleet != null && fleet.ships().stream().anyMatch(ship -> reserved(state, ship.id()));
    }
}
