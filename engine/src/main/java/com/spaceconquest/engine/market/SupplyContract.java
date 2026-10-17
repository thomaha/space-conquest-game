package com.spaceconquest.engine.market;

/** Prefunded wholesale purchase from one facility into its local hub. No goods exist until delivery. */
public record SupplyContract(String id, String facilityId, String supplierOwnerId, String hubId, String materialId,
                             double quantityKg, double deliveredKg, double pricePerKg, double escrowCredits,
                             long acceptedTurn, long deadlineTurn, String status) {
    public static final String OPEN = "OPEN", FULFILLED = "FULFILLED", EXPIRED = "EXPIRED", CANCELLED = "CANCELLED";
    public SupplyContract {
        for (String value : new String[]{id, facilityId, supplierOwnerId, hubId, materialId})
            if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing contract identity");
        if (!Double.isFinite(quantityKg) || quantityKg <= 0 || !Double.isFinite(deliveredKg) || deliveredKg < 0
                || deliveredKg > quantityKg || !Double.isFinite(pricePerKg) || pricePerKg <= 0
                || !Double.isFinite(escrowCredits) || escrowCredits < 0 || acceptedTurn < 0 || deadlineTurn < acceptedTurn
                || !java.util.Set.of(OPEN, FULFILLED, EXPIRED, CANCELLED).contains(status))
            throw new IllegalArgumentException("Invalid supply contract");
        double expected = OPEN.equals(status) ? (quantityKg - deliveredKg) * pricePerKg : 0;
        if (Math.abs(escrowCredits - expected) > Math.max(1e-6, expected * 1e-9))
            throw new IllegalArgumentException("Contract escrow does not match outstanding goods");
    }
    public double remainingKg() { return quantityKg - deliveredKg; }
    public boolean openAt(long turn) { return OPEN.equals(status) && turn <= deadlineTurn; }
    public SupplyContract delivered(double quantity) {
        if (!Double.isFinite(quantity) || quantity < 0 || quantity > remainingKg() + 1e-9)
            throw new IllegalArgumentException("Invalid contract delivery");
        double total = Math.min(quantityKg, deliveredKg + quantity);
        boolean done = quantityKg - total <= Math.max(1e-9, quantityKg * 1e-12);
        return new SupplyContract(id, facilityId, supplierOwnerId, hubId, materialId, quantityKg, total, pricePerKg,
                done ? 0 : (quantityKg - total) * pricePerKg, acceptedTurn, deadlineTurn, done ? FULFILLED : OPEN);
    }
    public SupplyContract closed(String reason) {
        return new SupplyContract(id, facilityId, supplierOwnerId, hubId, materialId, quantityKg, deliveredKg, pricePerKg,
                0, acceptedTurn, deadlineTurn, reason);
    }
}
