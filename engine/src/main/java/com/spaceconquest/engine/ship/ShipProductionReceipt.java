package com.spaceconquest.engine.ship;

import java.util.*;

/** Frozen quantities and work, without market prices or spending. */
public record ShipProductionReceipt(Map<String, Double> materialsKg, double workHours) {
    public ShipProductionReceipt {
        materialsKg = materialsKg == null ? Map.of() : Map.copyOf(materialsKg);
        if (!Double.isFinite(workHours) || workHours < 0 || materialsKg.entrySet().stream().anyMatch(entry ->
                entry.getKey().isBlank() || !Double.isFinite(entry.getValue()) || entry.getValue() <= 0))
            throw new IllegalArgumentException("Invalid ship production receipt");
    }
    public ShipProductionReceipt scale(double factor) {
        if (!Double.isFinite(factor) || factor <= 0) throw new IllegalArgumentException("Invalid receipt multiplier");
        var materials = new HashMap<String, Double>(); materialsKg.forEach((id, kg) -> materials.put(id, kg * factor));
        return new ShipProductionReceipt(materials, workHours * factor);
    }
    public ShipProductionReceipt plus(ShipProductionReceipt other) {
        var materials = new HashMap<>(materialsKg); other.materialsKg().forEach((id, kg) -> materials.merge(id, kg, Double::sum));
        return new ShipProductionReceipt(materials, workHours + other.workHours());
    }
}
