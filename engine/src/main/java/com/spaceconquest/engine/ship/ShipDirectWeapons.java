package com.spaceconquest.engine.ship;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Immediate salvos share magazines and require whole ammunition shots with complete shot energy. */
public final class ShipDirectWeapons {
    public record Plan(List<WeaponHit> hits, double energyKwh, Map<String, Double> ammunitionKg) {
        public Plan { hits = List.copyOf(hits); ammunitionKg = Map.copyOf(ammunitionKg); }
        public static Plan empty() { return new Plan(List.of(), 0, Map.of()); }
        public double damage() { return hits.stream().mapToDouble(WeaponHit::damage).sum(); }
        public ShipInstance consume(ShipInstance ship) { return ShipWeaponAmmunition.consume(ship, ammunitionKg); }
    }
    private record Mount(ShipModule module, int shots, double damagePerShot, double energyPerShot) {}
    private ShipDirectWeapons() {}

    public static Plan plan(ShipDesign design, ShipInstance ship, double meters, double availableKwh) {
        if (design == null || ship.currentHullHealth() <= 0 || !Double.isFinite(meters) || meters < 0
                || Double.isNaN(availableKwh) || availableKwh < 0 || !ShipServicePower.funded(ship, ShipServicePower.WEAPONS))
            return Plan.empty();
        var mounts = new ArrayList<Mount>();
        var remaining = new HashMap<>(ship.storedCargoKg());
        double hull = Math.clamp(ship.currentHullHealth() / Math.max(100, design.calculatedStructuralIntegrity() * 100), 0, 1);
        double continuousCost = 0;
        for (int index = 0; index < design.equippedModuleIds().size(); index++) {
            var module = CombatModuleCatalog.module(design, design.equippedModuleIds().get(index));
            if (module == null || ShipMissileWeapons.projectile(module) || WeaponHit.pulses(module) <= 0
                    || ship.moduleDamage().condition(index) <= 0
                    || module.operationalStats().getOrDefault("weaponRangeMeters", -1.0) < meters) continue;
            int pulses = WeaponHit.pulses(module);
            int shots = Math.min(pulses, ShipWeaponAmmunition.rounds(remaining, module));
            double condition = hull * ship.moduleDamage().condition(index);
            double damage = module.operationalStats().getOrDefault("weaponDamage", 0.0) * condition / pulses;
            if (damage <= 0 || shots <= 0) continue;
            double energy = module.operationalStats().getOrDefault("weaponSalvoKwh", 0.0) / pulses;
            if (module.ammunitionId().isBlank()) { energy *= condition; continuousCost += energy * shots; }
            mounts.add(new Mount(module, shots, damage, energy));
        }
        double continuousPaid = Math.min(availableKwh, continuousCost);
        double fraction = continuousCost > 0 ? continuousPaid / continuousCost : 1;
        double paid = continuousPaid;
        var hits = new ArrayList<WeaponHit>();
        var used = new HashMap<String, Double>();
        // Energy-only salvos retain proportional funding and precede discrete ammunition shots.
        for (var mount : mounts) {
            boolean ammunition = !mount.module().ammunitionId().isBlank();
            int shots = Math.min(mount.shots(), ShipWeaponAmmunition.rounds(remaining, mount.module()));
            for (int shot = 0; shot < shots; shot++) {
                if (ammunition && paid + mount.energyPerShot() > availableKwh + 1e-9) continue;
                double damage = mount.damagePerShot() * (ammunition ? 1 : fraction);
                if (damage <= 0) continue;
                hits.add(new WeaponHit(mount.module(), damage, meters));
                if (ammunition) {
                    paid += mount.energyPerShot();
                    ShipWeaponAmmunition.reserve(remaining, mount.module(), 1);
                    used.merge(mount.module().ammunitionId(), mount.module().operationalStats().get("ammunitionMassKg"), Double::sum);
                }
            }
        }
        return new Plan(hits, paid, used);
    }
}
