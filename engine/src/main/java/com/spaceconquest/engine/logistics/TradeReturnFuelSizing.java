package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Pure bounded working-store forecasts; only TradeReturnFuel buys the selected real inventory. */
final class TradeReturnFuelSizing {
    private TradeReturnFuelSizing() {}

    static Fleet prepare(GameState state, Fleet arrival, CommercialHub destination) {
        if (arrival.ships().stream().anyMatch(ship -> FleetSupplySimulation.design(state, ship) == null)) return null;
        var port = state.commercialHubs().stream().filter(hub -> FleetPositioning.atHub(state, arrival, hub))
                .findFirst().orElse(null);
        if (port == null) return null;
        var orders = new HashMap<>(port.activeOrders());
        double available = arrival.ships().stream().mapToDouble(ship -> {
            var design = FleetSupplySimulation.design(state, ship);
            var power = design.powerProfile();
            return design.fuelCapacityKg() + design.maxCargoMassKg()
                    + (power == null ? 0 : power.generatorTankKg() + power.reactorTankKg());
        }).sum() * 2 + 1;
        for (var ship : arrival.ships()) {
            var design = FleetSupplySimulation.design(state, ship);
            var drive = PropulsionCatalog.mainDrive(design.equippedModuleIds());
            if (drive != null) {
                drive.propellantMaterials(1).keySet().forEach(id -> offer(orders, id, available));
                PropulsionCatalog.reactorFuels(drive.moduleId()).forEach(fuel -> offer(orders, fuel.materialId(), available));
            }
            if (design.powerProfile() != null) design.powerProfile().fuels().values().forEach(fuel ->
                    fuel.materials(1).keySet().forEach(id -> offer(orders, id, available)));
        }
        var planningPort = port.withInventory(state.turn(), port.currentStoredWeightKg(), orders);
        var planning = state.withCommercialHubs(state.commercialHubs().stream()
                .map(hub -> hub.id().equals(port.id()) ? planningPort : hub).toList());
        var prepared = TradeLegReadiness.prepareFull(planning, arrival, destination);
        var full = prepared.fleets().stream().filter(fleet -> fleet.id().equals(arrival.id())).findFirst().orElseThrow();
        if (!TradeLegReadiness.inspect(state, full, destination).ready()) return null;
        if (TradeFuelEconomy.preferFull(state, arrival, port)) return full;
        double main = scale(state, arrival, full, destination, true, 1);
        double power = scale(state, arrival, full, destination, false, main);
        var selected = blend(arrival, full, main, power);
        return TradeLegReadiness.inspect(state, selected, destination).ready() ? selected : full;
    }

    private static void offer(Map<String, MarketOrder> orders, String id, double available) {
        orders.put(id, new MarketOrder(id, available, 0, 0, 0));
    }

    private static double scale(GameState state, Fleet base, Fleet full, CommercialHub destination,
                                boolean main, double other) {
        double low = 0, high = 1;
        for (int attempt = 0; attempt < 10; attempt++) {
            double middle = (low + high) / 2;
            var candidate = blend(base, full, main ? middle : other, main ? other : middle);
            if (TradeLegReadiness.inspect(state, candidate, destination).ready()) high = middle;
            else low = middle;
        }
        // Keep two percent headroom above the accepted requirement, bounded by proven working capacity.
        return Math.min(1, high * 1.02 + 1e-4);
    }

    private static Fleet blend(Fleet base, Fleet full, double main, double power) {
        var ships = new ArrayList<ShipInstance>();
        for (var ship : base.ships()) {
            var supplied = full.ships().stream().filter(item -> item.id().equals(ship.id())).findFirst().orElseThrow();
            var previous = ShipPowerProcessor.reserves(ship);
            var next = ShipPowerProcessor.reserves(supplied);
            var electrical = new ShipPowerState(materials(previous.generatorMaterialsKg(), next.generatorMaterialsKg(), power),
                    next.chemicalMixture(), next.reactorFuel(), previous.batteryChargeKwh(), previous.arraysDeployed(),
                    previous.arrayCondition(), previous.orientationFraction(), previous.unmetEssentialHours(),
                    previous.unmetDriveKwh(), previous.unmetCargoKwh(), previous.lastUnmetEssentialKwh(),
                    previous.chargedInputKwhToday(), previous.cargoPreservation(), previous.rescueStatus(), previous.shedServices());
            ships.add(new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(), ship.currentShieldHealth(),
                    ship.currentFuelKg() + Math.max(0, supplied.currentFuelKg() - ship.currentFuelKg()) * main,
                    materials(ship.storedCargoKg(), supplied.storedCargoKg(), main), ship.passengerCount(),
                    ship.passengerRaceId(), ship.transitMode(), electrical, ship.supplyState(), ship.moduleDamage()));
        }
        return base.withShips(ships);
    }

    private static Map<String, Double> materials(Map<String, Double> base, Map<String, Double> full, double scale) {
        var result = new HashMap<>(base);
        full.forEach((id, mass) -> result.put(id, base.getOrDefault(id, 0.0)
                + Math.max(0, mass - base.getOrDefault(id, 0.0)) * scale));
        return result;
    }
}
