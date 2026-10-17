package com.spaceconquest.engine.combat;

import java.util.Map;

/** Application-defined chassis captured at order time; all capacities and materials come from data. */
public record AuxiliaryHardware(String kind, Map<String, Double> materialCostsKg, Map<String, Double> stats, String applicationId) {
    public AuxiliaryHardware(String kind, Map<String, Double> materials, Map<String, Double> stats) { this(kind, materials, stats, null); }
    public AuxiliaryHardware {
        materialCostsKg = Map.copyOf(materialCostsKg); stats = Map.copyOf(stats);
        for (var map : java.util.List.of(materialCostsKg, stats)) for (double value : map.values())
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid auxiliary hardware value");
        if (!java.util.Set.of("WING", "BATTERY").contains(kind)) throw new IllegalArgumentException("Invalid auxiliary kind");
        for (String key : java.util.List.of("hullHealth", "magazineKg", "energyCapacityKwh"))
            if (!stats.containsKey(key)) throw new IllegalArgumentException("Missing auxiliary capability: " + key);
        if (stats.get("hullHealth") <= 0) throw new IllegalArgumentException("Missing auxiliary hull");
        if (kind.equals("BATTERY")) for (String key : java.util.List.of("surfaceSlots", "bodyDefenseSlots", "armorThicknessCm"))
            if (!stats.containsKey(key)) throw new IllegalArgumentException("Missing battery capability: " + key);
        if (kind.equals("BATTERY") && (stats.get("surfaceSlots") <= 0 || stats.get("surfaceSlots") != Math.floor(stats.get("surfaceSlots"))
                || stats.get("bodyDefenseSlots") < stats.get("surfaceSlots"))) throw new IllegalArgumentException("Invalid surface slots");
    }
    public double value(String key) { return stats.getOrDefault(key, 0.0); }
    public void checkStores(AuxiliaryWeaponStores stores, int units) {
        if (stores.energyKwh() > units * value("energyCapacityKwh") + 1e-9
                || stores.ammunitionKg().values().stream().mapToDouble(Double::doubleValue).sum() > units * value("magazineKg") + 1e-9)
            throw new IllegalArgumentException("Auxiliary storage capacity exceeded");
    }
}
