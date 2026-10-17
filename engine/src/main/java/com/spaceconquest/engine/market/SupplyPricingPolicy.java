package com.spaceconquest.engine.market;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spaceconquest.engine.*;
import java.io.IOException;

/** Shared economic assumptions, independent of named facilities or commodities. */
public record SupplyPricingPolicy(double targetNetMargin, double capitalRecoveryDays,
                                  double transportOperatingCreditsPerDay, double transportMaintenanceDailyFraction,
                                  int maximumImportCandidates) {
    private static final SupplyPricingPolicy CURRENT = load();
    public SupplyPricingPolicy {
        if (!Double.isFinite(targetNetMargin) || targetNetMargin < 0 || targetNetMargin >= 1
                || !Double.isFinite(capitalRecoveryDays) || capitalRecoveryDays <= 0
                || !Double.isFinite(transportOperatingCreditsPerDay) || transportOperatingCreditsPerDay < 0
                || !Double.isFinite(transportMaintenanceDailyFraction) || transportMaintenanceDailyFraction < 0
                || maximumImportCandidates < 1) throw new IllegalArgumentException("Invalid supply pricing policy");
    }
    public static SupplyPricingPolicy current() { return CURRENT; }
    private static SupplyPricingPolicy load() {
        try (var stream = SupplyPricingPolicy.class.getResourceAsStream("/supply_pricing.json")) {
            if (stream == null) throw new IllegalStateException("Missing supply pricing policy");
            return new ObjectMapper().readValue(stream, SupplyPricingPolicy.class);
        } catch (IOException failure) { throw new IllegalStateException("Cannot load supply pricing policy", failure); }
    }
    public static double taxRate(GameState state, String owner) {
        var corporation = state.corporations().stream().filter(c -> c.id().equals(owner)).findFirst().orElse(null);
        return corporation == null ? 0 : state.empires().stream().filter(e -> e.id().equals(corporation.empireId()))
                .mapToDouble(e -> Math.clamp(e.corporateTaxRate(), 0, 1)).findFirst().orElse(0);
    }
    /** Capital reserves are not deductions in the current realized corporate profit-tax ledger. */
    public double requiredReceipts(double operatingCosts, double capitalReserve, double taxRate) {
        double denominator = 1 - taxRate - targetNetMargin;
        if (!Double.isFinite(operatingCosts) || operatingCosts < 0 || !Double.isFinite(capitalReserve) || capitalReserve < 0
                || !Double.isFinite(taxRate) || taxRate < 0 || taxRate > 1) return Double.POSITIVE_INFINITY;
        return denominator <= 0 ? Double.POSITIVE_INFINITY : ((1 - taxRate) * operatingCosts + capitalReserve) / denominator;
    }
}
