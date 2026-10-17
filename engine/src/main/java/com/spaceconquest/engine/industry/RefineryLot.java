package com.spaceconquest.engine.industry;

import java.util.Map;

/** Actual element masses in an owned intake or retained tailings lot, in kilograms. */
public record RefineryLot(String id, String source, Map<String, Double> elementsKg) {
    public RefineryLot {
        if (id == null || id.isBlank() || source == null || source.isBlank() || elementsKg == null || elementsKg.entrySet().stream()
                .anyMatch(e -> e.getKey() == null || e.getKey().isBlank() || e.getValue() == null || !Double.isFinite(e.getValue()) || e.getValue() < 0)
                || !Double.isFinite(elementsKg.values().stream().mapToDouble(Double::doubleValue).sum()))
            throw new IllegalArgumentException("Invalid refinery lot");
        elementsKg = Map.copyOf(elementsKg);
    }
    public double massKg() { return elementsKg.values().stream().mapToDouble(Double::doubleValue).sum(); }
    public RefineryLot scale(double factor) {
        if (!Double.isFinite(factor) || factor < 0) throw new IllegalArgumentException("Invalid lot fraction");
        var amounts = new java.util.TreeMap<String, Double>();
        elementsKg.forEach((element, mass) -> amounts.put(element, mass * factor));
        return new RefineryLot(id, source, amounts);
    }
}
