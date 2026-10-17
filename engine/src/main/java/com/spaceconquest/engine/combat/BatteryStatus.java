package com.spaceconquest.engine.combat;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Immutable current-store diagnostics; projected construction and repairs are discarded. */
public record BatteryStatus(String id, Firing firing, Repair repair) {
    public record Firing(String status, int fundedShots, int fullSalvoShots, double rangeMeters,
                         double upkeepKwh, double salvoKwh, double condition) {}
    public record Repair(String status, String firstJob, double projectedMountPoints, double projectedHullPoints,
                         double sharedYardHours, Map<String, Double> shortages, String note) {
        public Repair { shortages = Map.copyOf(shortages); }
    }
    public static List<BatteryStatus> inspect(GameState state, String owner, String body) {
        var batteries = state.auxiliaryForces().batteries().stream().filter(b -> owner.equals(b.ownerEntityId())
                && body.equals(b.battery().planetId())).toList();
        if (batteries.isEmpty()) return List.of();
        boolean damaged = batteries.stream().anyMatch(b -> b.battery().hullHealth() > 0
                && (b.battery().moduleDamage().condition(0) < 1 || b.battery().hullHealth() < b.battery().hardware().value("hullHealth")));
        var projected = damaged ? new ShipConstructionProcessor().process(state) : state;
        return batteries.stream().map(b -> new BatteryStatus(b.battery().id(), firing(b.battery()), repair(state, projected, b))).toList();
    }
    private static Firing firing(PlanetaryDefenseBattery battery) {
        var weapon = battery.weapon(); var salvo = WeaponSalvo.of(weapon); double condition = battery.moduleDamage().condition(0);
        var quote = battery.stores().fire(weapon, 1, 0);
        String status;
        int funded = quote.hits().size();
        if (battery.hullHealth() <= 0) { status = "Destroyed battery"; funded = 0; }
        else if (!battery.isOperational()) { status = "Battery inactive or unstaffed"; funded = 0; }
        else if (condition <= 0) { status = "Weapon electronics disabled"; funded = 0; }
        else if (!weapon.ammunitionId().isBlank() && ShipWeaponAmmunition.rounds(battery.stores().ammunitionKg(), weapon) == 0)
            status = "Empty magazine: no whole round";
        else if (funded == 0) status = quote.reason();
        else if (funded < salvo.shots()) status = "Reduced salvo: " + quote.reason();
        else status = condition < 1 ? "Ready with reduced weapon condition" : "Ready for quoted salvo";
        return new Firing(status, funded, salvo.shots(), weapon.operationalStats().getOrDefault("weaponRangeMeters", 0.0),
                weapon.powerDrawKw() * TacticalCombatProcessor.SECONDS_PER_ROUND / 3600, salvo.energyKwh(), condition);
    }
    private static Repair repair(GameState state, GameState projected, AuxiliaryForces.OwnedBattery owned) {
        var battery = owned.battery(); String owner = owned.ownerEntityId(); String body = battery.planetId();
        String note = "Current paid staffing and stores; construction and earlier batteries share work. Next tick rechecks costs and capacity.";
        if (battery.hullHealth() <= 0) return blocked("Destroyed: dismantle to free slots", "", 0, Map.of(), note);
        boolean mount = battery.moduleDamage().condition(0) < 1;
        double missing = mount ? battery.moduleDamage().damageByMount().get(0) : Math.max(0, battery.hardware().value("hullHealth") - battery.hullHealth());
        String job = mount ? "Weapon electronics" : "Hull";
        if (missing <= 0) return blocked("No repairs needed", "", 0, Map.of(), note);
        if (!BatteryMaintenance.accessible(state, owner, battery)) return blocked("Owner does not control this system", job, 0, Map.of(), note);
        String system = ConstructionMaterials.systemForBody(state, body);
        var app = AuxiliaryApplicationCatalog.application(battery.hardware().applicationId() == null
                ? AuxiliaryApplicationCatalog.baseline("BATTERY").applicationId() : battery.hardware().applicationId());
        int required = Math.max(battery.weapon().complexityLevel(), app == null ? 0 : app.complexity());
        if (ShipManufacturingCapacity.forYard(state, owner, system, body) < required)
            return blocked("No owned surface yard of sufficient complexity", job, 0, Map.of(), note);
        double hours = ShipyardWorkCapacity.forYard(state, owner, system, body).workPerDay();
        if (hours <= 0) return blocked("No paid yard staffing or operational work capacity", job, hours, Map.of(), note);
        var after = projected.auxiliaryForces().batteries().stream().filter(b -> b.battery().id().equals(battery.id())).findFirst().orElseThrow().battery();
        double mountPoints = battery.moduleDamage().damageByMount().getOrDefault(0, 0.0) - after.moduleDamage().damageByMount().getOrDefault(0, 0.0);
        double hullPoints = after.hullHealth() - battery.hullHealth();
        double rate = BatteryMaintenance.hoursPerPoint(battery.hardware(), mount ? "repairMountWorkHoursPerPoint" : "repairHullWorkHoursPerPoint");
        if (mountPoints > 0 || hullPoints > 0) return new Repair("Paid repair work available in preview", job, mountPoints, hullPoints, hours, Map.of(), note);
        if (rate <= 0) return blocked("Application supplies no work rate for this repair", job, hours, Map.of(), note);
        var materials = mount ? battery.weapon().materialCostsKg() : battery.hardware().materialCostsKg();
        double maximum = mount ? ShipModuleDamage.DURABILITY : battery.hardware().value("hullHealth");
        double points = Math.min(missing, hours / rate); var bill = new HashMap<String, Double>(); var shortages = new HashMap<String, Double>();
        materials.forEach((id, kg) -> { if (kg > 0) bill.put(id, kg * points / maximum); });
        if (bill.isEmpty()) return blocked("Captured hardware has no repair material recipe", job, hours, Map.of(), note);
        var hub = state.commercialHubs().stream().filter(h -> body.equals(h.entityId())).findFirst().orElse(null);
        if (hub == null) return blocked("No local commercial hub", job, hours, Map.of(), note);
        bill.forEach((id, kg) -> {
            var order = hub.activeOrders().get(id);
            double stocked = order == null || !Double.isFinite(order.supplyKg()) ? 0 : Math.max(0, order.supplyKg());
            if (kg > stocked + 1e-6) shortages.put(id, kg - stocked);
        });
        boolean queued = state.shipConstructionOrders().stream().anyMatch(o -> body.equals(o.yardBodyId()))
                || state.auxiliaryForces().orders().stream().anyMatch(o -> body.equals(o.bodyId()))
                || state.auxiliaryForces().batteries().stream().takeWhile(b -> !b.battery().id().equals(battery.id()))
                        .anyMatch(b -> body.equals(b.battery().planetId()) && b.battery().hullHealth() > 0
                                && (b.battery().moduleDamage().condition(0) < 1 || b.battery().hullHealth() < b.battery().hardware().value("hullHealth")));
        String status = queued ? "Waiting for shared yard work or paid materials" : !shortages.isEmpty() ? "Missing repair materials"
                : "Insufficient funds or invalid local material prices";
        return blocked(status, job, hours, shortages, note + " Shortages quote the first job before competing work.");
    }
    private static Repair blocked(String status, String job, double hours, Map<String, Double> shortages, String note) {
        return new Repair(status, job, 0, 0, hours, shortages, note);
    }
}
