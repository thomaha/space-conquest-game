package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.habitation.PassengerStasis;
import com.spaceconquest.engine.technology.ApplicationProduction;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/** Applies researched development paths to recognized blueprint components. */
public final class ShipApplicationProduction {
    private static final Map<String, String> APPLICATIONS = Map.ofEntries(
            Map.entry("mod_chemical_rocket", "rocket_engines"),
            Map.entry("mod_methalox_rocket", "methalox_rocket_engines"),
            Map.entry("mod_hydrolox_rocket", "hydrolox_rocket_engines"),
            Map.entry("mod_fission_thruster", "fission_engines"),
            Map.entry("mod_ion_drive", "mpd_ion_drives"),
            Map.entry("mod_fusion_drive", "fusion_engines"),
            Map.entry("mod_antimatter_drive", "antimatter_engines"),
            Map.entry("mod_fission_reactor", "fission_reactors"),
            Map.entry(ShipComponentCatalog.SOLAR_ARRAY_ID, "solar_power"),
            Map.entry(PassengerStasis.MODULE_ID, "cryogenic_stasis_pod"),
            Map.entry("mod_cargo_vault", "pressurized_cargo_holds"),
            Map.entry("mod_cargo_hold_large", "pressurized_cargo_holds"),
            Map.entry(ChemicalFreighterCatalog.CARGO_HOLD, "pressurized_cargo_holds"));

    private ShipApplicationProduction() {}

    public static String applicationId(String moduleId) {
        return APPLICATIONS.get(moduleId);
    }

    public static ShipModule optimize(GameState state, String ownerId, ShipModule module) {
        String applicationId = applicationId(module.id());
        if (state == null || applicationId == null) return module;
        var modifiers = ApplicationProduction.modifiersForOwner(state, ownerId, applicationId);
        if (modifiers.effectMultiplier() == 1.0 && modifiers.costMultiplier() == 1.0
                && modifiers.complexityShift() == 0) return module;
        Map<String, Double> stats = module.operationalStats();
        if (stats.containsKey("thrusterEfficiency")) {
            stats = new HashMap<>(stats);
            stats.computeIfPresent("thrusterEfficiency", (key, efficiency) -> Math.clamp(efficiency * modifiers.effectMultiplier(), 0, 1));
        }
        if (PassengerStasis.MODULE_ID.equals(module.id()) || "pressurized_cargo_holds".equals(applicationId)) {
            stats = new HashMap<>(stats);
            if (PassengerStasis.MODULE_ID.equals(module.id()))
                stats.put("stasisCapacity", Math.floor(stats.getOrDefault("stasisCapacity",
                        (double) PassengerStasis.PASSENGERS_PER_POD) * modifiers.effectMultiplier() + 0.000001));
            else stats.computeIfPresent("cargoCapacityKg", (key, capacity) -> capacity * modifiers.effectMultiplier());
        }
        return new ShipModule(module.id(), module.name(), module.slotSize(), module.slotCost(),
                module.dryMassKg(), module.powerDrawKw(), module.powerOutputKw() * modifiers.effectMultiplier(),
                module.thrustOutputN() * modifiers.effectMultiplier(),
                Math.max(1, module.complexityLevel() + modifiers.complexityShift()),
                module.materialCostsKg(), stats);
    }

    /** Applies the cost change only to the linked modules' mass share of the provisional ship bill. */
    public static ShipManufacturingProfile profile(GameState state, String ownerId,
                                                   List<ShipModule> optimizedModules, double totalDryMassKg) {
        double costDeltaMass = 0.0;
        int complexity = 1;
        int stasisCapacity = 0;
        for (ShipModule module : optimizedModules) {
            complexity = Math.max(complexity, module.complexityLevel());
            if (PassengerStasis.MODULE_ID.equals(module.id()))
                stasisCapacity += (int) Math.max(0, module.operationalStats()
                        .getOrDefault("stasisCapacity", (double) PassengerStasis.PASSENGERS_PER_POD).doubleValue());
            String applicationId = applicationId(module.id());
            if (state != null && applicationId != null) {
                costDeltaMass += module.dryMassKg()
                        * (ApplicationProduction.modifiersForOwner(state, ownerId, applicationId).costMultiplier() - 1.0);
            }
        }
        return new ShipManufacturingProfile(1.0 + costDeltaMass / Math.max(1.0, totalDryMassKg), complexity, stasisCapacity);
    }
}
