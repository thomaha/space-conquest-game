package com.spaceconquest.engine.ship;

import java.util.List;

/** One paid pulse or projectile with the immutable capabilities of its installed application. */
public record WeaponHit(ShipModule weapon, double damage, double separationMeters) {
    public WeaponHit {
        if (weapon == null || !Double.isFinite(damage) || damage < 0
                || !Double.isFinite(separationMeters) || separationMeters < 0)
            throw new IllegalArgumentException("Invalid weapon hit");
    }
    public double stat(String name, double legacyDefault) {
        return weapon.operationalStats().getOrDefault(name, legacyDefault);
    }
    public WeaponHit scaled(double multiplier) { return new WeaponHit(weapon, damage * multiplier, separationMeters); }
    public double accuracy(double targetAcceleration) {
        double range = stat("weaponRangeMeters", 0);
        if (separationMeters > range) return 0;
        double fraction = range > 0 ? separationMeters / range : 0;
        return Math.clamp(stat("accuracy", 1) * (1 - stat("rangeAccuracyFalloff", 0) * fraction * fraction)
                / (1 + Math.max(0, targetAcceleration) * stat("targetAccelerationSensitivity", 0)), 0, 1);
    }
    public static int pulses(ShipModule module) {
        return (int) Math.floor(module.operationalStats().getOrDefault("shotsPerRound", 1.0));
    }
    /** The power allocator proportionally funds mounts; each pulse remains separate at impact. */
    public static List<WeaponHit> direct(ShipDesign design, ShipInstance ship, double meters, double funding) {
        if (funding <= 0) return List.of();
        return ShipDirectWeapons.plan(design, ship, meters, Double.POSITIVE_INFINITY).hits().stream()
                .map(hit -> hit.scaled(funding)).toList();
    }
}
