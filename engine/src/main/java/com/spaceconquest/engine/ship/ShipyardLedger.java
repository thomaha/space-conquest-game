package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.*;
import java.util.*;

/** Bounded display history with unbounded cumulative realized results for reliable tax settlement after trimming or reload. */
public record ShipyardLedger(List<ShipyardReceipt> history, Map<String, Double> cumulativeProfit, long lastPayrollTurn,
        Map<String, ShipDeploymentIntent> deployments, Map<String, String> replacements) {
    public ShipyardLedger {
        history = List.copyOf(history); cumulativeProfit = Map.copyOf(cumulativeProfit);
        deployments = deployments == null ? Map.of() : Map.copyOf(deployments);
        replacements = replacements == null ? Map.of() : Map.copyOf(replacements);
        if (lastPayrollTurn < -1 || cumulativeProfit.values().stream().anyMatch(value -> !Double.isFinite(value))) throw new IllegalArgumentException("Invalid shipyard ledger");
    }
    public ShipyardLedger(List<ShipyardReceipt> history, Map<String, Double> cumulativeProfit, long lastPayrollTurn, Map<String, ShipDeploymentIntent> deployments) {
        this(history, cumulativeProfit, lastPayrollTurn, deployments, Map.of());
    }
    public ShipyardLedger replacement(String sourceId, String completedId) {
        var next = new HashMap<>(replacements); next.put(sourceId, completedId);
        return new ShipyardLedger(history, cumulativeProfit, lastPayrollTurn, deployments, next);
    }
    public ShipyardLedger(List<ShipyardReceipt> history, Map<String, Double> cumulativeProfit, long lastPayrollTurn) {
        this(history, cumulativeProfit, lastPayrollTurn, Map.of());
    }
    public ShipyardLedger deployment(String shipId, ShipDeploymentIntent intent) {
        var next = new HashMap<>(deployments);
        if (intent == null) next.remove(shipId); else next.put(shipId, intent);
        return new ShipyardLedger(history, cumulativeProfit, lastPayrollTurn, next, replacements);
    }
    public static ShipyardLedger empty() { return new ShipyardLedger(List.of(), Map.of(), -1); }
    public ShipyardLedger append(ShipyardReceipt receipt) {
        var rows = new ArrayList<>(history); rows.add(receipt);
        int limit = ShipyardAccessPolicy.current().maximumHistoryEntries();
        if (rows.size() > limit) rows.subList(0, rows.size() - limit).clear();
        var profit = new HashMap<>(cumulativeProfit); profit.merge(receipt.providerId(), receipt.feeCredits() - receipt.wageCostsCredits(), Double::sum);
        return new ShipyardLedger(rows, profit, lastPayrollTurn, deployments, replacements);
    }
    public ShipyardLedger payroll(GameState state) {
        if (lastPayrollTurn == state.turn()) return this;
        var result = this;
        for (var station : state.orbitalStations()) {
            double wages = station.modules().stream().filter(module -> switch (module.type()) {
                case "SHIPYARD_GRID", "CAPITAL_SLIPWAY", "COMPONENT_ASSEMBLY" -> true; default -> false;
            }).mapToDouble(module -> module.dailyWageCostsCredits()).sum();
            if (wages > 0) result = result.append(new ShipyardReceipt(state.turn(), "", "", station.ownerEntityId(), station.systemId(), station.id(), 0, 0, wages, false, "Paid yard payroll"));
        }
        var owners = new HashMap<String, String>();
        state.fleets().stream().flatMap(fleet -> fleet.ships().stream()).filter(ship -> ship.currentHullHealth() > 0).forEach(ship -> owners.put(ship.id(), ship.ownerEntityId()));
        var active = new HashMap<String, String>();
        result.replacements().forEach((source, replacement) -> {
            if (owners.containsKey(source) && Objects.equals(owners.get(source), owners.get(replacement))) active.put(source, replacement);
        });
        return new ShipyardLedger(result.history(), result.cumulativeProfit(), state.turn(), result.deployments(), active);
    }
    public static ShipyardReceipt work(GameState state, ShipConstructionOrder order, double units, double fee, boolean complete, String status) {
        return new ShipyardReceipt(state.turn(), order.id(), order.ownerEntityId(), ShipyardAccess.provider(order), order.systemId(), order.yardBodyId(), units, fee, 0, complete, status);
    }
}
