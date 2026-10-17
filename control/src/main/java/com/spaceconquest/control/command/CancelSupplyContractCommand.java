package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.market.*;
import com.spaceconquest.engine.ship.FleetPositioning;

/** Cancels only undelivered obligations and returns their held cash to the original buyer hub. */
public record CancelSupplyContractCommand(String empireId, String contractId) implements GameCommand {
    @Override public boolean validate(GameState state) {
        if (state == null || empireId == null) return false;
        var contract = state.businessLedger().contracts().stream().filter(c -> c.id().equals(contractId)
                && SupplyContract.OPEN.equals(c.status())).findFirst().orElse(null);
        if (contract == null) return false;
        var hub = state.commercialHubs().stream().filter(h -> h.id().equals(contract.hubId())).findFirst().orElse(null);
        return hub != null && state.empires().stream().anyMatch(e -> e.id().equals(empireId)
                && e.controlledSystemIds().contains(FleetPositioning.systemForHub(state, hub)));
    }
    @Override public GameState apply(GameState state) { return validate(state) ? SupplyProcurement.cancel(state, contractId) : state; }
}
