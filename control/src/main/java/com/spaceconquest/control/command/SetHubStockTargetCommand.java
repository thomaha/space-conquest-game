package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.market.HubStockTarget;
import com.spaceconquest.engine.ship.FleetPositioning;
import java.util.HashMap;

/** Configures proactive hub procurement on the command tick. Zero/zero restores automatic stockpile policy. */
public record SetHubStockTargetCommand(String empireId, String hubId, String materialId,
                                       double reorderThresholdKg, double targetStockKg) implements GameCommand {
    @Override
    public boolean validate(GameState state) {
        if (state == null || empireId == null || hubId == null || materialId == null
                || !Double.isFinite(reorderThresholdKg) || !Double.isFinite(targetStockKg)
                || reorderThresholdKg < 0 || targetStockKg < reorderThresholdKg) return false;
        var hub = state.commercialHubs().stream().filter(item -> hubId.equals(item.id())).findFirst().orElse(null);
        if (hub == null || state.commercialHubs().stream().noneMatch(item -> item.activeOrders().containsKey(materialId))) return false;
        String system = FleetPositioning.systemForHub(state, hub);
        if (system == null || state.empires().stream().noneMatch(empire -> empireId.equals(empire.id())
                && empire.controlledSystemIds().contains(system))) return false;
        double allocated = hub.stockTargets().entrySet().stream().filter(item -> !materialId.equals(item.getKey()))
                .mapToDouble(item -> item.getValue().targetStockKg()).sum();
        return allocated + targetStockKg <= hub.storageCapacityKg();
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) return state;
        return state.withCommercialHubs(state.commercialHubs().stream().map(hub -> {
            if (!hubId.equals(hub.id())) return hub;
            var targets = new HashMap<>(hub.stockTargets());
            if (targetStockKg == 0) targets.remove(materialId);
            else targets.put(materialId, new HubStockTarget(reorderThresholdKg, targetStockKg));
            var orders = new HashMap<>(hub.activeOrders());
            if (targetStockKg > 0) orders.putIfAbsent(materialId, new com.spaceconquest.engine.MarketOrder(
                    materialId, 0, 0, com.spaceconquest.engine.market.MarketProcessor.basePricePerKg(materialId), 0));
            return new com.spaceconquest.engine.CommercialHub(hub.id(), hub.entityId(), hub.transactionTariffRate(),
                    hub.storageCapacityKg(), hub.currentStoredWeightKg(), hub.logisticsRangeUnits(), orders, targets, hub.stockHistory(), hub.reservedTradeFuel());
        }).toList());
    }
}
