package com.spaceconquest.engine.ship;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** Whole projectile launches use the installed application's frozen capabilities. */
public final class ShipMissileWeapons {
    public static final String AMMUNITION = CombatModuleCatalog.module(CombatModuleCatalog.MISSILE).ammunitionId();
    public static final double MASS_KG = CombatModuleCatalog.module(CombatModuleCatalog.MISSILE).operationalStats().get("ammunitionMassKg");
    public static final double SPEED_METERS_PER_SECOND = CombatModuleCatalog.module(CombatModuleCatalog.MISSILE).operationalStats().get("projectileSpeedMetersPerSecond");
    public record Launch(int mount, double damage, double energyKwh, double speedMetersPerSecond,
                         boolean requiresLiveGuidance, String ammunitionId, double ammunitionKg, String weaponId,
                         ShipModule weapon) {
        public Launch(int mount, double damage, double energyKwh, double speedMetersPerSecond,
                      boolean requiresLiveGuidance, String ammunitionId, double ammunitionKg, String weaponId) {
            this(mount, damage, energyKwh, speedMetersPerSecond, requiresLiveGuidance, ammunitionId,
                    ammunitionKg, weaponId, CombatModuleCatalog.module(weaponId));
        }
    }
    private ShipMissileWeapons() {}
    public static boolean projectile(ShipModule module) {
        return module != null && module.operationalStats().getOrDefault("projectileSpeedMetersPerSecond", 0.0) > 0;
    }
    public static int rounds(ShipInstance ship) {
        return ShipWeaponAmmunition.rounds(ship.storedCargoKg(), CombatModuleCatalog.module(CombatModuleCatalog.MISSILE));
    }
    public static List<Launch> candidates(ShipDesign design, ShipInstance ship, double meters) {
        if (design == null || ship.currentHullHealth() <= 0 || !Double.isFinite(meters) || meters < 0
                || !ShipServicePower.funded(ship, ShipServicePower.WEAPONS)) return List.of();
        var remaining = new HashMap<>(ship.storedCargoKg());
        double hull = Math.clamp(ship.currentHullHealth() / Math.max(100, design.calculatedStructuralIntegrity() * 100), 0, 1);
        var launches = new ArrayList<Launch>();
        for (int mount = 0; mount < design.equippedModuleIds().size(); mount++) {
            var module = CombatModuleCatalog.module(design, design.equippedModuleIds().get(mount));
            if (!projectile(module) || ship.moduleDamage().condition(mount) <= 0
                    || meters > module.operationalStats().get("weaponRangeMeters")) continue;
            var stats = module.operationalStats();
            int count = Math.min(ShipWeaponAmmunition.rounds(remaining, module), (int) Math.floor(stats.get("launchesPerRound")));
            for (int shot = 0; shot < count; shot++) launches.add(new Launch(mount,
                    stats.get("weaponDamage") * hull * ship.moduleDamage().condition(mount), stats.get("weaponSalvoKwh"),
                    stats.get("projectileSpeedMetersPerSecond"), stats.getOrDefault("requiresLiveGuidance", 0.0) > 0,
                    module.ammunitionId(), stats.getOrDefault("ammunitionMassKg", 0.0), module.id(), module));
            ShipWeaponAmmunition.reserve(remaining, module, count);
        }
        return List.copyOf(launches);
    }
    public static ShipInstance consume(ShipInstance ship, List<Launch> launches) {
        var used = new HashMap<String, Double>();
        launches.stream().filter(launch -> !launch.ammunitionId().isBlank())
                .forEach(launch -> used.merge(launch.ammunitionId(), launch.ammunitionKg(), Double::sum));
        return ShipWeaponAmmunition.consume(ship, used);
    }
    public static ShipInstance consume(ShipInstance ship, int count) {
        if (count < 0 || count > rounds(ship)) throw new IllegalArgumentException("Insufficient whole missile rounds");
        return count == 0 ? ship : ShipWeaponAmmunition.consume(ship, java.util.Map.of(AMMUNITION, count * MASS_KG));
    }
}
