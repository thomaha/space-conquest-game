package com.spaceconquest.control.command;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.market.SupplyProcurement;
import com.spaceconquest.engine.ship.FleetPositioning;

/** The local market controller commits only existing buyer cash on the simulation tick. */
public record CreateSupplyContractCommand(String empireId, String contractId, String facilityId, String hubId,
                                           String materialId, double quantityKg, double wholesalePricePerKg,
                                           long deadlineTurn) implements GameCommand {
    private boolean controls(GameState state) {
        if (state == null || empireId == null) return false;
        var hub = state.commercialHubs().stream().filter(h -> h.id().equals(hubId)).findFirst().orElse(null);
        return hub != null && state.empires().stream().anyMatch(e -> e.id().equals(empireId)
                && e.controlledSystemIds().contains(FleetPositioning.systemForHub(state, hub)));
    }
    @Override public boolean validate(GameState state) {
        return controls(state) && SupplyProcurement.accept(state, contractId, facilityId, hubId, materialId,
                quantityKg, wholesalePricePerKg, deadlineTurn) != state;
    }
    @Override public GameState apply(GameState state) {
        return !controls(state) ? state : SupplyProcurement.accept(state, contractId, facilityId, hubId,
                materialId, quantityKg, wholesalePricePerKg, deadlineTurn);
    }
}
