package com.spaceconquest.engine.industry;

import java.util.Map;

/** Persistent unsold stock and the most recent day's realized facility transactions. */
public record IndustryAccount(
        String facilityId,
        Map<String, Double> unsoldStockKg,
        Map<String, Double> producedKg,
        Map<String, Double> soldKg,
        double inputCostsCredits,
        double wageCostsCredits,
        double salesCredits,
        double tariffCredits,
        double generatedKwh,
        double powerCostsCredits
) {
    public IndustryAccount {
        unsoldStockKg = unsoldStockKg == null ? Map.of() : Map.copyOf(unsoldStockKg);
        producedKg = producedKg == null ? Map.of() : Map.copyOf(producedKg);
        soldKg = soldKg == null ? Map.of() : Map.copyOf(soldKg);
    }

    public double realizedResultCredits() {
        return salesCredits - inputCostsCredits - wageCostsCredits - tariffCredits - powerCostsCredits;
    }

    public IndustryAccount withPowerSale(double credits) {
        return new IndustryAccount(facilityId, unsoldStockKg, producedKg, soldKg,
                inputCostsCredits, wageCostsCredits, salesCredits + credits, tariffCredits,
                generatedKwh, powerCostsCredits);
    }

    public IndustryAccount withPowerCost(double credits) {
        return new IndustryAccount(facilityId, unsoldStockKg, producedKg, soldKg,
                inputCostsCredits, wageCostsCredits, salesCredits, tariffCredits,
                generatedKwh, powerCostsCredits + credits);
    }
}
