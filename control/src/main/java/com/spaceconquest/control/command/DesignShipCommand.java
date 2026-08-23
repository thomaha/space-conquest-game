package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.ShipDesign;

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
        if (state == null || shipDesign == null || shipDesign.id() == null) {
            return false;
        }
        return true;
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        List<ShipDesign> updatedDesigns = new ArrayList<>(state.shipDesigns());
        updatedDesigns.removeIf(d -> d.id().equals(shipDesign.id()));
        updatedDesigns.add(shipDesign);

        return new GameState(
                state.turn(),
                state.status(),
                state.solarSystems(),
                state.empires(),
                state.corporations(),
                state.commercialHubs(),
                state.shadowSyndicates(),
                state.diplomaticRelations(),
                state.systemGovernors(),
                state.researchProjects(),
                state.technologyExchangeRoutes(),
                updatedDesigns,
                state.fleets(),
                state.geologicalDeposits(),
                state.powerGrids(),
                state.industrialFacilities(),
                state.expansionProjects()
        );
    }
}
