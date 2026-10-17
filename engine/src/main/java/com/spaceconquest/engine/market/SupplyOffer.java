package com.spaceconquest.engine.market;

/** Finite daily capacity and daily costs with a first-delivery delay; never inventory or a guaranteed sale. */
public record SupplyOffer(String supplierId, String materialId, double quantityKg, double retailPricePerKg,
                          double deliveryDays, double operatingCosts, double capitalReserve, double profitTaxRate) {
    public SupplyOffer {
        if (supplierId == null || materialId == null || !Double.isFinite(quantityKg) || quantityKg <= 0
                || !Double.isFinite(retailPricePerKg) || retailPricePerKg <= 0 || !Double.isFinite(deliveryDays) || deliveryDays < 0
                || !Double.isFinite(operatingCosts) || operatingCosts < 0 || !Double.isFinite(capitalReserve) || capitalReserve < 0
                || !Double.isFinite(profitTaxRate) || profitTaxRate < 0 || profitTaxRate > 1)
            throw new IllegalArgumentException("Invalid supply offer");
    }
}
