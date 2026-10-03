package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.ShipBlueprintFactory;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipDesignSpecification;

import java.util.ArrayList;

/** Registers a blueprint calculated from component choices at command execution time. */
public record DesignShipCommand(ShipDesignSpecification specification) implements GameCommand {
    public DesignShipCommand(ShipDesign submitted) { this(ShipDesignSpecification.from(submitted)); }

    @Override
    public boolean validate(GameState state) {
        return available(state) && ShipBlueprintFactory.evaluate(state, specification).valid();
    }

    private boolean available(GameState state) {
        return state != null && specification != null && state.shipDesigns().stream()
                .noneMatch(design -> design.id().equals(specification.id()));
    }

    @Override
    public GameState apply(GameState state) {
        if (!available(state)) return state;
        var evaluation = ShipBlueprintFactory.evaluate(state, specification);
        if (!evaluation.valid()) return state;
        var designs = new ArrayList<>(state.shipDesigns());
        designs.add(evaluation.design());
        return state.withShipDesigns(designs);
    }
}
