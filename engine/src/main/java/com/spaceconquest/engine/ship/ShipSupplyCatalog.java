package com.spaceconquest.engine.ship;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Provisional dedicated supply storage and pump equipment; capacities exclude working fuel tanks. */
public final class ShipSupplyCatalog {
    public static final String LIQUID_TANK = "mod_bulk_liquid_supply_tank";
    public static final String CRYOGENIC_TANK = "mod_bulk_cryogenic_supply_tank";
    public static final String REACTOR_MAGAZINE = "mod_supply_reactor_magazine";
    public static final String TRANSFER_PUMP = "mod_fleet_supply_pump";
    public static final String SERVICE_HOLD = "mod_supply_service_hold";
    public static final List<String> STORAGE_IDS = List.of(LIQUID_TANK, CRYOGENIC_TANK, REACTOR_MAGAZINE);
    public record Compartment(String moduleId, double capacityKg, Set<String> materials) {
        public Compartment { materials = Set.copyOf(materials); }
    }
    private static final Map<String, Compartment> COMPARTMENTS = Map.of(
            LIQUID_TANK, new Compartment(LIQUID_TANK, 40000, Set.of("rp1_kerosene")),
            CRYOGENIC_TANK, new Compartment(CRYOGENIC_TANK, 40000, Set.of("liquid_oxygen", "liquid_methane",
                    "liquid_hydrogen", "hydrogen_gas", "deuterium_gas", "methane_ice")),
            REACTOR_MAGAZINE, new Compartment(REACTOR_MAGAZINE, 100, Set.of("refined_uranium", "refined_thorium", "fusion_fuel_pellets")));
    private ShipSupplyCatalog() {}

    public static ShipModule module(String id) {
        return switch (id) {
            case LIQUID_TANK -> storage(id, "Bulk RP-1 supply tank (40,000 kg)", 6, 2500, 2, 2);
            case CRYOGENIC_TANK -> storage(id, "Bulk cryogenic supply tank (40,000 kg)", 6, 3000, 40, 3);
            case REACTOR_MAGAZINE -> storage(id, "Reactor supply magazine (100 kg)", 1, 500, 1, 3);
            case TRANSFER_PUMP -> new ShipModule(id, "Fleet supply pump (1,000 kg/h)", "MEDIUM", 2, 750,
                    10, 0, 0, 2, Map.of("steel", 400.0, "refined_copper", 100.0), Map.of("supplyTransferKgPerHour", 1000.0));
            case SERVICE_HOLD -> new ShipModule(id, "Supply ship service hold (1,000 kg)", "SMALL", 2, 300,
                    2, 0, 0, 1, Map.of(), Map.of("cargoCapacityKg", 1000.0));
            default -> null;
        };
    }
    private static ShipModule storage(String id, String name, int slots, double mass, double power, int complexity) {
        return new ShipModule(id, name, "LARGE", slots, mass, power, 0, 0, complexity,
                Map.of("steel", mass * .6, "refined_aluminum", mass * .2),
                Map.of("supplyCapacityKg", COMPARTMENTS.get(id).capacityKg()));
    }
    public static double capacity(ShipDesign design, String materialId) {
        if (design == null || materialId == null) return 0;
        return design.equippedModuleIds().stream().map(COMPARTMENTS::get).filter(compartment -> compartment != null
                && compartment.materials().contains(materialId)).mapToDouble(Compartment::capacityKg).sum();
    }
    public static double totalCapacity(ShipDesign design) {
        return design.equippedModuleIds().stream().map(COMPARTMENTS::get).filter(java.util.Objects::nonNull)
                .mapToDouble(Compartment::capacityKg).sum();
    }
    public static List<String> materials(ShipDesign design) {
        return COMPARTMENTS.values().stream().flatMap(compartment -> compartment.materials().stream()).distinct()
                .filter(id -> capacity(design, id) > 0).sorted().toList();
    }
    public static boolean fits(ShipDesign design, Map<String, Double> stock) {
        if (design == null || stock == null || stock.entrySet().stream().anyMatch(entry -> entry.getKey() == null
                || entry.getValue() == null || !Double.isFinite(entry.getValue()) || entry.getValue() < 0
                || entry.getValue() > 0 && capacity(design, entry.getKey()) == 0)) return false;
        return COMPARTMENTS.values().stream().allMatch(compartment -> stock.entrySet().stream()
                .filter(entry -> compartment.materials().contains(entry.getKey())).mapToDouble(Map.Entry::getValue).sum()
                <= design.equippedModuleIds().stream().filter(compartment.moduleId()::equals).count() * compartment.capacityKg());
    }
    public static double transferKgPerHour(ShipDesign design) {
        return design == null ? 0 : design.equippedModuleIds().stream().filter(TRANSFER_PUMP::equals).count() * 1000.0;
    }
    public static List<String> tankerModules(String driveId, String powerId) {
        var modules = new java.util.ArrayList<>(ShipComponentCatalog.workbenchModules(driveId, false, powerId));
        modules.remove("mod_cargo_vault");
        modules.add(CRYOGENIC_TANK);
        if ("mod_chemical_rocket".equals(driveId)) modules.add(LIQUID_TANK);
        if (!PropulsionCatalog.reactorFuels(driveId).isEmpty()) {
            modules.add(SERVICE_HOLD);
            modules.add(REACTOR_MAGAZINE);
        }
        modules.add(TRANSFER_PUMP);
        return List.copyOf(modules);
    }
}
