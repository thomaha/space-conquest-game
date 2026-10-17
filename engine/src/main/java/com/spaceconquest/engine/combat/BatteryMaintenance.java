package com.spaceconquest.engine.combat;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Tick-owned surface repair shares paid yard work with construction and buys complete material recipes. */
public final class BatteryMaintenance {
    private record Work(GameState state, double points, double hours) {}
    private BatteryMaintenance() {}
    public static boolean accessible(GameState state, String owner, PlanetaryDefenseBattery battery) {
        String system = ConstructionMaterials.systemForBody(state, battery.planetId());
        return system != null && state.empires().stream().anyMatch(e -> owner.equals(e.id()) && e.controlledSystemIds().contains(system));
    }
    /** Older captured chassis use their application's newly introduced repair rates without gaining supplies. */
    public static double hoursPerPoint(AuxiliaryHardware hardware, String key) {
        if (hardware.stats().containsKey(key)) return hardware.value(key);
        var app = hardware.applicationId() == null ? null : AuxiliaryApplicationCatalog.application(hardware.applicationId());
        return app == null ? AuxiliaryApplicationCatalog.baseline("BATTERY").value(key) : app.auxiliaryHardware().value(key);
    }
    public static GameState advance(GameState state, Map<String, Double> remainingWork, Map<String, Integer> paidWorkers) {
        var current = state;
        for (var owned : state.auxiliaryForces().batteries()) {
            var battery = owned.battery(); String owner = owned.ownerEntityId();
            if (battery.hullHealth() <= 0 || !accessible(current, owner, battery)) continue;
            if (battery.hullHealth() >= battery.hardware().value("hullHealth") && battery.moduleDamage().condition(0) >= 1) continue;
            String system = ConstructionMaterials.systemForBody(current, battery.planetId());
            var chassis = AuxiliaryApplicationCatalog.application(battery.hardware().applicationId() == null
                    ? AuxiliaryApplicationCatalog.baseline("BATTERY").applicationId() : battery.hardware().applicationId());
            int complexity = Math.max(battery.weapon().complexityLevel(), chassis == null ? 0 : chassis.complexity());
            if (ShipManufacturingCapacity.forYard(current, owner, system, battery.planetId()) < complexity) continue;
            String key = system + "/" + battery.planetId();
            var before = current;
            double remaining = remainingWork.computeIfAbsent(key, ignored -> ShipyardWorkCapacity.forYard(before, owner,
                    system, battery.planetId(), paidWorkers).workPerDay());
            var mount = buy(current, owner, battery, battery.weapon().materialCostsKg(), ShipModuleDamage.DURABILITY,
                    battery.moduleDamage().damageByMount().getOrDefault(0, 0.0), hoursPerPoint(battery.hardware(), "repairMountWorkHoursPerPoint"), remaining);
            current = mount.state(); remaining -= mount.hours();
            var hull = buy(current, owner, battery, battery.hardware().materialCostsKg(), battery.hardware().value("hullHealth"),
                    Math.max(0, battery.hardware().value("hullHealth") - battery.hullHealth()),
                    hoursPerPoint(battery.hardware(), "repairHullWorkHoursPerPoint"), remaining);
            current = hull.state(); remainingWork.put(key, Math.max(0, remaining - hull.hours()));
            if (mount.points() > 0 || hull.points() > 0) current = current.toBuilder().auxiliaryForces(current.auxiliaryForces().withBattery(
                    battery.withDamage(battery.hullHealth() + hull.points(), battery.moduleDamage().repair(0, mount.points())))).build();
        }
        return current;
    }
    private static Work buy(GameState state, String owner, PlanetaryDefenseBattery battery, Map<String, Double> materials,
            double maximum, double missing, double hoursPerPoint, double remaining) {
        if (!Double.isFinite(hoursPerPoint) || hoursPerPoint <= 0 || remaining <= 1e-9 || missing <= 1e-9)
            return new Work(state, 0, 0);
        double points = Math.min(missing, remaining / hoursPerPoint);
        var bill = new HashMap<String, Double>();
        materials.forEach((id, kg) -> { if (kg > 0) bill.put(id, kg * points / maximum); });
        if (bill.isEmpty()) return new Work(state, 0, 0);
        var purchase = ConstructionMaterials.buyUpTo(state, battery.planetId(), owner, bill);
        return purchase.covers(bill) ? new Work(purchase.state(), points, points * hoursPerPoint) : new Work(state, 0, 0);
    }
}
