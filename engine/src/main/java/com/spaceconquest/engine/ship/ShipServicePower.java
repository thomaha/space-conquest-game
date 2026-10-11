package com.spaceconquest.engine.ship;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/** Provisional service priority behind life support, cargo conditioning and scheduled burns. */
public final class ShipServicePower {
    public static final String SENSORS = "sensors", WORKSHOPS = "workshops", WEAPONS = "weapons", SHIELDS = "shields";
    public static final List<String> PRIORITY = List.of(SENSORS, WORKSHOPS, WEAPONS, SHIELDS);
    public enum Activity {
        CRUISE(PRIORITY), COMBAT(List.of(SENSORS, WEAPONS, SHIELDS, WORKSHOPS)),
        ESCAPE(List.of(SHIELDS, SENSORS, WEAPONS, WORKSHOPS)), REPAIR(List.of(WORKSHOPS, SENSORS, SHIELDS, WEAPONS));
        private final List<String> priority;
        Activity(List<String> priority) { this.priority = priority; }
        public List<String> priority() { return priority; }
    }
    private ShipServicePower() {}
    static String service(String id) {
        if (SensorModuleCatalog.module(id) != null) return SENSORS;
        if (ShipRepairModuleCatalog.module(id) != null) return WORKSHOPS;
        if (CombatModuleCatalog.SHIELD.equals(id)) return SHIELDS;
        return CombatModuleCatalog.module(id) != null ? WEAPONS : null;
    }
    static Map<String, Double> capture(List<ShipModule> modules) {
        var loads = new HashMap<String, Double>();
        for (var module : modules) {
            String service = service(module.id());
            if (service != null) loads.merge(service, module.powerDrawKw(), Double::sum);
        }
        return Map.copyOf(loads);
    }
    static Map<String, Double> live(ShipDesign design, ShipInstance ship, double capturedShare) {
        var loads = new HashMap<String, Double>();
        for (int mount = 0; mount < design.equippedModuleIds().size(); mount++) {
            String id = design.equippedModuleIds().get(mount), service = service(id);
            if (service != null) loads.merge(service, ShipComponentCatalog.module(id).powerDrawKw()
                    * ship.moduleDamage().condition(mount) * capturedShare, Double::sum);
        }
        return Map.copyOf(loads);
    }
    public static boolean funded(ShipInstance ship, String service) {
        return ship.powerState() == null || !ship.powerState().shedServices().contains(service);
    }
    record Selection(double loadKw, java.util.Set<String> shed) {}
    static Selection select(ShipPowerProfile profile, double available, double required) {
        return select(profile, available, required, Activity.CRUISE);
    }
    static Selection select(ShipPowerProfile profile, double available, double required, Activity activity) {
        return select(profile, available, required, activity, 0, SHIELDS);
    }
    static Selection select(ShipPowerProfile profile, double available, double required, Activity activity, double activeKw, String activeService) {
        double load = required;
        double reserved = 0;
        var shed = new java.util.HashSet<String>();
        for (String service : activity.priority()) {
            if (!shed.contains(activeService) && (activity == Activity.COMBAT && WEAPONS.equals(activeService) && WORKSHOPS.equals(service)
                    || activity == Activity.ESCAPE && SHIELDS.equals(activeService) && WEAPONS.equals(service)))
                reserved = Math.min(activeKw, Math.max(0, available - load));
            double demand = profile.serviceLoadsKw().getOrDefault(service, 0.0);
            if (demand > 0 && load + demand + reserved > available + 1e-6) shed.add(service);
            else load += demand;
        }
        return new Selection(load, java.util.Set.copyOf(shed));
    }

    public static Activity activity(com.spaceconquest.engine.GameState state, Fleet fleet) {
        return activity(state, fleet, FleetThreatAssessment.caution(state, fleet).action());
    }
    static Activity activity(com.spaceconquest.engine.GameState state, Fleet fleet, FleetThreatAssessment.Action threat) {
        var protection = fleet.interceptOrder() == null ? null : fleet.interceptOrder().protection();
        if (com.spaceconquest.engine.combat.TacticalCombatProcessor.STANCE_EVASIVE_RETREAT.equalsIgnoreCase(fleet.fleetStance())
                || threat == FleetThreatAssessment.Action.ESCAPE
                || protection != null && protection.escaped() && FleetShieldPolicy.travelling(fleet)) return Activity.ESCAPE;
        if (threat != FleetThreatAssessment.Action.NO_CONTACT) return Activity.COMBAT;
        if (!FleetShieldPolicy.travelling(fleet) && (protection != null && protection.stage() == FleetProtectionAssignment.Stage.REPAIRING
                || fleet.ships().stream().anyMatch(ship -> ship.currentHullHealth() > 0
                && (ship.currentHullHealth() < ShipConstructionProcessor.COMMISSIONED_HULL_HEALTH
                || !ship.moduleDamage().damageByMount().isEmpty()))
                && fleet.ships().stream().anyMatch(ship -> {
                    var design = FleetSupplySimulation.design(state, ship);
                    return design != null && design.equippedModuleIds().contains(ShipRepairModuleCatalog.WORKSHOP);
                }))) return Activity.REPAIR;
        return Activity.CRUISE;
    }
}
