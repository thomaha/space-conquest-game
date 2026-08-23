package com.spaceconquest.engine.macrostructure;

import java.util.Map;

/**
 * Represents a specialized functional module installed on an orbital space station.
 *
 * @param id                    unique module identifier
 * @param name                  display name of the module
 * @param type                  module classification type
 * @param slotSize              internal starframe slots consumed
 * @param dryMassKg             structural dry mass in kilograms
 * @param powerDrawKw           turn-based electrical power draw in kW
 * @param powerOutputKw         turn-based electrical power generation in kW
 * @param materialInputs        turn-based resource consumption in kg
 * @param workforceProfessionId required workforce profession
 * @param requiredWorkers       number of personnel needed for full operation
 * @param isOnline              true if module is active and receiving power
 */
public record StationModule(
        String id,
        String name,
        String type,
        int slotSize,
        double dryMassKg,
        double powerDrawKw,
        double powerOutputKw,
        Map<String, Double> materialInputs,
        String workforceProfessionId,
        int requiredWorkers,
        boolean isOnline
) {
    public static final String TYPE_CONTROL = "CONTROL";
    public static final String TYPE_HABITATION = "HABITATION";
    public static final String TYPE_POWER = "POWER";
    public static final String TYPE_STORAGE = "STORAGE";
    public static final String TYPE_COMMERCE = "COMMERCE";
    public static final String TYPE_LOGISTICS = "LOGISTICS";
    public static final String TYPE_CIVILIAN_HANGAR = "CIVILIAN_HANGAR";
    public static final String TYPE_MILITARY_HANGAR = "MILITARY_HANGAR";
    public static final String TYPE_RESUPPLY_DEPOT = "RESUPPLY_DEPOT";
    public static final String TYPE_STRIKE_WING = "STRIKE_WING";
    public static final String TYPE_HYDROPONIC_FOOD = "HYDROPONIC_FOOD";
    public static final String TYPE_LITHOTROPHIC_MINERAL = "LITHOTROPHIC_MINERAL";
    public static final String TYPE_GASEOUS_FEED = "GASEOUS_FEED";
    public static final String TYPE_BIOREACTOR_TISSUE = "BIOREACTOR_TISSUE";
    public static final String TYPE_METALLURGY_FOUNDRY = "METALLURGY_FOUNDRY";
    public static final String TYPE_CHEMICAL_GAS_PROCESSING = "CHEMICAL_GAS_PROCESSING";
    public static final String TYPE_CONSUMER_GOODS_FACTORY = "CONSUMER_GOODS_FACTORY";
    public static final String TYPE_COMPONENT_ASSEMBLY = "COMPONENT_ASSEMBLY";
    public static final String TYPE_WEAPON_FOUNDRY = "WEAPON_FOUNDRY";
    public static final String TYPE_ENERGY_WEAPON_LAB = "ENERGY_WEAPON_LAB";
    public static final String TYPE_SHIPYARD_GRID = "SHIPYARD_GRID";
    public static final String TYPE_CAPITAL_SLIPWAY = "CAPITAL_SLIPWAY";
    public static final String TYPE_THEORETICAL_PHYSICS_LAB = "THEORETICAL_PHYSICS_LAB";
    public static final String TYPE_MATERIAL_SCIENCE_LAB = "MATERIAL_SCIENCE_LAB";
    public static final String TYPE_XENOBIOLOGY_LAB = "XENOBIOLOGY_LAB";
    public static final String TYPE_SHIELD_GENERATOR = "SHIELD_GENERATOR";
    public static final String TYPE_DEFENSIVE_PLATFORM = "DEFENSIVE_PLATFORM";
    public static final String TYPE_HEAVY_ORBITAL_BATTERY = "HEAVY_ORBITAL_BATTERY";

    public StationModule {
        if (materialInputs == null) materialInputs = Map.of();
    }
}
