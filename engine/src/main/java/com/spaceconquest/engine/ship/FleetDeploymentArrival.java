package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import java.util.*;

/** Bounded discarded electrical and physical arrival projection, including the final local approach. */
public final class FleetDeploymentArrival {
    private FleetDeploymentArrival() {}
    public static Fleet project(GameState state, Fleet source, String system, LocalTravel.Plan departure,
                                InterstellarTravel.Plan crossing, FleetLocation.Site destination, double reserveFraction) {
        var deep = FleetLocation.Site.deepSpace();
        var outbound = departure == null ? source : LocalTravel.arrivalPreview(state, source, deep, departure);
        for (var ship : outbound.ships()) {
            var feed = crossing.reactorFuelBudgetKg().get(ship.id());
            if (feed != null && ship.storedCargoKg().getOrDefault(feed.materialId(), 0.0) + 1e-6 < feed.quantityKg()) return null;
        }
        outbound = InterstellarTravel.commitReactorFuel(outbound, crossing);
        var ships = new ArrayList<ShipInstance>();
        for (var ship : outbound.ships()) {
            var design = FleetSupplySimulation.design(state, ship);
            if (design == null || design.powerProfile(ship) == null) return null;
            double fuel = ship.currentFuelKg() - crossing.fuelBudgetKg().getOrDefault(ship.id(), 0.0);
            if (fuel < design.fuelCapacityKg() * reserveFraction) return null;
            var budget = ShipShieldPower.forecast(ship, design, ShipPowerPhases.withActivity(
                    ShipPowerPhases.crossing(design.powerProfile(ship), crossing), ShipServicePower.activity(state, source)),
                    ShipSolarEnvironment.DARK, FleetShieldPolicy.recharge(state, source, true));
            if (!budget.ready()) return null;
            var arrived = budget.ship();
            ships.add(new ShipInstance(arrived.id(), arrived.designId(), arrived.ownerEntityId(), arrived.currentHullHealth(), arrived.currentShieldHealth(),
                    Math.max(0, fuel), arrived.storedCargoKg(), arrived.passengerCount(), arrived.passengerRaceId(), arrived.transitMode(), arrived.powerState(), arrived.supplyState(), arrived.moduleDamage()));
        }
        var arrival = new Fleet(source.id(), source.name(), source.ownerEntityId(), system, "", 0, 0, 0, false, source.fleetStance(), ships, FleetLocation.at(deep))
                .withFuelPolicy(source.fuelPolicy()).withShieldPolicy(source.shieldPolicy());
        var projected = FleetReturnTravel.replace(state, arrival);
        var approach = destination.equals(deep) ? null : FleetReturnTravel.localPlan(projected, arrival, destination);
        if (!destination.equals(deep) && approach == null) return null;
        if (approach != null) arrival = LocalTravel.arrivalPreview(projected, arrival, destination, approach);
        projected = FleetReturnTravel.replace(projected, arrival);
        if (FleetWeaponRecovery.needs(projected, arrival)) return null;
        for (var ship : arrival.ships()) {
            var design = FleetSupplySimulation.design(state, ship); var profile = design.powerProfile(ship);
            if (ship.currentFuelKg() + 1e-6 < design.fuelCapacityKg() * reserveFraction
                    || !ShipArrivalReserve.check(profile, ShipPowerProcessor.reserves(ship), profile.essentialKw(ship, design),
                    ShipPowerProcessor.cargoKw(profile, ship, design), ShipSolarEnvironment.at(projected, arrival, destination)).ready()) return null;
        }
        return arrival;
    }
}
