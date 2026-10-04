package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.GameState;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Live compatible stock at a trading port. Availability is informational and does not reserve goods. */
public final class FleetPortReadiness {
    public record Report(boolean available, Map<String, Double> refillKg, List<String> shortages, String explanation) {
        public Report { refillKg = Map.copyOf(refillKg); shortages = List.copyOf(shortages); }
    }
    private FleetPortReadiness() {}

    public static Report inspect(GameState state, Fleet fleet, CommercialHub hub) {
        if (hub == null || FleetPositioning.hubSite(state, hub) == null)
            return new Report(false, Map.of(), List.of(), "No accessible trading port.");
        if (state.orbitalStations().stream().anyMatch(station -> station.id().equals(hub.entityId()) && !station.isOperational()))
            return new Report(false, Map.of(), List.of(), "The destination station is not operational.");
        Map<String, Double> request = new HashMap<>();
        for (var ship : fleet.ships()) {
            var design = FleetSupplySimulation.design(state, ship);
            if (design == null) return new Report(false, request, List.of(ship.id()), "Unknown ship equipment.");
            var drive = PropulsionCatalog.mainDrive(design.equippedModuleIds());
            if (drive != null) {
                // At least one kilogram probes feed availability even when current tanks are full.
                double refill = Math.max(1, design.fuelCapacityKg() - ship.currentFuelKg());
                add(request, drive.propellantMaterials(refill));
                var feed = PropulsionCatalog.preferredReactorFuel(drive, ship);
                if (feed != null && feed.kgPerPropellantKg() > 0) request.merge(feed.materialId(),
                        Math.max(feed.kgPerPropellantKg(), design.fuelCapacityKg() * feed.kgPerPropellantKg()
                        - ship.storedCargoKg().getOrDefault(feed.materialId(), 0.0)), Double::sum);
            }
            var p = design.powerProfile();
            if (p == null) continue;
            var power = ShipPowerProcessor.reserves(ship);
            for (String id : List.of(power.chemicalMixture(), power.reactorFuel())) {
                var fuel = p.fuels().get(id);
                if (fuel == null || (fuel.oxidizerId() == null ? p.fissionKw() <= 0 : p.chemicalKw() <= 0)) continue;
                double capacity = fuel.oxidizerId() == null ? p.reactorTankKg() : p.generatorTankKg();
                double occupied = fuel.materials(1).keySet().stream().mapToDouble(material ->
                        power.generatorMaterialsKg().getOrDefault(material, 0.0)).sum();
                add(request, fuel.materials(Math.max(Math.min(1, capacity), capacity - occupied)));
            }
        }
        var shortages = request.entrySet().stream().filter(entry -> {
            var stock = hub.activeOrders().get(entry.getKey());
            return stock == null || !Double.isFinite(stock.supplyKg()) || !Double.isFinite(stock.pricePerKg())
                    || stock.pricePerKg() < 0 || stock.supplyKg() + 1e-6 < entry.getValue();
        }).map(Map.Entry::getKey).sorted().toList();
        return new Report(shortages.isEmpty(), request, shortages, shortages.isEmpty()
                ? "Compatible port stock is available now; purchases still require funds and local access."
                : "Port refill stock is insufficient: " + String.join(", ", shortages) + ".");
    }

    private static void add(Map<String, Double> target, Map<String, Double> materials) {
        materials.forEach((id, kg) -> target.merge(id, kg, Double::sum));
    }
}
