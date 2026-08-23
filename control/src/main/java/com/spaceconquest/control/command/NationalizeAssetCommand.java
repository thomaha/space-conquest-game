package com.spaceconquest.control.command;

import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;

import java.util.ArrayList;
import java.util.List;

/**
 * Command allowing a sovereign empire to nationalize private corporate assets (ships or industrial facilities).
 */
public record NationalizeAssetCommand(
        String empireId,
        String corporationId,
        String assetId
) implements GameCommand {

    @Override
    public boolean validate(GameState state) {
        if (state == null || empireId == null || corporationId == null || assetId == null) return false;

        boolean empireExists = state.empires().stream().anyMatch(e -> e.id().equals(empireId));
        Corporation corp = state.corporations().stream().filter(c -> c.id().equals(corporationId)).findFirst().orElse(null);

        if (!empireExists || corp == null) return false;

        boolean ownsAsset = corp.ownedFacilityIds().contains(assetId) || corp.ownedShipIds().contains(assetId);
        return ownsAsset;
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) return state;

        List<Corporation> updatedCorps = new ArrayList<>();
        for (Corporation c : state.corporations()) {
            if (c.id().equals(corporationId)) {
                List<String> facilities = new ArrayList<>(c.ownedFacilityIds());
                List<String> ships = new ArrayList<>(c.ownedShipIds());
                facilities.remove(assetId);
                ships.remove(assetId);

                updatedCorps.add(new Corporation(
                        c.id(), c.name(), c.empireId(), c.headquartersEntityId(),
                        c.marketOrientation(), c.liquidCapitalReserves(),
                        facilities, ships, c.claimedVeinIds()
                ));
            } else {
                updatedCorps.add(c);
            }
        }

        return new GameState(
                state.turn(),
                state.status(),
                state.solarSystems(),
                state.empires(),
                updatedCorps,
                state.commercialHubs(),
                state.shadowSyndicates(),
                state.diplomaticRelations(),
                state.systemGovernors(),
                state.researchProjects(),
                state.technologyExchangeRoutes(),
                state.shipDesigns(),
                state.fleets(),
                state.geologicalDeposits(),
                state.powerGrids(),
                state.industrialFacilities(),
                state.expansionProjects()
        );
    }
}
