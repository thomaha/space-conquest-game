package com.spaceconquest.engine.ship;

import java.util.List;
import java.util.Map;

/** Provisional drive characteristics for the propulsion modules in the technology tree. */
public final class PropulsionCatalog {
    public static final String FUEL_TANK_MODULE_ID = "mod_propellant_tank";
    public record Drive(String moduleId, String technologyId, String propellantId,
                        double exhaustVelocityMps, String oxidizerId, double fuelFraction) {
        public Drive(String moduleId, String technologyId, String propellantId,
                     double exhaustVelocityMps) {
            this(moduleId, technologyId, propellantId, exhaustVelocityMps, null, 1.0);
        }

        public Map<String, Double> propellantMaterials(double totalKg) {
            if (oxidizerId == null) return Map.of(propellantId, totalKg);
            return Map.of(propellantId, totalKg * fuelFraction,
                    oxidizerId, totalKg * (1.0 - fuelFraction));
        }

        public String propellantLabel() {
            return oxidizerId == null ? propellantId : propellantId + " + " + oxidizerId;
        }
    }
    public record ReactorFuel(String materialId, double exhaustMultiplier,
                              double kgPerPropellantKg) {}

    private static final Map<String, List<ReactorFuel>> REACTOR_FUELS = Map.of(
            "mod_fission_thruster", List.of(
                    new ReactorFuel("refined_uranium", 1.0, 0.001),
                    new ReactorFuel("refined_thorium", 1.0, 0.0012)),
            "mod_fusion_drive", List.of(
                    new ReactorFuel("hydrogen_gas", 0.15, 0.0),
                    new ReactorFuel("deuterium_gas", 0.60, 0.02),
                    new ReactorFuel("fusion_fuel_pellets", 1.0, 0.01)));

    private static final Map<String, Drive> DRIVES = Map.of(
            "mod_chemical_rocket", new Drive("mod_chemical_rocket", "rocketry", "rp1_kerosene", 3_400.0, "liquid_oxygen", 0.28),
            "mod_methalox_rocket", new Drive("mod_methalox_rocket", "methalox_propulsion", "liquid_methane", 3_700.0, "liquid_oxygen", 0.22),
            "mod_hydrolox_rocket", new Drive("mod_hydrolox_rocket", "hydrolox_propulsion", "liquid_hydrogen", 4_500.0, "liquid_oxygen", 0.17),
            "mod_fission_thruster", new Drive("mod_fission_thruster", "nuclear_fission", "hydrogen_gas", 9_000.0),
            "mod_fusion_drive", new Drive("mod_fusion_drive", "nuclear_fusion", "hydrogen_gas", 1_000_000.0),
            "mod_ion_drive", new Drive("mod_ion_drive", "superconductors", "methane_ice", 40_000.0),
            "mod_antimatter_drive", new Drive("mod_antimatter_drive", "antimatter", "antimatter_containment_cell", 100_000_000.0));

    public static final List<String> MAIN_DRIVE_IDS = List.of("mod_chemical_rocket",
            "mod_methalox_rocket", "mod_hydrolox_rocket",
            "mod_fission_thruster", "mod_ion_drive", "mod_fusion_drive",
            "mod_antimatter_drive");

    private PropulsionCatalog() {}

    public static Drive drive(String moduleId) {
        return DRIVES.get(moduleId);
    }

    public static List<ReactorFuel> reactorFuels(String moduleId) {
        return REACTOR_FUELS.getOrDefault(moduleId, List.of());
    }

    public static ReactorFuel reactorFuel(String moduleId, String materialId) {
        return reactorFuels(moduleId).stream()
                .filter(fuel -> fuel.materialId().equals(materialId))
                .findFirst().orElse(null);
    }

    /** Keeps an existing reactor feed when replenishing, otherwise uses the default. */
    public static ReactorFuel preferredReactorFuel(Drive drive, ShipInstance ship) {
        List<ReactorFuel> fuels = reactorFuels(drive.moduleId());
        return fuels.stream().filter(fuel -> ship.storedCargoKg()
                        .getOrDefault(fuel.materialId(), 0.0) > 0.000001)
                .max(java.util.Comparator.comparingDouble(ReactorFuel::exhaustMultiplier))
                .orElse(fuels.isEmpty() ? null : fuels.getFirst());
    }

    /** Selects the most efficient carried reactor fuel that can support a full tank. */
    public static ReactorFuel availableReactorFuel(Drive drive, ShipInstance ship) {
        return reactorFuels(drive.moduleId()).stream()
                .filter(fuel -> fuel.kgPerPropellantKg() == 0.0
                        || ship.storedCargoKg().getOrDefault(fuel.materialId(), 0.0)
                        + 0.000001 >= ship.currentFuelKg() * fuel.kgPerPropellantKg())
                .max(java.util.Comparator.comparingDouble(ReactorFuel::exhaustMultiplier))
                .orElse(null);
    }

    public static boolean researched(List<String> moduleIds, List<String> technologies) {
        if (moduleIds == null || technologies == null) return false;
        for (String moduleId : moduleIds) {
            Drive drive = drive(moduleId);
            if (drive != null && !technologies.contains(drive.technologyId())) return false;
        }
        return true;
    }

    public static boolean validConfiguration(List<String> moduleIds, double tankCapacityKg) {
        if (moduleIds == null || !Double.isFinite(tankCapacityKg)
                || tankCapacityKg < 0.0) return false;
        long mainDrives = moduleIds.stream().filter(DRIVES::containsKey).count();
        return mainDrives == 0 || (mainDrives == 1 && tankCapacityKg > 0.0);
    }

    public static ShipModule module(String moduleId) {
        return switch (moduleId) {
            case "mod_chemical_rocket" -> new ShipModule(moduleId, "RP-1/LOX rocket", "MEDIUM",
                    4, 4_000, 20, 0, 600_000, 2, Map.of(), Map.of());
            case "mod_methalox_rocket" -> new ShipModule(moduleId, "Methane/LOX rocket", "MEDIUM",
                    4, 4_200, 22, 0, 620_000, 3, Map.of(), Map.of());
            case "mod_hydrolox_rocket" -> new ShipModule(moduleId, "Hydrogen/LOX rocket", "MEDIUM",
                    5, 5_000, 25, 0, 650_000, 4, Map.of(), Map.of());
            case "mod_fission_thruster" -> new ShipModule(moduleId, "Fission thermal drive", "MEDIUM",
                    5, 6_000, 100, 0, 850_000, 4, Map.of(), Map.of());
            case "mod_ion_drive" -> new ShipModule(moduleId, "MPD ion drive", "MEDIUM",
                    6, 4_500, 120, 0, 3.9, 7, Map.of(), Map.of("thrusterEfficiency", .65));
            case "mod_fusion_drive" -> new ShipModule(moduleId, "Fusion drive", "LARGE",
                    8, 5_000, 200, 0, 1_200_000, 8, Map.of(), Map.of());
            case "mod_antimatter_drive" -> new ShipModule(moduleId, "Antimatter drive", "LARGE",
                    10, 6_000, 300, 0, 1_500_000, 10, Map.of(), Map.of());
            default -> null;
        };
    }

    public static ShipModule fuelTankModule() {
        return new ShipModule(FUEL_TANK_MODULE_ID, "Propellant tank (15,000 kg)",
                "MEDIUM", 4, 2_000, 0, 0, 0, 2, Map.of(),
                Map.of("fuelCapacityKg", 15_000.0));
    }

    /** The highest-impulse recognized main drive determines the current single-tank fuel type. */
    public static Drive mainDrive(List<String> moduleIds) {
        if (moduleIds == null) return null;
        Drive best = null;
        for (String moduleId : moduleIds) {
            Drive drive = drive(moduleId);
            if (drive != null && (best == null
                    || drive.exhaustVelocityMps() > best.exhaustVelocityMps())) best = drive;
        }
        return best;
    }
}
