package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.macrostructure.StationModule;
import java.util.List;
import java.util.Map;

/** Provisional sensor hardware tiers. Technology unlocks construction, never retroactive upgrades. */
public final class SensorModuleCatalog {
    public static final String BASIC = "mod_basic_sensors";
    public static final String ADVANCED = "mod_advanced_sensors";
    public static final List<String> IDS = List.of(BASIC, ADVANCED);
    private SensorModuleCatalog() {}

    public static ShipModule module(String id) {
        boolean advanced = ADVANCED.equals(id) || "scanner_array_01".equals(id);
        if (!advanced && !BASIC.equals(id)) return null;
        return new ShipModule(id, advanced ? "Advanced sensor array" : "Basic sensor array", "SMALL", advanced ? 2 : 1,
                advanced ? 500 : 200, advanced ? 40 : 10, 0, 0, advanced ? 6 : 2,
                Map.of("refined_copper", advanced ? 100.0 : 40.0),
                Map.of("sensorRangeKm", advanced ? 200_000_000.0 : 2_000_000.0, "scanStrength", advanced ? 25.0 : 10.0));
    }

    public static boolean researched(List<String> modules, List<String> technologies) {
        return modules.stream().allMatch(id -> module(id) == null || technologies.contains("electricity")
                && (!ADVANCED.equals(id) && !"scanner_array_01".equals(id)
                || technologies.contains("energy_fields") && technologies.contains("scanners")));
    }

    public static ShipSensorProfile profile(ShipDesign design) {
        if (design.sensorProfile() != null) return design.sensorProfile();
        return ShipSensorProfile.capture(design.equippedModuleIds().stream().map(SensorModuleCatalog::module)
                .filter(java.util.Objects::nonNull).toList());
    }

    /** Applies live mount condition without changing the capability captured by the blueprint. */
    public static ShipSensorProfile profile(ShipDesign design, ShipInstance ship) {
        if (design == null || ship == null || ship.currentHullHealth() <= 0
                || !ShipServicePower.funded(ship, ShipServicePower.SENSORS)
                || ship.powerState() != null && ship.powerState().lastUnmetEssentialKwh() > 1e-6)
            return new ShipSensorProfile(0, 0);
        double range = 0, strength = 0, workingRange = 0, workingStrength = 0;
        for (int mount = 0; mount < design.equippedModuleIds().size(); mount++) {
            var module = module(design.equippedModuleIds().get(mount));
            if (module == null) continue;
            double r = module.operationalStats().getOrDefault("sensorRangeKm", 0.0);
            double s = module.operationalStats().getOrDefault("scanStrength", 0.0);
            double condition = ship.moduleDamage().condition(mount);
            range = Math.max(range, r); strength = Math.max(strength, s);
            workingRange = Math.max(workingRange, r * condition);
            workingStrength = Math.max(workingStrength, s * condition);
        }
        var captured = profile(design);
        return new ShipSensorProfile(range > 0 ? captured.rangeKm() * workingRange / range : 0,
                strength > 0 ? captured.scanStrength() * workingStrength / strength : 0);
    }

    public static StationModule stationModule(String id) {
        var module = module(id);
        return module == null ? null : new StationModule(id, module.name(), id, module.slotCost(), module.dryMassKg(),
                module.powerDrawKw(), 0, Map.of(), "technician", 1, true);
    }
}
