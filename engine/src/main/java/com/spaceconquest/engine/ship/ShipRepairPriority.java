package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.galaxy.SensorCoverage;
import java.util.*;

/** Deterministic per-recipient priorities; quotes consume no stores and do not create repair capacity. */
public final class ShipRepairPriority {
    public record Context(boolean missingCoverage, FleetThreatAssessment.Action threat, boolean withdrawing,
                          ShipSolarEnvironment environment, Map<String, Set<Integer>> mobility, ShipServicePower.Activity activity) {
        public Context(boolean missingCoverage, FleetThreatAssessment.Action threat, boolean withdrawing,
                       ShipSolarEnvironment environment, Map<String, Set<Integer>> mobility) {
            this(missingCoverage, threat, withdrawing, environment, mobility,
                    withdrawing || threat == FleetThreatAssessment.Action.ESCAPE ? ShipServicePower.Activity.ESCAPE
                            : threat == FleetThreatAssessment.Action.HOLD ? ShipServicePower.Activity.COMBAT : ShipServicePower.Activity.CRUISE);
        }
        public Context(boolean missingCoverage, FleetThreatAssessment.Action threat, boolean withdrawing) {
            this(missingCoverage, threat, withdrawing, ShipSolarEnvironment.DARK, Map.of());
        }
        public Context(boolean missingCoverage, FleetThreatAssessment.Action threat, boolean withdrawing, ShipSolarEnvironment environment) {
            this(missingCoverage, threat, withdrawing, environment, Map.of());
        }
        public Context {
            Objects.requireNonNull(environment);
            Objects.requireNonNull(activity);
            var copy = new HashMap<String, Set<Integer>>();
            mobility.forEach((ship, mounts) -> copy.put(ship, Set.copyOf(mounts)));
            mobility = Map.copyOf(copy);
        }
    }
    private ShipRepairPriority() {}

    public static Context context(GameState state, Fleet fleet) {
        boolean missing = !SensorCoverage.covers(state, fleet.ownerEntityId(), fleet.currentSystemId(), fleet.location());
        var threat = FleetThreatAssessment.caution(state, fleet).action();
        boolean retreat = com.spaceconquest.engine.combat.TacticalCombatProcessor.STANCE_EVASIVE_RETREAT.equalsIgnoreCase(fleet.fleetStance())
                || fleet.interceptOrder() != null && fleet.interceptOrder().protection() != null && fleet.interceptOrder().protection().escaped();
        var environment = fleet.hasInterstellarOrder() && !fleet.location().inTransit() ? ShipSolarEnvironment.DARK
                : fleet.location().inTransit() ? ShipSolarEnvironment.journey(state, fleet, fleet.location().destination())
                : ShipSolarEnvironment.at(state, fleet, fleet.location().current());
        return new Context(missing, threat, retreat, environment,
                FleetMobilityRepairPriority.useful(state, fleet, retreat || threat == FleetThreatAssessment.Action.ESCAPE),
                ShipServicePower.activity(state, fleet, threat));
    }

    public static List<Integer> mounts(Context context, ShipDesign design, ShipInstance ship) {
        var mounts = new ArrayList<Integer>();
        for (int mount = 0; mount < design.equippedModuleIds().size(); mount++) {
            var module = ShipModuleDamage.damageableModule(design.equippedModuleIds().get(mount));
            if (module != null && !module.materialCostsKg().isEmpty()
                    && ship.moduleDamage().damageByMount().getOrDefault(mount, 0.0) > 1e-9) mounts.add(mount);
        }
        var power = ShipPowerRepairPriority.useful(design, ship, context.environment(), context.activity());
        mounts.sort(Comparator.comparingInt((Integer mount) -> rank(context, design, ship, mount, power.contains(mount))).thenComparingInt(Integer::intValue));
        return List.copyOf(mounts);
    }

    private static int rank(Context context, ShipDesign design, ShipInstance ship, int mount, boolean usefulPower) {
        var module = ShipModuleDamage.damageableModule(design.equippedModuleIds().get(mount));
        var stats = module.operationalStats();
        if (stats.getOrDefault("repairPointsPerDay", 0.0) > 0 && ship.moduleDamage().condition(mount) <= 0) return 0;
        if (usefulPower) return 1;
        if (context.mobility().getOrDefault(ship.id(), Set.of()).contains(mount)) return 2;
        if (context.missingCoverage() && stats.getOrDefault("sensorRangeKm", 0.0) > 0) return 3;
        if (context.threat() != FleetThreatAssessment.Action.NO_CONTACT) {
            boolean defensive = context.withdrawing() || context.threat() == FleetThreatAssessment.Action.ESCAPE;
            if (stats.getOrDefault("shieldCapacity", 0.0) > 0) return defensive ? 4 : 5;
            if (stats.getOrDefault("weaponDamage", 0.0) > 0 || stats.getOrDefault("pointDefenseCapacity", 0.0) > 0) return defensive ? 5 : 4;
        }
        return 6;
    }
}
