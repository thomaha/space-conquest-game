package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.habitation.PassengerStasis;
import com.spaceconquest.engine.ship.PropulsionCatalog;
import com.spaceconquest.engine.ship.ShipDesign;

import java.util.ArrayList;
import java.util.List;

/** Replaces an empire-owned public ship blueprint with its edited specification. */
public record UpdateShipDesignCommand(ShipDesign shipDesign) implements GameCommand {
    @Override
    public boolean validate(GameState state) {
        if (state == null || shipDesign == null || shipDesign.id() == null
                || shipDesign.ownerEntityId() == null || shipDesign.isProprietaryCorporateDesign())
            return false;
        var existing = state.shipDesigns().stream().filter(design ->
                design.id().equals(shipDesign.id())).findFirst().orElse(null);
        if (existing == null || !existing.ownerEntityId().equals(shipDesign.ownerEntityId())
                || state.corporations().stream().anyMatch(corp ->
                corp.id().equals(shipDesign.ownerEntityId()))) return false;
        var owner = state.empires().stream().filter(empire ->
                empire.id().equals(shipDesign.ownerEntityId())).findFirst().orElse(null);
        if (owner == null) return false;
        return (!shipDesign.equippedModuleIds().contains(PassengerStasis.MODULE_ID)
                || owner.unlockedTechIds().contains(PassengerStasis.TECHNOLOGY_ID))
                && PropulsionCatalog.researched(shipDesign.equippedModuleIds(), owner.unlockedTechIds())
                && PropulsionCatalog.validConfiguration(shipDesign.equippedModuleIds(),
                shipDesign.fuelCapacityKg());
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) return state;
        List<ShipDesign> updated = new ArrayList<>(state.shipDesigns());
        for (int index = 0; index < updated.size(); index++) {
            if (updated.get(index).id().equals(shipDesign.id())) {
                updated.set(index, shipDesign);
                break;
            }
        }
        return state.withShipDesigns(updated);
    }
}
