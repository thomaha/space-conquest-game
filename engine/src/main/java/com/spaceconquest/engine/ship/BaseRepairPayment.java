package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import java.util.*;

/** Quotes a complete material recipe plus labor before atomically paying for actual repair work. */
final class BaseRepairPayment {
    record Work(GameState state, double points) {}
    record Quote(double points, double credits, Map<String, Double> shortages, String blocker) {
        Quote { shortages = Map.copyOf(shortages); }
    }
    private BaseRepairPayment() {}
    static Work buy(GameState state, String stationId, String owner, Map<String, Double> perPoint, double requested) {
        var quote = quote(state, stationId, owner, perPoint, requested);
        double points = quote.points();
        if (points <= 1e-9) return new Work(state, 0);
        var hub = state.commercialHubs().stream().filter(item -> stationId.equals(item.entityId())).findFirst().orElseThrow();
        var request = new HashMap<String, Double>();
        for (var ingredient : perPoint.entrySet()) request.put(ingredient.getKey(), ingredient.getValue() * points);
        var paid = labor(state, owner, hub.id(), points * BaseRepairCatalog.LABOR_CREDITS_PER_POINT);
        var purchase = ConstructionMaterials.buyUpTo(paid, stationId, owner, request);
        return purchase.covers(request) ? new Work(purchase.state(), points) : new Work(state, 0);
    }

    static Quote quote(GameState state, String stationId, String owner, Map<String, Double> perPoint, double requested) {
        if (requested <= 1e-9) return new Quote(0, 0, Map.of(), "No repair needed");
        var hub = state.commercialHubs().stream().filter(item -> stationId.equals(item.entityId())).findFirst().orElse(null);
        if (hub == null) return new Quote(0, 0, Map.of(), "No local commercial hub");
        double points = requested, unitCost = BaseRepairCatalog.LABOR_CREDITS_PER_POINT;
        var shortages = new HashMap<String, Double>();
        String invalidMaterial = "";
        for (var ingredient : new TreeMap<>(perPoint).entrySet()) {
            var order = hub.activeOrders().get(ingredient.getKey());
            double stocked = order == null || !Double.isFinite(order.supplyKg()) ? 0 : Math.max(0, order.supplyKg());
            double missing = Math.max(0, requested * ingredient.getValue() - stocked);
            if (missing > 1e-9) shortages.put(ingredient.getKey(), missing);
            if (order == null || !Double.isFinite(order.supplyKg()) || order.supplyKg() <= 0
                    || !Double.isFinite(order.pricePerKg()) || order.pricePerKg() < 0) {
                if (invalidMaterial.isEmpty()) invalidMaterial = ingredient.getKey();
                continue;
            }
            points = Math.min(points, stocked / ingredient.getValue());
            unitCost += ingredient.getValue() * order.pricePerKg();
        }
        if (!invalidMaterial.isEmpty()) return new Quote(0, 0, shortages, "Missing local material or valid price: " + invalidMaterial);
        if (!Double.isFinite(unitCost)) return new Quote(0, 0, shortages, "Invalid repair price");
        double funds = cash(state, owner);
        boolean cashLimited = funds / unitCost < points;
        points = Math.min(points, funds / unitCost);
        if (!Double.isFinite(points) || points <= 1e-9) return new Quote(0, 0, shortages, "Insufficient repair funds");
        return new Quote(points, Double.isInfinite(funds) ? 0 : points * unitCost, shortages,
                cashLimited ? "Limited by repair funds" : shortages.isEmpty() ? "" : "Limited by local material stock");
    }
    private static double cash(GameState state, String owner) {
        var corporation = state.corporations().stream().filter(item -> owner.equals(item.id())).findFirst().orElse(null);
        if (corporation != null) return Math.max(0, corporation.liquidCapitalReserves());
        return state.empires().stream().filter(item -> owner.equals(item.id()))
                .mapToDouble(item -> hive(item) ? Double.POSITIVE_INFINITY : Math.max(0, item.treasuryCredits())).findFirst().orElse(0);
    }
    private static boolean hive(Empire empire) {
        return empire.societyStructure() != null && empire.societyStructure().toLowerCase(Locale.ROOT).contains("hive");
    }
    private static GameState labor(GameState state, String owner, String hub, double credits) {
        if (state.empires().stream().anyMatch(empire -> owner.equals(empire.id()) && hive(empire))) return state;
        var accounts = new ArrayList<>(state.marketAccounts());
        var old = accounts.stream().filter(account -> hub.equals(account.hubId())).findFirst().orElse(null);
        accounts.removeIf(account -> hub.equals(account.hubId()));
        accounts.add(new MarketAccount(hub, credits + (old == null ? 0 : old.unsettledSalesCredits())));
        var empires = state.empires().stream().map(empire -> !owner.equals(empire.id()) ? empire :
                new Empire(empire.id(), empire.name(), empire.raceId(), empire.societyStructure(),
                        Math.max(0, empire.treasuryCredits() - credits), empire.corporateTaxRate(), empire.controlledSystemIds(),
                        empire.ministries(), empire.systemGovernorAssignments(), empire.unlockedTechIds(), empire.activeShipDesignIds())).toList();
        var corporations = state.corporations().stream().map(corporation -> !owner.equals(corporation.id()) ? corporation :
                new Corporation(corporation.id(), corporation.name(), corporation.empireId(), corporation.headquartersEntityId(),
                        corporation.marketOrientation(), Math.max(0, corporation.liquidCapitalReserves() - credits),
                        corporation.ownedFacilityIds(), corporation.ownedShipIds(), corporation.claimedVeinIds())).toList();
        return state.toBuilder().empires(empires).corporations(corporations).marketAccounts(accounts).build();
    }
}
