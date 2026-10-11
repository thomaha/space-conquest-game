package com.spaceconquest.engine.ship;

import java.util.HashMap;
import java.util.Map;

/** Sparse persistent damage by blueprint mount index; absent mounts are undamaged. */
public record ShipModuleDamage(Map<Integer, Double> damageByMount) {
    public static final double DURABILITY = 100;
    public ShipModuleDamage {
        var clean = new HashMap<Integer, Double>();
        if (damageByMount != null) damageByMount.forEach((mount, damage) -> {
            if (mount == null || mount < 0 || damage == null || !Double.isFinite(damage) || damage < 0)
                throw new IllegalArgumentException("Invalid module damage");
            if (damage > 0) clean.put(mount, Math.min(DURABILITY, damage));
        });
        damageByMount = Map.copyOf(clean);
    }
    public static ShipModuleDamage empty() { return new ShipModuleDamage(Map.of()); }
    public double condition(int mount) { return 1 - damageByMount.getOrDefault(mount, 0.0) / DURABILITY; }

    /** General hits spread collateral damage; targeted hits disable mounts in blueprint order. */
    public ShipModuleDamage hit(ShipDesign design, double hullDamage, String targetedStat) {
        if (design == null || hullDamage <= 0 || !Double.isFinite(hullDamage)) return this;
        var mounts = new java.util.ArrayList<Integer>();
        for (int i = 0; i < design.equippedModuleIds().size(); i++) {
            var module = damageableModule(design.equippedModuleIds().get(i));
            if (module != null && condition(i) > 0 && (targetedStat == null
                    || module.operationalStats().getOrDefault(targetedStat, 0.0) > 0
                    || "weaponDamage".equals(targetedStat) && module.operationalStats().getOrDefault("pointDefenseCapacity", 0.0) > 0)) mounts.add(i);
        }
        if (mounts.isEmpty()) return this;
        var damage = new HashMap<>(damageByMount);
        double remaining = targetedStat == null ? hullDamage * .25 : hullDamage;
        for (int mount : mounts) {
            double used = Math.min(DURABILITY * condition(mount), targetedStat == null ? remaining / mounts.size() : remaining);
            damage.merge(mount, used, Double::sum);
            if (targetedStat != null) remaining -= used;
        }
        return new ShipModuleDamage(damage);
    }

    public double capability(ShipDesign design, String stat) {
        if (design == null) return 0;
        double total = 0;
        for (int i = 0; i < design.equippedModuleIds().size(); i++) {
            var module = damageableModule(design.equippedModuleIds().get(i));
            if (module != null) total += module.operationalStats().getOrDefault(stat, 0.0) * condition(i);
        }
        return total;
    }

    public ShipModuleDamage repair(int mount, double points) {
        if (!Double.isFinite(points) || points < 0) throw new IllegalArgumentException("Invalid module repair");
        var damage = new HashMap<>(damageByMount);
        double remaining = Math.max(0, damage.getOrDefault(mount, 0.0) - points);
        if (remaining <= 1e-9) damage.remove(mount); else damage.put(mount, remaining);
        return new ShipModuleDamage(damage);
    }

    public static ShipModule damageableModule(String id) {
        var module = CombatModuleCatalog.module(id);
        if (module == null) module = ShipRepairModuleCatalog.module(id);
        if (module == null) module = SensorModuleCatalog.module(id);
        if (module == null) module = PropulsionCatalog.module(id);
        return module != null ? module : ShipElectricalDamage.hardware(id) ? ShipComponentCatalog.module(id) : null;
    }
}
