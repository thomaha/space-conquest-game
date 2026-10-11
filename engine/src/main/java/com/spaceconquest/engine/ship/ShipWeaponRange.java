package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.galaxy.SensorPosition;
import com.spaceconquest.engine.combat.ShipCombatStats;

/** Provisional inclusive mount ranges at actual separation, independent of detection range. */
public final class ShipWeaponRange {
    private ShipWeaponRange() {}
    public static double maximumRange(ShipDesign design, ShipInstance ship) {
        if (design == null || ship == null || ship.currentHullHealth() <= 0) return 0;
        double range = 0;
        for (int mount = 0; mount < design.equippedModuleIds().size(); mount++) {
            var module = CombatModuleCatalog.module(design.equippedModuleIds().get(mount));
            if (module != null && ship.moduleDamage().condition(mount) > 0
                    && (!CombatModuleCatalog.MISSILE.equals(module.id()) || ShipMissileWeapons.rounds(ship) > 0))
                range = Math.max(range, module.operationalStats().getOrDefault("weaponRangeMeters", 0.0));
        }
        return range;
    }
    public static double capability(ShipDesign design, ShipInstance ship, String stat, double meters) {
        return capability(design, ship, stat, meters, false);
    }
    public static double directCapability(ShipDesign design, ShipInstance ship, String stat, double meters) {
        return capability(design, ship, stat, meters, true);
    }
    private static double capability(ShipDesign design, ShipInstance ship, String stat, double meters, boolean direct) {
        if (design == null || ship == null || Double.isNaN(meters) || meters < 0) return 0;
        double value = 0;
        int ammunition = ShipMissileWeapons.rounds(ship);
        for (int mount = 0; mount < design.equippedModuleIds().size(); mount++) {
            var module = CombatModuleCatalog.module(design.equippedModuleIds().get(mount));
            if (module != null && CombatModuleCatalog.MISSILE.equals(module.id())) {
                if (direct || ammunition <= 0 || ship.moduleDamage().condition(mount) <= 0
                        || module.operationalStats().get("weaponRangeMeters") < meters) continue;
                ammunition--;
            }
            if (module != null && module.operationalStats().getOrDefault("weaponRangeMeters", -1.0) >= meters)
                value += module.operationalStats().getOrDefault(stat, 0.0) * ship.moduleDamage().condition(mount);
        }
        return value;
    }
    public static double firepower(ShipDesign design, ShipInstance ship, double meters) {
        double all = ship.moduleDamage().capability(design, "weaponDamage");
        return all > 0 ? ShipCombatStats.of(design, ship).firepower() * capability(design, ship, "weaponDamage", meters) / all : 0;
    }
    public static double directFirepower(ShipDesign design, ShipInstance ship, double meters) {
        double all = ship.moduleDamage().capability(design, "weaponDamage");
        return all > 0 ? ShipCombatStats.of(design, ship).firepower() * directCapability(design, ship, "weaponDamage", meters) / all : 0;
    }
    public static double separation(GameState state, Fleet first, Fleet second) {
        if (!first.currentSystemId().equals(second.currentSystemId())) return Double.POSITIVE_INFINITY;
        if (!first.location().inTransit() && !second.location().inTransit() && first.location().current().equals(second.location().current())) return 0;
        var a = SensorPosition.at(state, first.currentSystemId(), first.location());
        var b = SensorPosition.at(state, second.currentSystemId(), second.location());
        return a == null || b == null ? Double.POSITIVE_INFINITY : VectorFlight.norm(VectorFlight.subtract(a, b));
    }
    /** At least one surviving mount can reach the target; actual firing still pays energy and needs live detection. */
    public static boolean reaches(GameState state, Fleet fleet, Fleet target) {
        double meters = separation(state, fleet, target);
        return fleet.ships().stream().anyMatch(ship -> ship.currentHullHealth() > 0
                && capability(FleetSupplySimulation.design(state, ship), ship, "weaponDamage", meters) > 0);
    }
}
