package com.spaceconquest.engine.ship;

/** Healthy offensive round totals from captured application hardware, before funding or targeting gates. */
public record WeaponSalvo(int shots, double damage, double energyKwh) {
    public static WeaponSalvo of(ShipModule weapon) {
        var stats = weapon.operationalStats();
        if (!stats.containsKey("weaponDamage")) return new WeaponSalvo(0, 0, 0);
        boolean projectile = ShipMissileWeapons.projectile(weapon);
        int shots = projectile ? (int) Math.floor(stats.getOrDefault("launchesPerRound", 1.0)) : WeaponHit.pulses(weapon);
        double salvos = projectile ? shots : shots > 0 ? 1 : 0;
        return new WeaponSalvo(shots, stats.get("weaponDamage") * salvos,
                stats.getOrDefault("weaponSalvoKwh", 0.0) * salvos);
    }
}
