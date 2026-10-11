package com.spaceconquest.engine.ship;

import java.util.List;
import java.util.Map;

/** Research unlocks installed workshop hardware rather than free repair capability. */
public final class ShipRepairModuleCatalog {
    public static final String WORKSHOP = "mod_ship_repair_workshop";
    private static final ShipModule MODULE = new ShipModule(WORKSHOP, "Ship repair workshop", "MEDIUM",
            3, 2000, 20, 0, 0, 4, Map.of("steel", 250.0, "refined_copper", 50.0, "silicon", 50.0),
            Map.of("repairPointsPerDay", 20.0));
    private ShipRepairModuleCatalog() {}
    public static ShipModule module(String id) { return WORKSHOP.equals(id) ? MODULE : null; }
    public static boolean researched(List<String> modules, List<String> technologies) {
        return !modules.contains(WORKSHOP) || technologies.containsAll(List.of("electricity", "industrial_production", "robotics"));
    }
}
