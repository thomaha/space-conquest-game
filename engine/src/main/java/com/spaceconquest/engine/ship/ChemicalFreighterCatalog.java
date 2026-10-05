package com.spaceconquest.engine.ship;

import java.util.List;
import java.util.Map;

/** Provisional small-hold chemical freighter equipment; quantities are gameplay coefficients. */
public final class ChemicalFreighterCatalog {
    public static final String MAIN_TANK = "mod_long_range_main_tank";
    public static final String CARGO_HOLD = "mod_compact_freighter_hold";
    public static final List<String> DRIVES = List.of("mod_chemical_rocket", "mod_methalox_rocket", "mod_hydrolox_rocket");
    private ChemicalFreighterCatalog() {}

    public static ShipModule module(String id) {
        if (MAIN_TANK.equals(id)) return new ShipModule(id, "Conditioned long-range main tank (120,000 kg)", "LARGE",
                16, 12000, 10, 0, 0, 3, Map.of(), Map.of("fuelCapacityKg", 120000.0, "tankVolumeM3", 400.0));
        if (CARGO_HOLD.equals(id)) return new ShipModule(id, "Compact pressurized hold (2,500 kg)", "SMALL",
                3, 750, 8, 0, 0, 2, Map.of(), Map.of("cargoCapacityKg", 2500.0));
        return null;
    }

    public static List<String> modules(String drive) {
        if (!DRIVES.contains(drive)) throw new IllegalArgumentException("A researched chemical main drive is required");
        return List.of(drive, MAIN_TANK, CARGO_HOLD, ShipComponentCatalog.SOLAR_ARRAY_ID, ShipComponentCatalog.BATTERY_ID);
    }

    public static boolean researched(List<String> modules, List<String> technologies) {
        return modules.stream().noneMatch(id -> MAIN_TANK.equals(id) || CARGO_HOLD.equals(id))
                || technologies.contains("electricity") && technologies.contains("industrial_production");
    }

    public static boolean compatible(List<String> modules) {
        if (!modules.contains(MAIN_TANK)) return true;
        var drive = PropulsionCatalog.mainDrive(modules);
        return drive != null && DRIVES.contains(drive.moduleId())
                && mixtureVolumeM3(drive.moduleId(), module(MAIN_TANK).operationalStats().get("fuelCapacityKg"))
                <= module(MAIN_TANK).operationalStats().get("tankVolumeM3");
    }

    /** Provisional density coefficients; the rating caps mass as well as this declared volume. */
    public static double mixtureVolumeM3(String driveId, double mixtureKg) {
        if (!Double.isFinite(mixtureKg) || mixtureKg < 0 || !DRIVES.contains(driveId))
            throw new IllegalArgumentException("A supported mixture and finite nonnegative mass are required");
        var drive = PropulsionCatalog.drive(driveId);
        double fuelDensity = switch (driveId) {
            case "mod_chemical_rocket" -> 800;
            case "mod_methalox_rocket" -> 420;
            default -> 70;
        };
        return mixtureKg * (drive.fuelFraction() / fuelDensity + (1 - drive.fuelFraction()) / 1140);
    }

    public static double unconditionedDailyLoss(String driveId) {
        return switch (driveId) {
            case "mod_chemical_rocket" -> .005;
            case "mod_methalox_rocket" -> .01;
            case "mod_hydrolox_rocket" -> .02;
            default -> 0;
        };
    }
}
