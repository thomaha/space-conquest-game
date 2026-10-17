package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.economy.EconomicOwnership;
import com.spaceconquest.engine.industry.ConstructionProgress;

/** Holds the current work fee before buying materials, then pays only for work those materials actually support. */
final class ContractShipConstruction {
    private ContractShipConstruction() {}
    record Step(ConstructionProgress.Step progress, ShipyardContract contract) {}
    static Step advance(GameState state, ShipConstructionOrder order, double capacity, boolean orbital) {
        var contract = order.yardContract(); double daily = Math.min(Math.max(0, capacity), Math.max(0, order.requiredWorkHours() - order.accumulatedWorkHours()));
        double rate = contract == null ? 0 : contract.feeCreditsPerWorkUnit();
        if (rate > 0) daily = Math.min(daily, Math.max(0, EconomicOwnership.cash(state, order.ownerEntityId())) / rate);
        double hold = rate * daily;
        var held = hold > 0 ? ShipyardPayments.change(state, order.ownerEntityId(), -hold) : state;
        var step = orbital ? ConstructionProgress.advanceOrbital(held, order.systemId(), order.yardBodyId(), order.ownerEntityId(), order.requiredMaterialsKg(),
                order.consumedMaterialsKg(), order.accumulatedWorkHours(), order.requiredWorkHours(), daily)
                : ConstructionProgress.advance(held, order.yardBodyId(), order.ownerEntityId(), order.requiredMaterialsKg(),
                order.consumedMaterialsKg(), order.accumulatedWorkHours(), order.requiredWorkHours(), daily);
        double used = Math.max(0, step.workHours() - order.accumulatedWorkHours()); double fee = used * rate;
        var paid = hold > 0 ? ShipyardPayments.change(step.state(), order.ownerEntityId(), Math.max(0, hold - fee)) : step.state();
        if (fee > 0) paid = ShipyardPayments.change(paid, contract.providerOwnerId(), fee);
        return new Step(new ConstructionProgress.Step(paid, step.workHours(), step.consumedKg(), step.complete()), contract == null ? null : contract.withPayment(fee));
    }
}
