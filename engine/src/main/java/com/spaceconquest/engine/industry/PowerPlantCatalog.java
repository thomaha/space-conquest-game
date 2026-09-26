package com.spaceconquest.engine.industry;

import java.util.Map;

/** Daily output and fuel requirements for grid-connected generation facilities. */
public final class PowerPlantCatalog {
    public record Plant(double kwPerTier, int requiredWorkers, String requiredTechnology,
                        String fuelMaterialId, double fuelKgPerTierDay) {}

    private static final Map<String, Plant> PLANTS = Map.of(
            "solar_power", new Plant(6000.0, 100, "electricity", null, 0.0),
            "wind_power", new Plant(3500.0, 100, "electricity", null, 0.0),
            "hydropower", new Plant(5000.0, 100, "electricity", null, 0.0),
            "thermoelectric_power", new Plant(4500.0, 100, "electricity", null, 0.0),
            "combustion_power", new Plant(4500.0, 100, "electricity", "hydrocarbons", 100.0),
            "nuclear_power_app", new Plant(10000.0, 100, "nuclear_fission", "refined_uranium", 10.0),
            "fusion_power_app", new Plant(25000.0, 100, "nuclear_fusion", "fusion_fuel_pellets", 5.0),
            "antimatter_power_app", new Plant(50000.0, 100, "antimatter",
                    "antimatter_containment_cell", 1.0));

    private PowerPlantCatalog() {}

    public static Plant find(String applicationId) {
        return PLANTS.get(applicationId);
    }

    public static Map<String, Plant> all() {
        return PLANTS;
    }
}
