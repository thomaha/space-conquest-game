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
            var module = damageableModule(design, design.equippedModuleIds().get(i));
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
            var module = damageableModule(design, design.equippedModuleIds().get(i));
            if (module != null) total += module.operationalStats().getOrDefault(stat, 0.0) * condition(i);
        }
        return total;
    }

    /** Ion effects damage electronic equipment directly without inventing hull damage or hitting passive armor. */
    public ShipModuleDamage hitElectronics(ShipDesign design, double points) {
        if (design == null || !Double.isFinite(points) || points <= 0) return this;
        var mounts = new java.util.ArrayList<Integer>();
        for (int mount = 0; mount < design.equippedModuleIds().size(); mount++) {
            String id = design.equippedModuleIds().get(mount);
            var module = damageableModule(design, id);
            if (module != null && condition(mount) > 0 && (module.powerDrawKw() > 0
                    || ShipElectricalDamage.hardware(id))) mounts.add(mount);
        }
        var damage = new HashMap<>(damageByMount);
        double remaining = points;
        while (!mounts.isEmpty() && remaining > 1e-9) {
            double share = remaining / mounts.size();
            var active = new java.util.ArrayList<Integer>();
            for (int mount : mounts) {
                double used = Math.min(DURABILITY - damage.getOrDefault(mount, 0.0), share);
                damage.merge(mount, used, Double::sum); remaining -= used;
                if (damage.get(mount) < DURABILITY) active.add(mount);
            }
            mounts = active;
        }
        return new ShipModuleDamage(damage);
    }

    public ShipModuleDamage repair(int mount, double points) {
        if (!Double.isFinite(points) || points < 0) throw new IllegalArgumentException("Invalid module repair");
        var damage = new HashMap<>(damageByMount);
        double remaining = Math.max(0, damage.getOrDefault(mount, 0.0) - points);
        if (remaining <= 1e-9) damage.remove(mount); else damage.put(mount, remaining);
        return new ShipModuleDamage(damage);
    }
    /** Explicit revision mapping; unchanged individual hardware retains damage even after reordering. */
    public ShipModuleDamage remap(ShipDesign source, ShipDesign target) {
        return remap(source, target, source == null || source.assembly() == null || target == null || target.assembly() == null ? Map.of() : target.assembly().identities().stream()
                .filter(source.assembly().identities()::contains)
                .collect(java.util.stream.Collectors.toMap(id -> id, id -> id)));
    }
    public ShipModuleDamage remap(ShipDesign source, ShipDesign target, Map<String, String> targetToSource) {
        if (source == null || target == null || source.assembly() == null || target.assembly() == null)
            throw new IllegalArgumentException("Captured installations are required for damage mapping");
        if (targetToSource == null || new java.util.HashSet<>(targetToSource.values()).size() != targetToSource.size()
                || !target.assembly().identities().containsAll(targetToSource.keySet()) || !source.assembly().identities().containsAll(targetToSource.values()))
            throw new IllegalArgumentException("Retained installation mapping must be one-to-one between known components");
        var previous = new HashMap<String, Integer>();
        for (int index = 0; index < source.assembly().installations().size(); index++) previous.put(source.assembly().installations().get(index).id(), index);
        var damage = new HashMap<Integer, Double>();
        for (int index = 0; index < target.assembly().installations().size(); index++) {
            var installation = target.assembly().installations().get(index); var oldIndex = previous.get(targetToSource.get(installation.id()));
            if (oldIndex != null && installation.hardware().equals(source.assembly().installations().get(oldIndex).hardware())) {
                double value = damageByMount.getOrDefault(oldIndex, 0.0);
                if (value > 0) damage.put(index, value);
            }
        }
        return new ShipModuleDamage(damage);
    }

    public static ShipModule damageableModule(ShipDesign design, String id) {
        if (design != null && design.assembly() != null && damageableModule(id) != null) return design.assembly().installations().stream()
                .filter(item -> id.equals(item.hardware().id())).map(ShipComponentInstallation::hardware).findFirst().orElse(null);
        var combat = CombatModuleCatalog.module(design, id);
        return combat != null ? combat : CombatModuleCatalog.module(id) != null ? null : damageableModule(id);
    }

    public static ShipModule damageableModule(String id) {
        var module = CombatModuleCatalog.module(id);
        if (module == null) module = ShipRepairModuleCatalog.module(id);
        if (module == null) module = ShipMiningModuleCatalog.module(id);
        if (module == null) module = SensorModuleCatalog.module(id);
        if (module == null) module = PropulsionCatalog.module(id);
        return module != null ? module : ShipElectricalDamage.hardware(id) ? ShipComponentCatalog.module(id) : null;
    }
}
