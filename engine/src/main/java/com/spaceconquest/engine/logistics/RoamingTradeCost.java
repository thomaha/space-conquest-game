package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.*;
import java.util.HashMap;
import java.util.Map;

/** Replacement cost of consumed working fuel and actual planned travel time, not speculative return costs. */
final class RoamingTradeCost {
    record Quote(double days, double fuelCredits, double launchCredits) {}
    private RoamingTradeCost() {}

    static Quote quote(GameState state, Fleet original, CommercialHub source, CommercialHub target) {
        Fleet fleet = original;
        Map<String, Double> materials = new HashMap<>();
        double days = 0, launch = 0;
        if (fleet.location().current().kind() == FleetLocation.Kind.SURFACE) {
            var provider = LocalTravel.surfaceLaunchPlan(state, fleet);
            if (provider == null) return null;
            launch = LaunchService.payerOperatingCost(state, provider, fleet.ownerEntityId());
        }
        boolean crossing = !fleet.currentSystemId().equals(FleetPositioning.systemForHub(state, target));
        var firstSite = crossing ? FleetLocation.Site.deepSpace() : FleetPositioning.hubSite(state, target);
        var local = LocalTravel.plan(state, fleet, firstSite);
        if (local != null) {
            days += Math.ceil(local.days());
            addPropellant(state, fleet, materials, local.propellantKg(), local.reactorFuelKg());
            fleet = power(state, fleet, Math.ceil(local.days()) * 24, materials);
            if (fleet == null) return null;
            fleet = LocalTravel.depart(fleet, firstSite, local).withLocation(FleetLocation.at(firstSite));
        } else if (!fleet.location().isAt(firstSite)) return null;
        if (crossing) {
            var plan = TradeLegReadiness.crossing(state, fleet, target);
            if (plan == null) return null;
            days += Math.ceil(plan.days());
            addPropellant(state, fleet, materials, plan.fuelBudgetKg(), plan.reactorFuelBudgetKg());
            if (Fleet.MODE_SUBLIGHT.equals(plan.mode())) {
                var forecast = FleetPropulsionSupply.forecast(state, fleet, plan);
                if (!ShipPowerForecast.ready(forecast.electrical())) return null;
                generatorUse(fleet, forecast.arrival(), materials);
                fleet = forecast.arrival();
            } else {
                fleet = power(state, fleet, Math.ceil(plan.days()) * 24, materials);
                if (fleet == null) return null;
            }
            fleet = new Fleet(fleet.id(), fleet.name(), fleet.ownerEntityId(), FleetPositioning.systemForHub(state, target),
                    "", 0, 0, 0, false, fleet.fleetStance(), fleet.ships(), FleetLocation.at(FleetLocation.Site.deepSpace()));
            var dock = LocalTravel.plan(state, fleet, FleetPositioning.hubSite(state, target));
            if (dock == null) return null;
            days += Math.ceil(dock.days());
            addPropellant(state, fleet, materials, dock.propellantKg(), dock.reactorFuelKg());
            if (power(state, fleet, Math.ceil(dock.days()) * 24, materials) == null) return null;
        }
        double fuel = 0;
        for (var item : materials.entrySet()) {
            var order = source.activeOrders().get(item.getKey());
            if (order == null || !Double.isFinite(order.pricePerKg()) || order.pricePerKg() < 0) return null;
            fuel += item.getValue() * order.pricePerKg();
        }
        return Double.isFinite(fuel) ? new Quote(Math.max(1, days), fuel, launch) : null;
    }

    private static Fleet power(GameState state, Fleet fleet, double hours, Map<String, Double> materials) {
        var ships = new java.util.ArrayList<ShipInstance>();
        for (var ship : fleet.ships()) {
            var design = FleetSupplySimulation.design(state, ship);
            if (design == null || design.powerProfile() == null) return null;
            var p = design.powerProfile();
            var interval = ShipPowerProcessor.interval(p, ShipPowerProcessor.reserves(ship), hours, 0,
                    p.essentialKw(ship, design), ShipPowerProcessor.cargoKw(p, ship, design), p.driveKw());
            if (!interval.supplied()) return null;
            ships.add(ship.withPowerState(interval.state()));
        }
        var next = fleet.withShips(ships);
        generatorUse(fleet, next, materials);
        return next;
    }

    private static void generatorUse(Fleet before, Fleet after, Map<String, Double> materials) {
        for (var ship : before.ships()) {
            var arrived = after.ships().stream().filter(item -> item.id().equals(ship.id())).findFirst().orElseThrow();
            ShipPowerProcessor.reserves(ship).generatorMaterialsKg().forEach((id, kg) -> {
                double consumed = kg - ShipPowerProcessor.reserves(arrived).generatorMaterialsKg().getOrDefault(id, 0.0);
                if (consumed > 1e-6) materials.merge(id, consumed, Double::sum);
            });
        }
    }

    private static void addPropellant(GameState state, Fleet fleet, Map<String, Double> materials,
                                      Map<String, Double> tanks, Map<String, InterstellarTravel.ReactorFuelUse> feeds) {
        for (var ship : fleet.ships()) {
            var design = FleetSupplySimulation.design(state, ship);
            var drive = PropulsionCatalog.mainDrive(design.equippedModuleIds());
            if (drive != null) drive.propellantMaterials(tanks.getOrDefault(ship.id(), 0.0))
                    .forEach((id, kg) -> { if (kg > 1e-6) materials.merge(id, kg, Double::sum); });
        }
        feeds.values().forEach(feed -> materials.merge(feed.materialId(), feed.quantityKg(), Double::sum));
    }
}
