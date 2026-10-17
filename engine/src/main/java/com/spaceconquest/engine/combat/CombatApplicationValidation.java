package com.spaceconquest.engine.combat;

import com.spaceconquest.engine.TechnicalApplication;
import java.util.*;

/** Validates source applications, never legacy captured blueprints or saved combat receipts. */
public final class CombatApplicationValidation {
    private CombatApplicationValidation() {}
    public static void validate(TechnicalApplication app) {
        if (app.combatModule() == null && app.auxiliaryHardware() == null) return;
        if (app.id() == null || app.id().isBlank() || app.name() == null || app.name().isBlank()
                || !Double.isFinite(app.costToBuildPerUnit()) || app.costToBuildPerUnit() <= 0 || app.complexity() <= 0)
            fail(app, "identity, positive construction work and complexity are required");
        if (app.combatModule() != null) {
            var module = app.combatModule(); var stats = module.operationalStats();
            materials(app, module.materialCostsKg());
            if (List.of("weaponDamage", "pointDefenseCapacity", "shieldCapacity", "hangarCraftCapacity").stream().noneMatch(stats::containsKey))
                fail(app, "missing combat role capability");
            if (module.complexityLevel() != app.complexity()) fail(app, "module complexity differs from application complexity");
            for (var stat : stats.entrySet()) if (stat.getValue() == null || !Double.isFinite(stat.getValue()) || stat.getValue() < 0)
                fail(app, "invalid capability " + stat.getKey());
            if (stats.containsKey("weaponDamage")) {
                require(app, stats, "weaponDamage", "weaponRangeMeters", "weaponSalvoKwh", "accuracy", "rangeAccuracyFalloff",
                        "targetAccelerationSensitivity", "shieldBypassFraction", "pointDefenseInterceptable");
                positive(app, stats, "weaponDamage", "weaponRangeMeters");
                if (stats.containsKey("projectileSpeedMetersPerSecond")) {
                    require(app, stats, "launchesPerRound", "requiresLiveGuidance", "projectileHasElectronics");
                    positive(app, stats, "projectileSpeedMetersPerSecond"); whole(app, stats, "launchesPerRound");
                } else { require(app, stats, "shotsPerRound"); whole(app, stats, "shotsPerRound"); }
                if (stats.getOrDefault("heavyArmorDamageMultiplier", 1.0) < 1) {
                    require(app, stats, "heavyArmorThresholdCm"); positive(app, stats, "heavyArmorThresholdCm");
                }
                if (stats.getOrDefault("electronicDamageFraction", 0.0) > 0) {
                    require(app, stats, "electronicDamageMultiplier"); positive(app, stats, "electronicDamageMultiplier");
                }
            }
            if (stats.containsKey("pointDefenseCapacity")) {
                require(app, stats, "pointDefenseRangeMeters", "pointDefenseShotKwh");
                positive(app, stats, "pointDefenseRangeMeters", "pointDefenseShotKwh"); whole(app, stats, "pointDefenseCapacity");
            }
            if (stats.containsKey("shieldCapacity")) {
                require(app, stats, "shieldRechargePointsPerHour", "shieldRechargeKwhPerPoint");
                positive(app, stats, "shieldCapacity", "shieldRechargePointsPerHour", "shieldRechargeKwhPerPoint");
            }
            if (stats.containsKey("hangarCraftCapacity")) whole(app, stats, "hangarCraftCapacity");
            for (String key : List.of("accuracy", "rangeAccuracyFalloff", "shieldBypassFraction", "electronicDamageFraction"))
                if (stats.getOrDefault(key, 0.0) > 1) fail(app, "fraction outside [0, 1]: " + key);
            for (String key : List.of("pointDefenseInterceptable", "projectileHasElectronics", "requiresLiveGuidance", "pointDefenseRequiresTargetElectronics"))
                if (stats.containsKey(key) && stats.get(key) != 0 && stats.get(key) != 1) fail(app, "capability must be 0 or 1: " + key);
            if (!module.ammunitionId().isBlank()) { require(app, stats, "ammunitionMassKg"); positive(app, stats, "ammunitionMassKg"); }
            for (String key : app.effectStats()) if (!stats.containsKey(key)) fail(app, "unknown effect stat " + key);
        }
        if (app.auxiliaryHardware() != null) {
            var hardware = app.auxiliaryHardware(); materials(app, hardware.materialCostsKg());
            if (hardware.kind().equals("BATTERY")) {
                require(app, hardware.stats(), "repairHullWorkHoursPerPoint", "repairMountWorkHoursPerPoint");
                positive(app, hardware.stats(), "repairHullWorkHoursPerPoint", "repairMountWorkHoursPerPoint");
                whole(app, hardware.stats(), "surfaceSlots", "bodyDefenseSlots");
            }
            for (String key : app.effectStats()) if (!hardware.stats().containsKey(key)) fail(app, "unknown effect stat " + key);
        }
    }
    private static void materials(TechnicalApplication app, Map<String, Double> bill) {
        if (bill.isEmpty() || bill.values().stream().noneMatch(kg -> kg > 0)) fail(app, "missing construction material recipe");
        bill.forEach((id, kg) -> {
            if (id == null || id.isBlank() || kg == null || !Double.isFinite(kg) || kg < 0) fail(app, "invalid material " + id);
        });
    }
    private static void require(TechnicalApplication app, Map<String, Double> stats, String... keys) {
        for (String key : keys) if (!stats.containsKey(key)) fail(app, "missing capability " + key);
    }
    private static void positive(TechnicalApplication app, Map<String, Double> stats, String... keys) {
        for (String key : keys) if (!Double.isFinite(stats.get(key)) || stats.get(key) <= 0) fail(app, "capability must be positive: " + key);
    }
    private static void whole(TechnicalApplication app, Map<String, Double> stats, String... keys) {
        positive(app, stats, keys);
        for (String key : keys) if (stats.get(key) != Math.floor(stats.get(key))) fail(app, "capability must be a whole count: " + key);
    }
    private static void fail(TechnicalApplication app, String reason) { throw new IllegalArgumentException("Combat application " + app.id() + ": " + reason); }
}
