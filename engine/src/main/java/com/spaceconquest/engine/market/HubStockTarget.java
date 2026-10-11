package com.spaceconquest.engine.market;

/** Kilogram inventory settings and persisted refill intent for one hub material. */
public record HubStockTarget(double reorderThresholdKg, double targetStockKg, boolean refillRequested) {
    public HubStockTarget(double reorderThresholdKg, double targetStockKg) {
        this(reorderThresholdKg, targetStockKg, false);
    }

    public HubStockTarget {
        if (!Double.isFinite(reorderThresholdKg) || !Double.isFinite(targetStockKg)
                || reorderThresholdKg < 0 || targetStockKg < reorderThresholdKg)
            throw new IllegalArgumentException("Stock target must be finite and at least the nonnegative reorder threshold.");
    }

    public HubStockTarget withRefillRequested(boolean requested) {
        return new HubStockTarget(reorderThresholdKg, targetStockKg, requested);
    }
}
