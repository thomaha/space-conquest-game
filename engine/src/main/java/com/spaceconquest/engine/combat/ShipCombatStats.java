package com.spaceconquest.engine.combat;

import com.spaceconquest.engine.ship.*;

/** Shared deterministic equipment and current-condition inputs for assessment and actual tactical damage. */
public record ShipCombatStats(double firepower, double hull, double shields, double armorDeflection) {
    public static ShipCombatStats of(ShipDesign design, ShipInstance ship) {
        double hull = Math.max(0, ship.currentHullHealth());
        if (design == null || hull <= 0) return new ShipCombatStats(0, hull, 0, 0);
        double condition = Math.clamp(hull / Math.max(100, design.calculatedStructuralIntegrity() * 100), 0, 1);
        boolean powered = powered(design, ship);
        double firepower = powered && ShipServicePower.funded(ship, ShipServicePower.WEAPONS)
                ? ship.moduleDamage().capability(design, "weaponDamage") * condition : 0;
        double shields = powered && ShipServicePower.funded(ship, ShipServicePower.SHIELDS)
                ? Math.clamp(ship.currentShieldHealth(), 0, ship.moduleDamage().capability(design, "shieldCapacity")) : 0;
        return new ShipCombatStats(firepower, hull, shields, Math.clamp(design.armorThicknessCm() * .05, 0, .6));
    }
    public double endurance() { return (hull + shields) / (1 - armorDeflection); }
    private static boolean powered(ShipDesign design, ShipInstance ship) {
        var profile = design.powerProfile(ship); var power = ship.powerState();
        if (power != null && power.lastUnmetEssentialKwh() > 1e-6) return false;
        if (profile == null) return true; // Legacy equipment snapshots have no electrical simulation profile.
        if (power == null) return false;
        if (profile.batteryKwh() > 0 && power.batteryChargeKwh() > 0) return true;
        if (profile.solarKw() > 0 && power.arraysDeployed() && power.arrayCondition() > 0 && power.orientationFraction() > 0) return true;
        return profile.fissionKw() > 0 && hasFuel(profile.fuels().get(power.reactorFuel()), power)
                || profile.chemicalKw() > 0 && hasFuel(profile.fuels().get(power.chemicalMixture()), power);
    }
    private static boolean hasFuel(ShipPowerProfile.Fuel fuel, ShipPowerState power) {
        return fuel != null && fuel.materials(1).keySet().stream().allMatch(id -> power.generatorMaterialsKg().getOrDefault(id, 0.0) > 0);
    }
    public static double stanceModifier(String stance) {
        return stance == null ? 1 : switch (stance.toUpperCase(java.util.Locale.ROOT)) {
            case TacticalCombatProcessor.STANCE_AGGRESSIVE_BRAWL -> 1.25;
            case TacticalCombatProcessor.STANCE_STANDOFF_KITE -> 1.10;
            case TacticalCombatProcessor.STANCE_POINT_DEFENSE_SCREEN -> .90;
            case TacticalCombatProcessor.STANCE_EVASIVE_RETREAT -> 0;
            default -> 1;
        };
    }
}
