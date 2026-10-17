package com.spaceconquest.engine.combat;

import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Explains actual paid output without running another power or combat projection. */
final class WeaponFiringReport {
    private WeaponFiringReport() {}

    static void append(ShipInstance before, ShipDesign design, ShipCombatPower.Result paid, boolean firing,
                       double meters, List<ShipPointDefense.Threat> threats, List<String> logs,
                       List<TacticalCombatProcessor.CombatActionReport> actions) {
        if (design == null) return;
        var groups = new LinkedHashMap<String, List<Integer>>();
        for (int mount = 0; mount < design.equippedModuleIds().size(); mount++) {
            var module = CombatModuleCatalog.module(design, design.equippedModuleIds().get(mount));
            if (module != null && (module.operationalStats().containsKey("weaponDamage")
                    || module.operationalStats().containsKey("pointDefenseCapacity")))
                groups.computeIfAbsent(module.id(), key -> new ArrayList<>()).add(mount);
        }
        double hull = Math.clamp(before.currentHullHealth() / Math.max(100, design.calculatedStructuralIntegrity() * 100), 0, 1);
        for (var group : groups.entrySet()) {
            var module = CombatModuleCatalog.module(design, group.getKey());
            var stats = module.operationalStats();
            boolean defense = stats.containsKey("pointDefenseCapacity");
            double expected = group.getValue().stream().mapToDouble(mount -> before.moduleDamage().condition(mount) * hull
                    * stats.getOrDefault(defense ? "pointDefenseCapacity" : "weaponDamage", 0.0)
                    * (!defense && ShipMissileWeapons.projectile(module) ? stats.get("launchesPerRound") : 1)).sum();
            double actual = defense ? paid.pointDefenseShots().stream().filter(shot -> shot.weaponId().equals(module.id())).count()
                    : paid.directHits().stream().filter(hit -> hit.weapon().id().equals(module.id())).mapToDouble(WeaponHit::damage).sum()
                    + paid.missileLaunches().stream().filter(launch -> launch.weaponId().equals(module.id())).mapToDouble(ShipMissileWeapons.Launch::damage).sum();
            long eligible = defense ? threats.stream().filter(threat -> threat.distanceMeters() <= stats.get("pointDefenseRangeMeters")
                    && (stats.getOrDefault("pointDefenseRequiresTargetElectronics", 0.0) <= 0 || threat.hasElectronics()))
                    .filter(threat -> paid.pointDefenseShots().stream().noneMatch(shot -> !shot.weaponId().equals(module.id())
                            && shot.targetId() == threat.id())).count() : 0;
            if (defense) expected = Math.min(eligible, group.getValue().stream().mapToDouble(mount -> Math.floor(
                    before.moduleDamage().condition(mount) * hull * stats.get("pointDefenseCapacity") + 1e-9)).sum());
            var reasons = new ArrayList<String>();
            if (hull <= 0 || group.getValue().stream().allMatch(mount -> before.moduleDamage().condition(mount) <= 0))
                reasons.add("hardware disabled or ship destroyed");
            else if (!defense && !firing) reasons.add("fleet firing order or live targeting holds fire");
            else if (!defense && meters > stats.getOrDefault("weaponRangeMeters", 0.0))
                reasons.add(String.format(Locale.ROOT, "out of range: %.3f km exceeds %.3f km", meters / 1000,
                        stats.getOrDefault("weaponRangeMeters", 0.0) / 1000));
            else if (defense && eligible == 0)
                reasons.add("no unassigned detected eligible incoming projectile in defensive range");
            else if (defense && expected <= 0) reasons.add("condition too low for a whole defensive shot");
            else if (actual + 1e-9 < (defense ? Math.floor(expected + 1e-9) : expected)) {
                if (!module.ammunitionId().isBlank() && ShipWeaponAmmunition.rounds(paid.ship().storedCargoKg(), module) == 0)
                    reasons.add("no whole round remains in shared magazine " + module.ammunitionId());
                if (paid.paidWeaponKwh() + 1e-9 < paid.requestedWeaponKwh()) reasons.add("insufficient firing power");
                if (reasons.isEmpty()) reasons.add("weapon power or upkeep not funded");
            }
            if (reasons.isEmpty()) continue;
            String description = before.id() + ": " + module.name() + (actual > 0 ? " reduced salvo; " : " held; ") + String.join("; ", reasons);
            logs.add(description);
            actions.add(new TacticalCombatProcessor.CombatActionReport("WEAPON_HOLD", description, 0, before.id()));
        }
    }
}
