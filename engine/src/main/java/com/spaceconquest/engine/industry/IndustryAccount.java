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
        double powerCostsCredits,
        double operatingCashCredits,
        boolean publicSubsidyEnabled,
        double subsidyCredits,
        double maintenanceCostsCredits
) {
    public IndustryAccount {
        unsoldStockKg = unsoldStockKg == null ? Map.of() : Map.copyOf(unsoldStockKg);
        producedKg = producedKg == null ? Map.of() : Map.copyOf(producedKg);
        soldKg = soldKg == null ? Map.of() : Map.copyOf(soldKg);
    }

    public IndustryAccount(String facilityId, Map<String, Double> unsoldStockKg,
                           Map<String, Double> producedKg, Map<String, Double> soldKg,
                           double inputCostsCredits, double wageCostsCredits, double salesCredits,
                           double tariffCredits, double generatedKwh, double powerCostsCredits) {
        this(facilityId, unsoldStockKg, producedKg, soldKg, inputCostsCredits, wageCostsCredits,
                salesCredits, tariffCredits, generatedKwh, powerCostsCredits, 0.0, false, 0.0, 0.0);
    }

    public static IndustryAccount empty(String facilityId) {
        return new IndustryAccount(facilityId, Map.of(), Map.of(), Map.of(),
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    }

    public IndustryAccount withOperatingCash(double credits) {
        return new IndustryAccount(facilityId, unsoldStockKg, producedKg, soldKg,
                inputCostsCredits, wageCostsCredits, salesCredits, tariffCredits,
                generatedKwh, powerCostsCredits, credits, publicSubsidyEnabled, subsidyCredits,
                maintenanceCostsCredits);
    }

    public IndustryAccount withPublicSubsidy(boolean enabled) {
        return new IndustryAccount(facilityId, unsoldStockKg, producedKg, soldKg,
                inputCostsCredits, wageCostsCredits, salesCredits, tariffCredits,
                generatedKwh, powerCostsCredits, operatingCashCredits, enabled, subsidyCredits,
                maintenanceCostsCredits);
    }

    public IndustryAccount withSubsidy(double credits) {
        return new IndustryAccount(facilityId, unsoldStockKg, producedKg, soldKg,
                inputCostsCredits, wageCostsCredits, salesCredits, tariffCredits,
                generatedKwh, powerCostsCredits, operatingCashCredits + credits, publicSubsidyEnabled,
                credits, maintenanceCostsCredits);
    }

    public IndustryAccount withDailyTransactions(IndustryAccount day) {
        return new IndustryAccount(facilityId, day.unsoldStockKg, day.producedKg, day.soldKg,
                day.inputCostsCredits, day.wageCostsCredits, day.salesCredits, day.tariffCredits,
                day.generatedKwh, day.powerCostsCredits, operatingCashCredits, publicSubsidyEnabled,
                0.0, day.maintenanceCostsCredits);
    }

    public IndustryAccount withMaintenanceCost(double credits) {
        return new IndustryAccount(facilityId, unsoldStockKg, producedKg, soldKg,
                inputCostsCredits, wageCostsCredits, salesCredits, tariffCredits,
                generatedKwh, powerCostsCredits, operatingCashCredits, publicSubsidyEnabled,
                subsidyCredits, maintenanceCostsCredits + credits);
    }

    public double realizedResultCredits() {
        return salesCredits - inputCostsCredits - wageCostsCredits - tariffCredits
                - powerCostsCredits - maintenanceCostsCredits;
    }

    public IndustryAccount withPowerSale(double credits) {
        return new IndustryAccount(facilityId, unsoldStockKg, producedKg, soldKg,
                inputCostsCredits, wageCostsCredits, salesCredits + credits, tariffCredits,
                generatedKwh, powerCostsCredits, operatingCashCredits, publicSubsidyEnabled,
                subsidyCredits, maintenanceCostsCredits);
    }

    public IndustryAccount withPowerCost(double credits) {
        return new IndustryAccount(facilityId, unsoldStockKg, producedKg, soldKg,
                inputCostsCredits, wageCostsCredits, salesCredits, tariffCredits,
                generatedKwh, powerCostsCredits + credits, operatingCashCredits, publicSubsidyEnabled,
                subsidyCredits, maintenanceCostsCredits);
    }
}
