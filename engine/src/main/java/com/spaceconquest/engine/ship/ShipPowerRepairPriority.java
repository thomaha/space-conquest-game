package com.spaceconquest.engine.ship;

import java.util.HashSet;
import java.util.Set;

/** Bounded immutable quotes identify electrical repairs that reduce actual required-load shortages. */
final class ShipPowerRepairPriority {
    private record Deficit(double essential, double cargo, int shedServices) {}
    private ShipPowerRepairPriority() {}

    static Set<Integer> useful(ShipDesign design, ShipInstance ship, ShipSolarEnvironment environment) {
        return useful(design, ship, environment, ShipServicePower.Activity.CRUISE);
    }
    static Set<Integer> useful(ShipDesign design, ShipInstance ship, ShipSolarEnvironment environment, ShipServicePower.Activity activity) {
        if (design.powerProfile() == null || ship.currentHullHealth() <= 0) return Set.of();
        var candidates = new HashSet<Integer>();
        for (int mount = 0; mount < design.equippedModuleIds().size(); mount++)
            if (ShipElectricalDamage.hardware(design.equippedModuleIds().get(mount))
                    && ship.moduleDamage().condition(mount) < 1) candidates.add(mount);
        if (candidates.isEmpty()) return Set.of();
        // Clamp before a candidate repair so restored battery capacity cannot invent retained energy.
        var actual = ShipElectricalDamage.clampCharge(design, ship);
        var baseline = deficit(design, actual, environment, activity);
        if (baseline == null || baseline.essential() + baseline.cargo() <= 1e-6 && baseline.shedServices() == 0) return Set.of();
        var useful = new HashSet<Integer>();
        for (int mount : candidates) {
            var candidate = actual.withModuleDamage(actual.moduleDamage().repair(mount, ShipModuleDamage.DURABILITY));
            var restored = deficit(design, candidate, environment, activity);
            if (restored != null && (restored.essential() < baseline.essential() - 1e-6
                    || Math.abs(restored.essential() - baseline.essential()) <= 1e-6
                    && (restored.cargo() < baseline.cargo() - 1e-6
                    || Math.abs(restored.cargo() - baseline.cargo()) <= 1e-6
                    && restored.shedServices() < baseline.shedServices()))) useful.add(mount);
        }
        return Set.copyOf(useful);
    }

    private static Deficit deficit(ShipDesign design, ShipInstance ship, ShipSolarEnvironment environment, ShipServicePower.Activity activity) {
        var p = design.powerProfile(ship);
        var current = OrbitalPowerAccounting.startDay(ShipPowerProcessor.reserves(ship));
        double essential = p.essentialKw(ship, design), cargo = ShipPowerProcessor.cargoKw(p, ship, design);
        double elapsed = 0, missingEssential = 0, missingCargo = 0;
        double period = environment.lightHours() + environment.darkHours();
        for (int phase = 0; elapsed < 24 - 1e-9 && phase < 64; phase++) {
            double position = elapsed % period;
            boolean light = position < environment.lightHours();
            double hours = Math.min(24 - elapsed, (light ? environment.lightHours() : period) - position);
            if (hours <= 1e-9) return null;
            var budget = ShipPowerProcessor.interval(p, current, hours,
                    light ? ShipPowerProcessor.solarKw(p, current, environment) : 0, essential, cargo, 0, activity);
            current = budget.state(); elapsed += hours;
            missingEssential += budget.unmetEssentialKwh(); missingCargo += budget.unmetCargoKwh();
        }
        int lostServices = 0;
        for (int index = 0; index < activity.priority().size(); index++)
            if (current.shedServices().contains(activity.priority().get(index)))
                lostServices += 1 << (activity.priority().size() - index - 1);
        return elapsed >= 24 - 1e-9 ? new Deficit(missingEssential, missingCargo, lostServices) : null;
    }
}
