package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import java.util.*;

/** Tick-owned onboard repair: providers pay from their cargo and share bounded work with their own fleet. */
public final class ShipRepairProcessor {
    private ShipRepairProcessor() {}
    public static List<Fleet> advanceDay(GameState state, List<Fleet> poweredFleets) {
        var powered = state.withFleets(poweredFleets);
        return poweredFleets.stream().map(fleet -> repair(powered, fleet)).toList();
    }

    static Fleet repair(GameState state, Fleet fleet) {
        if (ShipRefitPlan.reserved(state, fleet)) return fleet;
        if (Fleet.MODE_RECOVERY.equals(fleet.interstellarMode()) || (Fleet.MODE_POWER_INTERRUPTED.equals(fleet.interstellarMode())
                || fleet.location().localFlight() != null && fleet.location().localFlight().interrupted())
                && !PropulsionDamage.damaged(state, fleet) && !ShipElectricalDamage.damaged(state, fleet)) return fleet;
        var ships = new ArrayList<>(fleet.ships());
        // Freeze capacity before work: restoring a disabled workshop cannot grant an extra turn of work.
        double[] capacity = fleet.ships().stream().mapToDouble(ship -> capacity(state, fleet, ship)).toArray();
        if (Arrays.stream(capacity).noneMatch(points -> points > 1e-9)) return fleet;
        var priority = ShipRepairPriority.context(state, fleet);
        for (int provider = 0; provider < ships.size(); provider++) {
            double remaining = capacity[provider];
            if (remaining <= 0) continue;
            var materials = new HashMap<>(ships.get(provider).storedCargoKg());
            var targets = new ArrayList<Integer>(); targets.add(provider);
            for (int i = 0; i < ships.size(); i++) if (i != provider) targets.add(i);
            for (int target : targets) {
                var ship = ships.get(target); var design = design(state, ship);
                if (design == null || ship.currentHullHealth() <= 0 || !fleet.ownerEntityId().equals(ship.ownerEntityId())) continue;
                var damage = ship.moduleDamage();
                for (int mount : ShipRepairPriority.mounts(priority, design, ship)) {
                    if (remaining <= 1e-9) break;
                    var module = ShipModuleDamage.damageableModule(design, design.equippedModuleIds().get(mount));
                    if (module == null || module.materialCostsKg().isEmpty()) continue;
                    var perPoint = new HashMap<String, Double>();
                    module.materialCostsKg().forEach((id, kg) -> perPoint.put(id, kg / ShipModuleDamage.DURABILITY));
                    double points = spend(materials, perPoint, Math.min(remaining, damage.damageByMount().getOrDefault(mount, 0.0)));
                    damage = damage.repair(mount, points); remaining -= points;
                }
                double hullPoints = spend(materials, Map.of(design.hullMaterialId(), 1.0),
                        Math.min(remaining, Math.max(0, ShipConstructionProcessor.COMMISSIONED_HULL_HEALTH - ship.currentHullHealth())));
                remaining -= hullPoints;
                ships.set(target, new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(),
                        ship.currentHullHealth() + hullPoints, ship.currentShieldHealth(), ship.currentFuelKg(),
                        ship.storedCargoKg(), ship.passengerCount(), ship.passengerRaceId(), ship.transitMode(),
                        ship.powerState(), ship.supplyState(), damage));
                if (remaining <= 1e-9) break;
            }
            var host = ships.get(provider);
            ships.set(provider, new ShipInstance(host.id(), host.designId(), host.ownerEntityId(), host.currentHullHealth(),
                    host.currentShieldHealth(), host.currentFuelKg(), materials, host.passengerCount(), host.passengerRaceId(),
                    host.transitMode(), host.powerState(), host.supplyState(), host.moduleDamage()));
        }
        return fleet.withShips(ships);
    }

    static double capacity(GameState state, Fleet fleet, ShipInstance ship) {
        var design = design(state, ship); var power = ship.powerState();
        if (design == null || design.powerProfile() == null || power == null || ship.currentHullHealth() <= 0
                || !fleet.ownerEntityId().equals(ship.ownerEntityId()) || power.lastUnmetEssentialKwh() > 1e-6
                || power.unmetDriveKwh() > 1e-6) return 0;
        if (!ShipServicePower.funded(ship, ShipServicePower.WORKSHOPS)) return 0;
        if (state.tradeRoutes().stream().anyMatch(route -> route.assignedFreighterIds().contains(ship.id())
                && (route.isActive() || route.onboardKg() > 1e-9))) return 0;
        double points = ship.moduleDamage().capability(design, "repairPointsPerDay");
        long workshops = design.equippedModuleIds().stream().filter(ShipRepairModuleCatalog.WORKSHOP::equals).count();
        return design.powerProfile().hotelKw() + 1e-9 >= workshops * ShipRepairModuleCatalog.module(ShipRepairModuleCatalog.WORKSHOP).powerDrawKw()
                ? points : 0;
    }

    private static double spend(Map<String, Double> available, Map<String, Double> perPoint, double requested) {
        double points = requested;
        for (var ingredient : perPoint.entrySet()) {
            double kg = available.getOrDefault(ingredient.getKey(), 0.0);
            if (!Double.isFinite(kg) || kg < 0) return 0;
            points = Math.min(points, kg / ingredient.getValue());
        }
        if (points <= 1e-9) return 0;
        for (var ingredient : perPoint.entrySet()) {
            double kg = Math.max(0, available.getOrDefault(ingredient.getKey(), 0.0) - ingredient.getValue() * points);
            if (kg <= 1e-9) available.remove(ingredient.getKey()); else available.put(ingredient.getKey(), kg);
        }
        return points;
    }
    private static ShipDesign design(GameState state, ShipInstance ship) {
        return state.shipDesigns().stream().filter(item -> item.id().equals(ship.designId())).findFirst().orElse(null);
    }
}
