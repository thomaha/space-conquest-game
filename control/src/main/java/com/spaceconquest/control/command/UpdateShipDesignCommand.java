package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.ShipBlueprintFactory;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipDesignSpecification;

import java.util.ArrayList;

/** Recalculates an empire-owned blueprint from its edited component choices. */
public record UpdateShipDesignCommand(ShipDesignSpecification specification) implements GameCommand {
    public UpdateShipDesignCommand(ShipDesign submitted) { this(ShipDesignSpecification.from(submitted)); }

    @Override
    public boolean validate(GameState state) {
        return editable(state) && ShipBlueprintFactory.evaluate(state, specification).valid();
    }

    private boolean editable(GameState state) {
        return state != null && specification != null
                && state.fleets().stream().flatMap(fleet -> fleet.ships().stream())
                        .noneMatch(ship -> specification.id().equals(ship.designId()))
                && state.shipConstructionOrders().stream().noneMatch(order -> specification.id().equals(order.designId()))
                && state.shipDesigns().stream().anyMatch(design ->
                design.id().equals(specification.id()) && design.ownerEntityId().equals(specification.ownerEntityId())
                        && !design.isProprietaryCorporateDesign());
    }

    @Override
    public GameState apply(GameState state) {
        if (!editable(state)) return state;
        var evaluation = ShipBlueprintFactory.evaluate(state, specification);
        if (!evaluation.valid()) return state;
        var designs = new ArrayList<>(state.shipDesigns());
        for (int index = 0; index < designs.size(); index++) {
            if (designs.get(index).id().equals(specification.id())) {
                designs.set(index, evaluation.design());
                break;
            }
        }
        return state.withShipDesigns(designs);
    }
}
