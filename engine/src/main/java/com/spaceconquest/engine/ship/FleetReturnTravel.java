package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;

/** Funded same-system return planning from actual stationary or recovered circular motion. */
public final class FleetReturnTravel {
    private FleetReturnTravel() {}
    public static boolean available(Fleet fleet) {
        return !fleet.hasInterstellarOrder() && !fleet.isInWarp() && fleet.flightMotion() == null
                && (!fleet.location().inTransit() || RecoveredParkingTravel.ready(fleet));
    }
    public static LocalTravel.Plan plan(GameState state, Fleet fleet, FleetLocation.Site site) {
        var convoy = FleetEscortReturn.convoy(state, fleet);
        if (convoy != null && site != null && site.equals(FleetEscortReturn.site(convoy))) return FleetEscortReturn.plan(state, fleet, convoy);
        return localPlan(state, fleet, site);
    }
    public static LocalTravel.Plan localPlan(GameState state, Fleet fleet, FleetLocation.Site site) {
        if (site == null || !available(fleet) || fleet.location().stationaryAt(site)) return null;
        var plan = LocalTravel.plan(state, fleet, site);
        return plan != null && ShipPowerForecast.ready(ShipPowerForecast.departure(state, fleet, site, plan, null)) ? plan : null;
    }
    public static boolean ready(GameState state, Fleet fleet, FleetLocation.Site site) {
        var convoy = FleetEscortReturn.convoy(state, fleet);
        if (convoy != null && site != null && site.equals(FleetEscortReturn.site(convoy))) return plan(state, fleet, site) != null;
        return fleet.location().stationaryAt(site) || plan(state, fleet, site) != null;
    }
    public static GameState replace(GameState state, Fleet fleet) {
        return state.withFleets(state.fleets().stream().map(old -> old.id().equals(fleet.id()) ? fleet : old).toList());
    }
    public static Fleet stage(Fleet fleet, FleetProtectionAssignment.Stage stage, String target) {
        return fleet.withInterceptOrder(fleet.interceptOrder().withProtection(fleet.interceptOrder().protection().at(stage, target)));
    }
    public static double minimumFuel(GameState state, Fleet fleet, ShipInstance ship, FleetLocation.Site site) {
        var design = FleetSupplySimulation.design(state, ship);
        if (design == null) return Double.POSITIVE_INFINITY;
        double low = 0, high = design.fuelCapacityKg();
        if (!ready(state, fleet.withShips(java.util.List.of(fuel(ship, high))), site)) return Double.POSITIVE_INFINITY;
        for (int i = 0; i < 12; i++) {
            double middle = (low + high) / 2;
            if (ready(state, fleet.withShips(java.util.List.of(fuel(ship, middle))), site)) high = middle; else low = middle;
        }
        return Math.min(design.fuelCapacityKg(), high * 1.02 + 1e-6);
    }
    private static ShipInstance fuel(ShipInstance ship, double kg) {
        return new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(), ship.currentShieldHealth(),
                kg, ship.storedCargoKg(), ship.passengerCount(), ship.passengerRaceId(), ship.transitMode(), ship.powerState(), ship.supplyState(), ship.moduleDamage());
    }
}
