package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.habitation.PassengerStasis;

import java.util.LinkedHashMap;
import java.util.Map;

/** Material bills for the construction paths currently represented by game-state assets. */
public final class ConstructionMaterialCatalog {
    private ConstructionMaterialCatalog() {}

    public static Map<String, Double> facility(String applicationId, int tier) {
        double scale = Math.max(1, tier);
        Map<String, Double> bill = new LinkedHashMap<>();
        bill.put("refined_iron", 500.0 * scale);
        bill.put("refined_aluminum", 150.0 * scale);
        bill.put("refined_copper", 50.0 * scale);
        bill.put("silicon", 10.0 * scale);
        return Map.copyOf(bill);
    }

    public static Map<String, Double> ship(ShipDesign design) {
        double mass = Math.max(1_000.0, design.totalDryMassKg());
        Map<String, Double> bill = new LinkedHashMap<>();
        bill.merge(design.hullMaterialId(), mass * 0.65, Double::sum);
        bill.merge(design.armorMaterialId(), mass * 0.20, Double::sum);
        bill.merge("refined_copper", mass * 0.10, Double::sum);
        bill.merge("silicon", mass * 0.05, Double::sum);
        long pods = design.equippedModuleIds().stream()
                .filter(PassengerStasis.MODULE_ID::equals).count();
        if (pods > 0) {
            bill.merge("refined_aluminum", pods * 100.0, Double::sum);
            bill.merge("refined_copper", pods * 50.0, Double::sum);
        }
        return Map.copyOf(bill);
    }

    public static Map<String, Double> orbitalStation(String armorMaterialId, int slots) {
        Map<String, Double> bill = new LinkedHashMap<>();
        bill.put("refined_iron", Math.max(1, slots) * 500.0);
        bill.merge(armorMaterialId, Math.max(1, slots) * 100.0, Double::sum);
        bill.put("refined_copper", Math.max(1, slots) * 50.0);
        return Map.copyOf(bill);
    }

    public static Map<String, Double> spaceElevator() {
        return Map.of("refined_iron", 50_000.0, "refined_aluminum", 20_000.0,
                "refined_copper", 5_000.0);
    }

    public static Map<String, Double> stationModule(double dryMassKg) {
        return Map.of("refined_iron", Math.max(100.0, dryMassKg * 0.75),
                "refined_copper", Math.max(25.0, dryMassKg * 0.15),
                "silicon", Math.max(10.0, dryMassKg * 0.05));
    }

    public static Map<String, Double> megastructure(String type) {
        double multiplier = type != null && type.contains("GATEWAY") ? 4.0 : 10.0;
        return Map.of("refined_iron", 50_000.0 * multiplier,
                "refined_aluminum", 20_000.0 * multiplier,
                "refined_copper", 5_000.0 * multiplier,
                "silicon", 1_000.0 * multiplier);
    }

    public static Map<String, Double> terraforming(String type) {
        if (type != null && (type.endsWith("SEEDING") || type.contains("ALGAE"))) {
            return Map.of("agricultural_biomass", 1_000.0, "purified_water", 500.0);
        }
        return Map.of("refined_iron", 2_000.0, "refined_copper", 500.0,
                "silicon", 100.0);
    }
}
