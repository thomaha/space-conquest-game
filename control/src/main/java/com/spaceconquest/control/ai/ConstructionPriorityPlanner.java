package com.spaceconquest.control.ai;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.EconomicOwnership;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Reorders only affiliated provider queues; readiness quotes never spend the source snapshot. */
public final class ConstructionPriorityPlanner {
    private ConstructionPriorityPlanner() {}
    public static List<ShipConstructionOrder> rank(GameState state, Empire empire) {
        var groups = new LinkedHashMap<String, List<ShipConstructionOrder>>();
        for (var order : state.shipConstructionOrders()) {
            var home = EconomicOwnership.empireFor(state, ShipyardAccess.provider(order));
            if (home != null && empire.id().equals(home.id()) && empire.controlledSystemIds().contains(order.systemId())
                    && (empire.id().equals(ShipyardAccess.provider(order)) || EconomicOwnership.corporationsAllowed(empire)))
                groups.computeIfAbsent(key(order), ignored -> new ArrayList<>()).add(order);
        }
        var policy = ConstructionPriorityPolicy.current();
        boolean war = state.diplomaticRelations().stream().anyMatch(relation -> "TOTAL_WAR".equals(relation.tier())
                && (empire.id().equals(relation.empireAId()) || empire.id().equals(relation.empireBId())));
        boolean supply = !FacilitySupplyNeeds.shortages(state, empire, LogisticsPolicy.current()).isEmpty();
        var ranked = new HashMap<String, ArrayDeque<ShipConstructionOrder>>();
        groups.forEach((key, orders) -> {
            if (orders.size() > policy.maximumOrdersPerYard()) return;
            var values = orders.stream().map(order -> prioritize(state, empire, order, war, supply, policy)).toList();
            var sorted = new ArrayList<>(values);
            sorted.sort(Comparator.comparingInt((ShipConstructionOrder order) -> !order.priority().ready() ? 2 : order.priority().protectedTurn() ? 0 : 1)
                    .thenComparingDouble(order -> order.priority().protectedTurn() ? 0 : -order.priority().score())
                    .thenComparingLong(order -> order.priority().queuedTurn())); // Stable ties preserve the saved queue.
            ranked.put(key, new ArrayDeque<>(sorted));
        });
        var result = new ArrayList<ShipConstructionOrder>();
        for (var order : state.shipConstructionOrders()) {
            var queue = ranked.get(key(order));
            var home = EconomicOwnership.empireFor(state, ShipyardAccess.provider(order));
            result.add(queue != null && home != null && empire.id().equals(home.id())
                    && (empire.id().equals(ShipyardAccess.provider(order)) || EconomicOwnership.corporationsAllowed(empire)) ? queue.removeFirst() : order);
        }
        return List.copyOf(result);
    }
    private static String key(ShipConstructionOrder order) { return order.systemId() + "/" + order.yardBodyId(); }
    private static ShipConstructionOrder prioritize(GameState state, Empire empire, ShipConstructionOrder order, boolean war, boolean supply, ConstructionPriorityPolicy policy) {
        var quote = ShipConstructionReadiness.quote(state, order); var previous = order.priority();
        boolean retained = previous != null && empire.id().equals(previous.empireId()) && previous.rankedTurn() <= state.turn();
        int wait = !quote.ready() || !retained ? 0 : previous.rankedTurn() == state.turn() ? previous.waitingTurns()
                : (int) Math.min(policy.starvationWaitTurns(), (long) previous.waitingTurns() + 1);
        long queued = retained ? previous.queuedTurn() : state.turn();
        var design = state.shipDesigns().stream().filter(item -> order.designId().equals(item.id()) && order.ownerEntityId().equals(item.ownerEntityId())).findFirst().orElse(null);
        double score = 0; String reason = quote.reason();
        if (quote.ready() && design != null) {
            boolean combat = order.retirement() == null && EmpireFleetPlanner.combat(design.role());
            score = combat ? (war ? policy.wartimeCombat() : policy.peacetimeCombat())
                    + suppression(design, policy) * policy.suppression() * (war ? 1 : policy.peacetimeSuppressionFraction()) : 0;
            reason = combat ? war ? "Wartime threat reduction estimate" : "Combat reinforcement estimate" : order.retirement() != null ? "Paid retirement" : "Ordinary construction";
            if (order.retirement() == null && supply && ShipRole.CARGO_TRANSPORT.equals(design.role()) && design.maxCargoMassKg() > 0) { score += policy.essentialSupply(); reason = "Essential facility supply"; }
            if (order.retirement() == null && replacement(state, empire, design, policy)) { score += policy.replacement(); reason += "; fleet replacement"; }
            score += policy.completion() / (1 + quote.remainingDays()) + policy.waiting() * wait;
            if (order.yardContract() != null) score += Math.min(policy.maximumContractScore(), policy.contractWeight()
                    * order.yardContract().feeCreditsPerWorkUnit() * quote.workPerDay());
            reason += String.format(java.util.Locale.ROOT, "; %.1f work days remaining", quote.remainingDays());
        }
        boolean protectedTurn = quote.ready() && wait >= policy.starvationWaitTurns();
        if (protectedTurn) reason = "Protected waiting order; " + reason;
        return order.withPriority(new ShipConstructionPriority(empire.id(), queued, state.turn(), wait,
                Math.min(policy.maximumScore(), score), quote.ready(), protectedTurn, reason));
    }
    private static double suppression(ShipDesign design, ConstructionPriorityPolicy policy) {
        double result = 0;
        for (var module : design.combatModules().values()) {
            var stats = module.operationalStats(); var salvo = WeaponSalvo.of(module);
            result += salvo.damage() * stats.getOrDefault("accuracy", 1.0) * (1 + policy.electronicWeight()
                    * stats.getOrDefault("electronicDamageFraction", 0.0) * stats.getOrDefault("electronicDamageMultiplier", 1.0));
            result += stats.getOrDefault("pointDefenseCapacity", 0.0) * policy.pointDefenseWeight();
        }
        return result;
    }
    private static boolean replacement(GameState state, Empire empire, ShipDesign design, ConstructionPriorityPolicy policy) {
        return state.fleets().stream().filter(fleet -> { var home = EconomicOwnership.empireFor(state, fleet.ownerEntityId()); return home != null && empire.id().equals(home.id()); })
                .flatMap(fleet -> fleet.ships().stream()).anyMatch(ship -> ship.currentHullHealth() < ShipConstructionProcessor.COMMISSIONED_HULL_HEALTH * policy.replacementHullFraction()
                        && state.shipDesigns().stream().anyMatch(old -> old.id().equals(ship.designId()) && old.ownerEntityId().equals(ship.ownerEntityId()) && old.role().equals(design.role())));
    }
}
