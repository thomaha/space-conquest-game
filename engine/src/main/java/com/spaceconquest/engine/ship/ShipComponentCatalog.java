package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.habitation.PassengerStasis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared provisional component stats for player previews and authoritative blueprint construction. */
public final class ShipComponentCatalog {
    public static final Set<String> ROLES = Set.of(ShipRole.FIGHTER, ShipRole.EXPLORER, ShipRole.TROOP_TRANSPORT,
            ShipRole.CARGO_TRANSPORT, ShipRole.COLONY_SHIP, ShipRole.MINING_SHIP, ShipRole.ESCORT,
            ShipRole.COMBAT_SHIP, ShipRole.CARRIER_SHIP, ShipRole.CONSTRUCTION_SHIP);
    private static final Map<String, ShipModule> MODULES = Map.of(
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
        if (PropulsionCatalog.FUEL_TANK_MODULE_ID.equals(id)) return PropulsionCatalog.fuelTankModule();
        ShipModule drive = PropulsionCatalog.module(id);
        return drive == null ? MODULES.get(id) : drive;
    }

    public static ShipHullFrame mediumFrame(String materialId) {
        return new ShipHullFrame("frame_medium", "Medium hull starframe", 30, materialId, 15_000, 60);
    }

    public static List<String> workbenchModules(String driveId, boolean stasis) {
        List<String> ids = new ArrayList<>(List.of("mod_fission_reactor", driveId, "mod_cargo_vault",
                PropulsionCatalog.FUEL_TANK_MODULE_ID));
        if (stasis) ids.add(PassengerStasis.MODULE_ID);
        return List.copyOf(ids);
    }
}
