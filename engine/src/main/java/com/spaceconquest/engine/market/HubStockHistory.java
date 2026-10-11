package com.spaceconquest.engine.market;

import com.spaceconquest.engine.MarketOrder;
import java.util.*;

/** Tick-owned gross stock movements. Missing observations are never treated as measured zero demand. */
public record HubStockHistory(List<Day> days) {
    public static final int WINDOW_DAYS = 30;
    // Keep the incomplete current day in addition to the completed rolling window.
    public record Flow(double withdrawnKg, double deliveredKg, boolean stockout) {}
    public record Day(long turn, Map<String, Flow> materials) {
        public Day { materials = Map.copyOf(materials); }
    }
    public record Average(int observedDays, int stockoutDays, double withdrawnKg, double deliveredKg) {
        public double dailyWithdrawalKg() { return observedDays == 0 ? 0 : withdrawnKg / observedDays; }
        public double dailyDeliveryKg() { return observedDays == 0 ? 0 : deliveredKg / observedDays; }
    }
    public HubStockHistory {
        days = days == null ? List.of() : List.copyOf(days);
    }
    public static HubStockHistory empty() { return new HubStockHistory(List.of()); }
    public long currentTurn() { return days.isEmpty() ? 0 : days.getLast().turn(); }

    public HubStockHistory observe(long turn, Map<String, MarketOrder> orders) {
        return change(turn, orders, orders);
    }

    public HubStockHistory change(long turn, Map<String, MarketOrder> before, Map<String, MarketOrder> after) {
        var retained = new TreeMap<Long, Day>();
        for (var day : days) if (day.turn() >= turn - WINDOW_DAYS && day.turn() <= turn) retained.put(day.turn(), day);
        var flows = new HashMap<String, Flow>();
        if (retained.containsKey(turn)) flows.putAll(retained.get(turn).materials());
        var materials = new HashSet<>(before.keySet());
        materials.addAll(after.keySet());
        for (String material : materials) {
            double previous = stock(before.get(material)), next = stock(after.get(material));
            var old = flows.getOrDefault(material, new Flow(0, 0, false));
            flows.put(material, new Flow(old.withdrawnKg() + Math.max(0, previous - next),
                    old.deliveredKg() + Math.max(0, next - previous), old.stockout() || previous <= 1e-6 || next <= 1e-6));
        }
        retained.put(turn, new Day(turn, flows));
        return new HubStockHistory(List.copyOf(retained.values()));
    }

    /** Only completed observed days within the last 30 calendar days contribute to the denominator. */
    public Average average(String material, long currentTurn) {
        int observed = 0, empty = 0;
        double withdrawn = 0, delivered = 0;
        for (var day : days) {
            if (day.turn() < currentTurn - WINDOW_DAYS || day.turn() >= currentTurn) continue;
            var flow = day.materials().get(material);
            if (flow == null) continue;
            observed++;
            if (flow.stockout()) empty++;
            withdrawn += flow.withdrawnKg();
            delivered += flow.deliveredKg();
        }
        return new Average(observed, empty, withdrawn, delivered);
    }

    private static double stock(MarketOrder order) {
        return order == null || !Double.isFinite(order.supplyKg()) ? 0 : Math.max(0, order.supplyKg());
    }
}
