package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.habitation.PassengerStasis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared provisional component stats for player previews and authoritative blueprint construction. */
public final class ShipComponentCatalog {
    public static final String CHEMICAL_GENERATOR_ID = "mod_chemical_generator";
    public static final String FISSION_REACTOR_ID = "mod_fission_reactor";
    public static final String SOLAR_ARRAY_ID = "mod_solar_array";
    public static final String BATTERY_ID = "mod_ship_battery";
    public static final String GENERATOR_TANK_ID = "mod_generator_tank";
    public static final String REACTOR_TANK_ID = "mod_reactor_tank";
    public static final List<String> POWER_MODULE_IDS = List.of(SOLAR_ARRAY_ID, CHEMICAL_GENERATOR_ID, FISSION_REACTOR_ID);
    public static final Set<String> ROLES = Set.of(ShipRole.FIGHTER, ShipRole.EXPLORER, ShipRole.TROOP_TRANSPORT,
            ShipRole.CARGO_TRANSPORT, ShipRole.COLONY_SHIP, ShipRole.MINING_SHIP, ShipRole.ESCORT,
            ShipRole.COMBAT_SHIP, ShipRole.CARRIER_SHIP, ShipRole.CONSTRUCTION_SHIP);
    private static final Map<String, ShipModule> MODULES = Map.of(
            SOLAR_ARRAY_ID, new ShipModule(SOLAR_ARRAY_ID, "Deployable solar arrays (120 kW at 1 AU)",
                    "MEDIUM", 4, 2500, 0, 120, 0, 2, Map.of(), Map.of()),
            BATTERY_ID, new ShipModule(BATTERY_ID, "Ship battery (500 kWh)",
                    "MEDIUM", 2, 2500, 0, 0, 0, 2, Map.of(),
                    Map.of("batteryKwh", 500.0, "chargeKw", 100.0, "dischargeKw", 200.0)),
            GENERATOR_TANK_ID, new ShipModule(GENERATOR_TANK_ID, "Generator reserves (15,000 kg)",
                    "MEDIUM", 2, 2000, 0, 0, 0, 2, Map.of(), Map.of("generatorTankKg", 15000.0)),
            REACTOR_TANK_ID, new ShipModule(REACTOR_TANK_ID, "Electrical reactor fuel compartment (100 kg)",
                    "SMALL", 1, 500, 0, 0, 0, 2, Map.of(), Map.of("reactorTankKg", 100.0)),
            CHEMICAL_GENERATOR_ID, new ShipModule(CHEMICAL_GENERATOR_ID, "Chemical auxiliary generator",
                    "MEDIUM", 4, 4500, 0, 500, 0, 2, Map.of(), Map.of()),
            "mod_fission_reactor", new ShipModule("mod_fission_reactor", "Fission reactor tier 2",
                    "MEDIUM", 4, 3000, 0, 500, 0, 2, Map.of(), Map.of()),
            "mod_cargo_vault", new ShipModule("mod_cargo_vault", "Pressurized cargo vault",
                    "LARGE", 8, 2000, 30, 0, 0, 1, Map.of(), Map.of("cargoCapacityKg", 30_000.0)),
            "mod_cargo_hold_large", new ShipModule("mod_cargo_hold_large", "Large cargo hold",
                    "LARGE", 8, 2000, 30, 0, 0, 2, Map.of(), Map.of("cargoCapacityKg", 50_000.0)),
            PassengerStasis.MODULE_ID, new ShipModule(PassengerStasis.MODULE_ID, "Cryogenic stasis pod",
                    "MEDIUM", 2, 1200, 80, 0, 0, 7, Map.of("refined_aluminum", 100.0, "refined_copper", 50.0),
                    Map.of("stasisCapacity", (double) PassengerStasis.PASSENGERS_PER_POD)));

    private ShipComponentCatalog() {}

    public static ShipModule module(String id) {
        if (id == null) return null;
        ShipModule supply = ShipSupplyCatalog.module(id);
        if (supply != null) return supply;
        ShipModule freighter = ChemicalFreighterCatalog.module(id);
        if (freighter != null) return freighter;
        if (PropulsionCatalog.FUEL_TANK_MODULE_ID.equals(id)) return PropulsionCatalog.fuelTankModule();
        ShipModule drive = PropulsionCatalog.module(id);
        return drive == null ? MODULES.get(id) : drive;
    }

    public static ShipHullFrame mediumFrame(String materialId) {
        return new ShipHullFrame("frame_medium", "Medium hull starframe", 30, materialId, 15_000, 60);
    }

    public static List<String> workbenchModules(String driveId, boolean stasis) {
        return workbenchModules(driveId, stasis, CHEMICAL_GENERATOR_ID);
    }

    public static List<String> workbenchModules(String driveId, boolean stasis, String powerModuleId) {
        return workbenchModules(driveId, stasis, powerModuleId, false);
    }

    public static List<String> workbenchModules(String driveId, boolean stasis, String powerModuleId, boolean solarSupport) {
        List<String> ids = new ArrayList<>(List.of(powerModuleId, driveId, "mod_cargo_vault",
                PropulsionCatalog.FUEL_TANK_MODULE_ID, BATTERY_ID));
        if (solarSupport && !SOLAR_ARRAY_ID.equals(powerModuleId)) ids.add(SOLAR_ARRAY_ID);
        if (CHEMICAL_GENERATOR_ID.equals(powerModuleId)) ids.add(GENERATOR_TANK_ID);
        if (FISSION_REACTOR_ID.equals(powerModuleId)) ids.add(REACTOR_TANK_ID);
        if (stasis) ids.add(PassengerStasis.MODULE_ID);
        return List.copyOf(ids);
    }

    public static boolean powerResearched(List<String> modules, List<String> technologies) {
        return (!modules.contains(FISSION_REACTOR_ID) || technologies.contains("nuclear_fission"))
                && (!modules.contains(SOLAR_ARRAY_ID)
                || technologies.contains("electricity") && technologies.contains("solar_power"));
    }
}
