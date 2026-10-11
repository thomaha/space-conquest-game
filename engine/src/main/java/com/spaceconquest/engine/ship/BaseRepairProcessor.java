package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import java.util.*;

/** Daily station-wide work budget; each paid increment persists directly in ship condition and market state. */
public final class BaseRepairProcessor {
    private record Repair(GameState state, ShipInstance ship, double used) {}
    private BaseRepairProcessor() {}
    public static GameState advanceDay(GameState state) {
        GameState current = state;
        for (var station : state.orbitalStations()) {
            double remaining = BaseRepairCatalog.capacity(current, station);
            if (remaining <= 1e-9) continue;
            var fleets = new ArrayList<>(current.fleets());
            for (int f = 0; f < fleets.size() && remaining > 1e-9; f++) {
                var fleet = fleets.get(f);
                if (!station.systemId().equals(fleet.currentSystemId()) || fleet.hasInterstellarOrder() || fleet.isInWarp()
                        || !fleet.location().stationaryAt(FleetLocation.Site.docked(station.id()))
                        || !BaseRepairCatalog.accessible(current, station, fleet.ownerEntityId())) continue;
                var ships = new ArrayList<>(fleet.ships());
                var priority = ShipRepairPriority.context(current, fleet);
                for (int s = 0; s < ships.size() && remaining > 1e-9; s++) {
                    var ship = ships.get(s);
                    if (!fleet.ownerEntityId().equals(ship.ownerEntityId()) || ship.currentHullHealth() <= 0) continue;
                    var repair = repair(current, station.id(), ship, remaining, priority);
                    current = repair.state(); remaining -= repair.used(); ships.set(s, repair.ship());
                }
                fleets.set(f, fleet.withShips(ships));
            }
            current = current.withFleets(fleets);
        }
        return current;
    }
    private static Repair repair(GameState state, String station, ShipInstance ship, double capacity, ShipRepairPriority.Context priority) {
        var design = state.shipDesigns().stream().filter(item -> ship.designId().equals(item.id())).findFirst().orElse(null);
        if (design == null) return new Repair(state, ship, 0);
        GameState current = state; double remaining = capacity;
        var damage = ship.moduleDamage();
        for (int mount : ShipRepairPriority.mounts(priority, design, ship)) {
            if (remaining <= 1e-9) break;
            var module = ShipModuleDamage.damageableModule(design.equippedModuleIds().get(mount));
            if (module == null || module.materialCostsKg().isEmpty()) continue;
            var perPoint = new HashMap<String, Double>();
            module.materialCostsKg().forEach((id, kg) -> perPoint.put(id, kg / ShipModuleDamage.DURABILITY));
            var work = BaseRepairPayment.buy(current, station, ship.ownerEntityId(), perPoint,
                    Math.min(remaining, damage.damageByMount().getOrDefault(mount, 0.0)));
            current = work.state(); remaining -= work.points(); damage = damage.repair(mount, work.points());
        }
        var hull = BaseRepairPayment.buy(current, station, ship.ownerEntityId(), Map.of(design.hullMaterialId(), 1.0),
                Math.min(remaining, Math.max(0, ShipConstructionProcessor.COMMISSIONED_HULL_HEALTH - ship.currentHullHealth())));
        current = hull.state(); remaining -= hull.points();
        var repaired = new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), ship.currentHullHealth() + hull.points(),
                ship.currentShieldHealth(), ship.currentFuelKg(), ship.storedCargoKg(), ship.passengerCount(), ship.passengerRaceId(),
                ship.transitMode(), ship.powerState(), ship.supplyState(), damage);
        return new Repair(current, repaired, capacity - remaining);
    }
}
