package com.spaceconquest.engine.logistics;

import java.util.Map;

/** Paid physical fuel held at a port for one ship, outside public market inventory. */
public record ReservedTradeFuel(String ownerEntityId, String designId, Map<String, Double> materialsKg) {
    public ReservedTradeFuel {
        materialsKg = materialsKg == null ? Map.of() : Map.copyOf(materialsKg);
        if (materialsKg.values().stream().anyMatch(kg -> !Double.isFinite(kg) || kg < 0))
            throw new IllegalArgumentException("Invalid reserved fuel mass");
    }
    public double massKg() { return materialsKg.values().stream().mapToDouble(Double::doubleValue).sum(); }
}
