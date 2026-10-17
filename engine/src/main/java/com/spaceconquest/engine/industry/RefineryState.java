package com.spaceconquest.engine.industry;

import java.util.List;
import java.util.Map;

/** Facility-owned physical residue and a bounded latest receipt; intake selection is tick-owned state. */
public record RefineryState(String feedstockMaterialId, String selectedTailingsId,
                           List<RefineryLot> tailings, Receipt latestReceipt) {
    public record Receipt(long turn, String recoveryApplicationId, RefineryLot intake,
                          Map<String, Double> purchasedInputsKg, Map<String, Double> productsKg,
                          RefineryLot residue, double batches) {
        public Receipt { purchasedInputsKg = Map.copyOf(purchasedInputsKg); productsKg = Map.copyOf(productsKg); }
    }
    public RefineryState {
        tailings = tailings == null ? List.of() : List.copyOf(tailings);
        if (tailings.stream().map(RefineryLot::id).distinct().count() != tailings.size())
            throw new IllegalArgumentException("Duplicate refinery tailings lot");
    }
    public static RefineryState empty() { return new RefineryState(null, null, List.of(), null); }
    public double storedKg() { return tailings.stream().mapToDouble(RefineryLot::massKg).sum(); }
    public RefineryState select(String material, String tailingsId) {
        return new RefineryState(material, tailingsId, tailings, latestReceipt);
    }
}
