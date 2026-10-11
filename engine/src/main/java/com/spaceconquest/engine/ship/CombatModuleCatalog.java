package com.spaceconquest.engine.ship;

import java.util.List;
import java.util.Map;

/** Provisional installed combat hardware; research unlocks new construction rather than upgrading ships. */
public final class CombatModuleCatalog {
    public static final String GUN = "mod_ship_gun";
    public static final String LASER = "mod_ship_laser";
    public static final String MASS_DRIVER = "mod_ship_mass_driver";
    public static final String SHIELD = "mod_ship_shield";
    public static final String MISSILE = "mod_ship_guided_missile_launcher";
    public static final String LASER_POINT_DEFENSE = "mod_ship_laser_point_defense";
    public static final String KINETIC_POINT_DEFENSE = "mod_ship_kinetic_point_defense";
    public static final List<String> IDS = List.of(GUN, LASER, MASS_DRIVER, MISSILE, LASER_POINT_DEFENSE, KINETIC_POINT_DEFENSE, SHIELD);
    private static final Map<String, ShipModule> MODULES = Map.of(
            GUN, new ShipModule(GUN, "Ship cannon", "SMALL", 1, 500, 2, 0, 0, 2,
                    Map.of("steel", 100.0), Map.of("weaponDamage", 80.0, "weaponSalvoKwh", 1.0, "weaponRangeMeters", 100_000.0)),
            LASER, new ShipModule(LASER, "Ship laser", "MEDIUM", 2, 1000, 40, 0, 0, 6,
                    Map.of("industrial_ceramics", 100.0, "silicon", 50.0), Map.of("weaponDamage", 160.0, "weaponSalvoKwh", 8.0, "weaponRangeMeters", 1_000_000.0)),
            MASS_DRIVER, new ShipModule(MASS_DRIVER, "Ship mass driver", "MEDIUM", 3, 2000, 60, 0, 0, 7,
                    Map.of("steel", 150.0, "tungsten_ore", 50.0), Map.of("weaponDamage", 220.0, "weaponSalvoKwh", 12.0, "weaponRangeMeters", 500_000.0)),
            MISSILE, new ShipModule(MISSILE, "Guided missile launcher", "MEDIUM", 2, 1000, 10, 0, 0, 5,
                    Map.of("steel", 100.0, "silicon", 50.0),
                    Map.of("weaponDamage", 180.0, "weaponSalvoKwh", 2.0, "weaponRangeMeters", 2_000_000.0)),
            LASER_POINT_DEFENSE, new ShipModule(LASER_POINT_DEFENSE, "Laser point defense", "SMALL", 1, 500, 20, 0, 0, 6,
                    Map.of("industrial_ceramics", 50.0, "silicon", 25.0),
                    Map.of("pointDefenseCapacity", 2.0, "pointDefenseShotKwh", 1.0, "pointDefenseRangeMeters", 50_000.0)),
            KINETIC_POINT_DEFENSE, new ShipModule(KINETIC_POINT_DEFENSE, "Mass-driver point defense", "SMALL", 1, 500, 15, 0, 0, 6,
                    Map.of("steel", 100.0, "tungsten_ore", 25.0),
                    Map.of("pointDefenseCapacity", 3.0, "pointDefenseShotKwh", 0.5, "pointDefenseRangeMeters", 25_000.0)),
            SHIELD, new ShipModule(SHIELD, "Ship shield emitter", "MEDIUM", 2, 1000, 30, 0, 0, 8,
                    Map.of("superconducting_cuprates", 50.0, "refined_titanium", 100.0),
                    Map.of("shieldCapacity", 200.0, "shieldRechargePointsPerHour", 10.0)));
    private CombatModuleCatalog() {}
    public static ShipModule module(String id) { return id == null ? null : MODULES.get(id); }
    public static boolean researched(List<String> modules, List<String> technologies) {
        return modules.stream().allMatch(id -> module(id) == null || technologies.contains("electricity") && switch (id) {
            case GUN -> technologies.contains("industrial_production");
            case MISSILE -> technologies.contains("industrial_production") && technologies.contains("rocketry");
            case LASER -> technologies.contains("energy_fields") && technologies.contains("energy_weapons") && technologies.contains("laser_weapons");
            case LASER_POINT_DEFENSE -> technologies.contains("energy_fields") && technologies.contains("energy_weapons")
                    && technologies.contains("laser_weapons") && technologies.contains("pd_lasers");
            case MASS_DRIVER -> technologies.contains("energy_fields") && technologies.contains("electromagnetic_weapons") && technologies.contains("mass_drivers");
            case KINETIC_POINT_DEFENSE -> technologies.contains("energy_fields") && technologies.contains("electromagnetic_weapons")
                    && technologies.contains("mass_drivers") && technologies.contains("pd_mass_drivers");
            case SHIELD -> technologies.contains("energy_fields") && technologies.contains("shields");
            default -> false;
        });
    }
    public static double shieldCapacity(ShipDesign design) { return sum(design, "shieldCapacity"); }
    public static double weaponDamage(ShipDesign design) { return sum(design, "weaponDamage"); }
    private static double sum(ShipDesign design, String stat) {
        return design == null ? 0 : design.equippedModuleIds().stream().map(CombatModuleCatalog::module)
                .filter(java.util.Objects::nonNull).mapToDouble(module -> module.operationalStats().getOrDefault(stat, 0.0)).sum();
    }
}
