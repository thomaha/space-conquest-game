package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;

/** Existing ship motivating a paid new build; the original ship is retained without assumed salvage. */
public record ShipReplacementPlan(String sourceShipId) {
    public ShipReplacementPlan {
        if (sourceShipId == null || sourceShipId.isBlank()) throw new IllegalArgumentException("Replacement source is required");
    }
    /** Physical readiness shared by the AI decision and every saved retirement installment. */
    public static boolean operational(GameState state, String sourceId, String replacementId, double minimumCondition) {
        if (sourceId == null || sourceId.equals(replacementId)) return false;
        var source = ShipRetirementPlan.fleet(state, sourceId); var fleet = replacementId == null ? null : ShipRetirementPlan.fleet(state, replacementId);
        if (source == null || fleet == null || !source.ownerEntityId().equals(fleet.ownerEntityId()) || !source.currentSystemId().equals(fleet.currentSystemId())
                || fleet.hasInterstellarOrder() || fleet.location().inTransit() || fleet.isInWarp() || ShipRefitPlan.reserved(state, replacementId)
                || state.shipyardLedger().deployments().containsKey(replacementId)) return false;
        var ship = fleet.ships().stream().filter(item -> replacementId.equals(item.id())).findFirst().orElseThrow();
        var original = source.ships().stream().filter(item -> sourceId.equals(item.id())).findFirst().orElseThrow();
        var design = FleetSupplySimulation.design(state, ship); var old = FleetSupplySimulation.design(state, original);
        if (design == null || old == null || !java.util.Objects.equals(design.role(), old.role()) || !original.ownerEntityId().equals(ship.ownerEntityId())
                || ship.currentHullHealth() <= 0 || ship.currentHullHealth() < ShipConstructionProcessor.COMMISSIONED_HULL_HEALTH * minimumCondition
                || design.powerProfile() == null || PropulsionDamage.condition(design, ship) < minimumCondition) return false;
        var operative = fleet.withShips(java.util.List.of(ship));
        return ShipPowerForecast.ready(ShipPowerForecast.departure(state, operative, fleet.location().current(), null, null)) && !FleetWeaponRecovery.needs(state, operative);
    }
    public static boolean committed(GameState state, String shipId) {
        String owner = state.fleets().stream().flatMap(fleet -> fleet.ships().stream()).filter(ship -> shipId.equals(ship.id())).map(ShipInstance::ownerEntityId).findFirst().orElse("");
        if (pending(state, shipId)) return true;
        String replacement = state.shipyardLedger().replacements().get(shipId);
        return replacement != null && state.fleets().stream().flatMap(fleet -> fleet.ships().stream())
                .anyMatch(ship -> replacement.equals(ship.id()) && owner.equals(ship.ownerEntityId()) && ship.currentHullHealth() > 0);
    }
    public static boolean pending(GameState state, String shipId) {
        var fleet = ShipRetirementPlan.fleet(state, shipId);
        return fleet != null && state.shipConstructionOrders().stream().anyMatch(order -> order.replacement() != null
                && fleet.ownerEntityId().equals(order.ownerEntityId()) && shipId.equals(order.replacement().sourceShipId()));
    }
}
