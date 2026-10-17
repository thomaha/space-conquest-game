package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.TechnicalApplication;
import java.io.IOException;
import java.util.*;

/** Application-defined combat hardware; research changes new construction rather than existing ships. */
public final class CombatModuleCatalog {
    public static final String GUN = "mod_ship_gun";
    public static final String LASER = "mod_ship_laser";
    public static final String PARTICLE_BEAM = "mod_ship_particle_beam";
    public static final String ION = "mod_ship_ion_weapon";
    public static final String MASS_DRIVER = "mod_ship_mass_driver";
    public static final String SHIELD = "mod_ship_shield";
    public static final String MISSILE = "mod_ship_guided_missile_launcher";
    public static final String LASER_POINT_DEFENSE = "mod_ship_laser_point_defense";
    public static final String KINETIC_POINT_DEFENSE = "mod_ship_kinetic_point_defense";
    public static final String ION_POINT_DEFENSE = "mod_ship_ion_point_defense";
    private record Definition(String technologyId, TechnicalApplication application) {}
    private static final Map<String, Definition> DEFINITIONS = load();
    public static final List<String> IDS = List.copyOf(DEFINITIONS.keySet());
    private CombatModuleCatalog() {}
    private static Map<String, Definition> load() {
        var definitions = new LinkedHashMap<String, Definition>();
        try {
            for (var technology : DataModelLoader.loadTechnologies()) for (var application : technology.applications()) {
                var module = application.combatModule();
                if (module == null) continue;
                com.spaceconquest.engine.combat.CombatApplicationValidation.validate(application);
                validate(module);
                if (definitions.putIfAbsent(module.id(), new Definition(technology.id(), application)) != null)
                    throw new IllegalArgumentException("Duplicate application combat module: " + module.id());
            }
        } catch (IOException e) { throw new IllegalStateException("Cannot load combat applications", e); }
        return Collections.unmodifiableMap(definitions);
    }
    private static void validate(ShipModule module) {
        if (module.id() == null || module.id().isBlank() || module.slotCost() <= 0 || module.dryMassKg() <= 0
                || !Double.isFinite(module.dryMassKg()) || !Double.isFinite(module.powerDrawKw()) || module.powerDrawKw() < 0)
            throw new IllegalArgumentException("Invalid combat module: " + module.id());
        module.operationalStats().forEach((stat, value) -> {
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid combat stat: " + stat);
        });
        if (!module.ammunitionId().isBlank() && module.operationalStats().getOrDefault("ammunitionMassKg", 0.0) <= 0)
            throw new IllegalArgumentException("Missing ammunition mass: " + module.id());
        var stats = module.operationalStats();
        if (stats.containsKey("shieldCapacity") && stats.getOrDefault("shieldRechargeKwhPerPoint", 0.0) <= 0)
            throw new IllegalArgumentException("Missing shield recharge energy: " + module.id());
        for (String stat : stats.containsKey("pointDefenseCapacity") ? List.of("pointDefenseRangeMeters", "pointDefenseShotKwh") : List.<String>of())
            if (!stats.containsKey(stat)) throw new IllegalArgumentException("Missing defensive capability: " + stat);
        for (String stat : stats.containsKey("weaponDamage") ? List.of("weaponRangeMeters", "weaponSalvoKwh") : List.<String>of())
            if (!stats.containsKey(stat)) throw new IllegalArgumentException("Missing offensive capability: " + stat);
        if (stats.getOrDefault("projectileSpeedMetersPerSecond", 0.0) > 0 && stats.getOrDefault("launchesPerRound", 0.0) <= 0)
            throw new IllegalArgumentException("Missing projectile launch capacity: " + module.id());
        for (String stat : List.of("accuracy", "rangeAccuracyFalloff", "shieldBypassFraction", "electronicDamageFraction",
                "pointDefenseInterceptable", "projectileHasElectronics", "pointDefenseRequiresTargetElectronics"))
            if (stats.getOrDefault(stat, 0.0) > 1) throw new IllegalArgumentException("Invalid weapon fraction: " + stat);
        for (String stat : List.of("shotsPerRound", "launchesPerRound")) {
            double count = stats.getOrDefault(stat, 1.0);
            if (count < 1 || count > 10000 || count != Math.floor(count))
                throw new IllegalArgumentException("Invalid whole shot count: " + stat);
        }
    }
    public static ShipModule module(String id) {
        var definition = id == null ? null : DEFINITIONS.get(id);
        return definition == null ? null : definition.application().combatModule();
    }
    public static ShipModule module(ShipDesign design, String id) {
        return design == null ? null : design.combatModules().get(id);
    }
    public static ShipModule installedModule(ShipDesign design, String id) {
        return application(id) != null ? module(design, id) : ShipComponentCatalog.module(id);
    }
    public static TechnicalApplication application(String moduleId) {
        var definition = DEFINITIONS.get(moduleId);
        return definition == null ? null : definition.application();
    }
    public static ShipModule moduleForApplication(String applicationId) {
        return DEFINITIONS.values().stream().map(Definition::application).filter(application -> application.id().equals(applicationId))
                .map(TechnicalApplication::combatModule).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown weapon application: " + applicationId));
    }
    public static boolean researched(List<String> modules, List<String> technologies) {
        return modules.stream().allMatch(id -> {
            var definition = DEFINITIONS.get(id);
            return definition == null || technologies.contains(definition.technologyId())
                    && technologies.contains(definition.application().id())
                    && technologies.containsAll(definition.application().requiredTechnologies());
        });
    }
    public static Map<String, ShipModule> capture(List<ShipModule> modules) {
        var captured = new LinkedHashMap<String, ShipModule>();
        modules.stream().filter(module -> application(module.id()) != null).forEach(module -> captured.put(module.id(), module));
        return Map.copyOf(captured);
    }
    public static String description(ShipModule module) {
        var application = application(module.id());
        var stats = module.operationalStats();
        var text = new StringBuilder(application.name()).append(". ").append(application.description())
                .append(" Requires owned application development and ").append(application.requiredTechnologies()).append(". ")
                .append(module.powerDrawKw()).append(" kW idle draw; ").append(module.dryMassKg()).append(" kg installed mass. ");
        if (stats.containsKey("weaponDamage")) text.append(stats.get("weaponDamage"))
                .append(ShipMissileWeapons.projectile(module) ? " damage per launch; " : " damage per salvo; ")
                .append(stats.get("weaponSalvoKwh")).append(ShipMissileWeapons.projectile(module) ? " kWh per launch; " : " kWh per salvo; ")
                .append(stats.get("weaponRangeMeters") / 1000).append(" km maximum range. ");
        if (stats.containsKey("shotsPerRound")) text.append(stats.get("shotsPerRound")).append(" separate pulses per round. ");
        if (stats.containsKey("accuracy")) text.append(stats.get("accuracy") * 100).append("% base accuracy. ");
        if (stats.containsKey("shieldBypassFraction")) text.append(stats.get("shieldBypassFraction") * 100).append("% shield bypass. ");
        if (stats.getOrDefault("electronicDamageFraction", 0.0) > 0)
            text.append(stats.get("electronicDamageFraction") * 100).append("% electronic damage, repaired as component damage. ");
        if (stats.containsKey("heavyArmorThresholdCm")) text.append("At ").append(stats.get("heavyArmorThresholdCm"))
                .append(" cm armor or above, hull damage multiplier ").append(stats.get("heavyArmorDamageMultiplier")).append(". ");
        if (stats.getOrDefault("armorAbsorptionPerCm", 0.0) > 0) text.append(stats.get("armorAbsorptionPerCm"))
                .append(" damage absorbed per cm of armor for each separate pulse. ");
        if (stats.getOrDefault("rangeAccuracyFalloff", 0.0) > 0) text.append(stats.get("rangeAccuracyFalloff") * 100)
                .append("% relative accuracy loss at maximum range. ");
        if (stats.getOrDefault("pointDefenseRequiresTargetElectronics", 0.0) > 0)
            text.append("Only incoming projectiles with electronics are eligible for interception. ");
        if (stats.containsKey("pointDefenseCapacity")) text.append("Up to ").append(stats.get("pointDefenseCapacity"))
                .append(" incoming missiles per healthy mount per combat round within ").append(stats.get("pointDefenseRangeMeters") / 1000)
                .append(" km, at ").append(stats.get("pointDefenseShotKwh")).append(" kWh each. Funded defense precedes offense and shield recharge. ");
        if (stats.containsKey("shieldCapacity")) text.append(stats.get("shieldCapacity")).append(" shield capacity; ")
                .append(stats.get("shieldRechargePointsPerHour")).append(" recharge points/hour at ")
                .append(stats.get("shieldRechargeKwhPerPoint")).append(" kWh per point. ");
        if (stats.containsKey("hangarCraftCapacity")) text.append(stats.get("hangarCraftCapacity")).append(" craft berths per healthy installed hangar. ");
        if (!module.ammunitionId().isBlank()) text.append("Each shot consumes one ").append(stats.get("ammunitionMassKg"))
                .append(" kg ").append(module.ammunitionId()).append(" cargo round. Buy and load manufactured ammunition. ");
        if (ShipMissileWeapons.projectile(module)) text.append(stats.get("launchesPerRound")).append(" launches per combat round; ")
                .append(stats.get("projectileSpeedMetersPerSecond") / 1000).append(" km/s projectile speed. Impacts occur in later rounds. ");
        if (stats.getOrDefault("requiresLiveGuidance", 0.0) > 0) text.append("Live guidance is required throughout flight. ");
        return text.append("Live detection, damage and available power govern operation.").toString();
    }
    public static double shieldCapacity(ShipDesign design) { return sum(design, "shieldCapacity"); }
    public static double weaponDamage(ShipDesign design) { return sum(design, "weaponDamage"); }
    private static double sum(ShipDesign design, String stat) {
        return design == null ? 0 : design.equippedModuleIds().stream().map(id -> module(design, id))
                .filter(java.util.Objects::nonNull).mapToDouble(module -> module.operationalStats().getOrDefault(stat, 0.0)).sum();
    }
}
