package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Cheap, well-stocked ports justify carrying a full working fuel allowance. */
final class TradeFuelEconomy {
    private TradeFuelEconomy() {}

    static boolean preferFull(GameState state, Fleet fleet, CommercialHub port) {
        if (port == null || !available(state, port)) return false;
        var required = new HashMap<String, Double>();
        for (var ship : fleet.ships()) {
            var design = FleetSupplySimulation.design(state, ship);
            var drive = design == null ? null : PropulsionCatalog.mainDrive(design.equippedModuleIds());
            if (drive == null) return false;
            double refill = Math.max(0, design.fuelCapacityKg() - ship.currentFuelKg());
            drive.propellantMaterials(refill).forEach((id, kg) -> required.merge(id, kg, Double::sum));
            var feed = PropulsionCatalog.preferredReactorFuel(drive, ship);
            if (feed != null) required.merge(feed.materialId(), refill * feed.kgPerPropellantKg(), Double::sum);
            var power = design.powerProfile();
            if (power == null) continue;
            var reserves = ShipPowerProcessor.reserves(ship);
            for (String id : List.of(reserves.chemicalMixture(), reserves.reactorFuel())) {
                var fuel = power.fuels().get(id);
                if (fuel == null || (fuel.oxidizerId() == null ? power.fissionKw() <= 0 : power.chemicalKw() <= 0)) continue;
                double occupied = fuel.materials(1).keySet().stream().mapToDouble(material ->
                        reserves.generatorMaterialsKg().getOrDefault(material, 0.0)).sum();
                double capacity = fuel.oxidizerId() == null ? power.reactorTankKg() : power.generatorTankKg();
                fuel.materials(Math.max(0, capacity - occupied)).forEach((material, kg) -> required.merge(material, kg, Double::sum));
            }
        }
        required.values().removeIf(kg -> kg <= 1e-6);
        if (required.isEmpty() || !stocked(port, required)) return false;
        double price = price(port, required);
        double cheapest = state.commercialHubs().stream().filter(hub -> available(state, hub) && stocked(hub, required))
                .mapToDouble(hub -> price(hub, required)).min().orElse(Double.POSITIVE_INFINITY);
        return Double.isFinite(price) && price <= cheapest * 1.10 + 1e-9;
    }

    private static boolean available(GameState state, CommercialHub hub) {
        return FleetPositioning.hubSite(state, hub) != null
                && state.orbitalStations().stream().noneMatch(station -> station.id().equals(hub.entityId()) && !station.isOperational());
    }

    private static boolean stocked(CommercialHub hub, Map<String, Double> required) {
        return required.entrySet().stream().allMatch(entry -> {
            var order = hub.activeOrders().get(entry.getKey());
            var target = hub.stockTargets().get(entry.getKey());
            double buffer = target == null ? 0 : target.reorderThresholdKg();
            return order != null && order.supplyKg() >= entry.getValue() + buffer;
        });
    }

    private static double price(CommercialHub hub, Map<String, Double> required) {
        double total = 0;
        for (var entry : required.entrySet()) {
            double price = hub.activeOrders().get(entry.getKey()).pricePerKg();
            if (!Double.isFinite(price) || price < 0) return Double.POSITIVE_INFINITY;
            total += entry.getValue() * price;
        }
        return total;
    }
}
