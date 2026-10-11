package com.spaceconquest.control.command;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.market.HubStockTarget;
import com.spaceconquest.engine.market.MarketProcessor;
import com.spaceconquest.engine.ship.FleetPositioning;
import java.util.HashMap;
import java.util.Map;

/** Atomically applies a reviewed group of material targets on the command tick. */
public record SetHubStockTargetsCommand(String empireId, String hubId, Map<String, HubStockTarget> targets) implements GameCommand {
    public SetHubStockTargetsCommand { targets = Map.copyOf(targets); }

    @Override public boolean validate(GameState state) {
        if (state == null || empireId == null || hubId == null || targets.isEmpty()) return false;
        var hub = state.commercialHubs().stream().filter(item -> hubId.equals(item.id())).findFirst().orElse(null);
        if (hub == null) return false;
        String system = FleetPositioning.systemForHub(state, hub);
        if (system == null || state.empires().stream().noneMatch(empire -> empireId.equals(empire.id())
                && empire.controlledSystemIds().contains(system))) return false;
        if (targets.keySet().stream().anyMatch(id -> state.commercialHubs().stream()
                .noneMatch(item -> item.activeOrders().containsKey(id)))) return false;
        double retained = hub.stockTargets().entrySet().stream().filter(item -> !targets.containsKey(item.getKey()))
                .mapToDouble(item -> item.getValue().targetStockKg()).sum();
        return retained + targets.values().stream().mapToDouble(HubStockTarget::targetStockKg).sum() <= hub.storageCapacityKg();
    }

    @Override public GameState apply(GameState state) {
        if (!validate(state)) return state;
        return state.withCommercialHubs(state.commercialHubs().stream().map(hub -> {
            if (!hubId.equals(hub.id())) return hub;
            var updated = new HashMap<>(hub.stockTargets());
            var orders = new HashMap<>(hub.activeOrders());
            targets.forEach((id, target) -> {
                if (target.targetStockKg() == 0) updated.remove(id);
                else {
                    updated.put(id, new HubStockTarget(target.reorderThresholdKg(), target.targetStockKg()));
                    orders.putIfAbsent(id, new MarketOrder(id, 0, 0, MarketProcessor.basePricePerKg(id), 0));
                }
            });
            return new CommercialHub(hub.id(), hub.entityId(), hub.transactionTariffRate(), hub.storageCapacityKg(),
                    hub.currentStoredWeightKg(), hub.logisticsRangeUnits(), orders, updated, hub.stockHistory(), hub.reservedTradeFuel());
        }).toList());
    }
}
