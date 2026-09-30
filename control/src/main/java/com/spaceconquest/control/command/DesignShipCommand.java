package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.habitation.PassengerStasis;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.PropulsionCatalog;

import java.util.ArrayList;
import java.util.List;

/**
 * Command to register a validated spaceship blueprint in the galactic design registry.
 */
public record DesignShipCommand(
        ShipDesign shipDesign
) implements GameCommand {

    @Override
    public boolean validate(GameState state) {
        if (state == null || shipDesign == null || shipDesign.id() == null
                || shipDesign.ownerEntityId() == null || shipDesign.isProprietaryCorporateDesign()) {
            return false;
        }
        boolean researched = state.empires().stream().anyMatch(empire ->
                empire.id().equals(shipDesign.ownerEntityId())
                        && empire.unlockedTechIds().contains(PassengerStasis.TECHNOLOGY_ID));
        boolean drivesResearched = state.empires().stream().anyMatch(empire ->
                empire.id().equals(shipDesign.ownerEntityId())
                        && PropulsionCatalog.researched(shipDesign.equippedModuleIds(),
                        empire.unlockedTechIds()));
        return (!shipDesign.equippedModuleIds().contains(PassengerStasis.MODULE_ID) || researched)
                && drivesResearched
                && PropulsionCatalog.validConfiguration(shipDesign.equippedModuleIds(),
                shipDesign.fuelCapacityKg())
                && state.shipDesigns().stream().noneMatch(design -> design.id().equals(shipDesign.id()))
                && state.corporations().stream().noneMatch(corp -> corp.id().equals(shipDesign.ownerEntityId()));
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        List<ShipDesign> updatedDesigns = new ArrayList<>(state.shipDesigns());
        updatedDesigns.add(shipDesign);

        return state.toBuilder()
                .shipDesigns(updatedDesigns)
                .build();
    }
}
