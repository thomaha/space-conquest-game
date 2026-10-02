package com.spaceconquest.engine.market;

import com.spaceconquest.engine.MarketOrder;

import java.util.Map;

/** Shared minimum inventory targets used by market demand and physical freight sales. */
public final class MarketStockpilePolicy {
    private static final int REFILL_DAYS = 30;
    private static final Map<String, Double> BASE_TARGETS_KG = Map.of(
            "food_matrix", 10_000.0, "purified_water", 10_000.0,
            "oxygen_gas", 1_000.0, "consumer_goods", 5_000.0,
            "refined_iron", 5_000.0,
            "steel", 3_000.0, "refined_aluminum", 2_000.0,
            "refined_copper", 1_000.0, "silicon", 500.0);

    private MarketStockpilePolicy() {}

    public static double baseTargetKg(String resourceId) {
        return BASE_TARGETS_KG.getOrDefault(resourceId, 0.0);
    }

    public static Map<String, Double> baseTargetsKg() {
        return BASE_TARGETS_KG;
    }

    public static double targetStockKg(MarketOrder order) {
        if (order == null || !Double.isFinite(order.demandKg()) || order.demandKg() <= 0.0)
            return 0.0;
        double supply = Math.max(0.0, order.supplyKg());
        double base = baseTargetKg(order.resourceId());
        double totalDemand = order.demandKg();
        double fixedTargetUse = Math.max(0.0, totalDemand
                - Math.max(0.0, base - supply) / REFILL_DAYS);
        if (fixedTargetUse * REFILL_DAYS <= base) return base;
        double replenishmentUse = (totalDemand + supply / REFILL_DAYS) / 2.0;
        return Math.max(base, replenishmentUse * REFILL_DAYS);
    }
}
