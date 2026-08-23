package com.spaceconquest.control.command;

import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.espionage.PirateBase;

import java.util.ArrayList;
import java.util.List;

/**
 * Command directed by a shadow syndicate to extort private corporate supply lines.
 */
public record ExtortSupplyLineCommand(
        String pirateBaseId,
        String targetCorporationId,
        double extortionDemandCredits
) implements GameCommand {

    @Override
    public boolean validate(GameState state) {
        if (state == null || pirateBaseId == null || targetCorporationId == null) {
            return false;
        }
        boolean hasBase = state.pirateBases().stream().anyMatch(b -> b.id().equals(pirateBaseId));
        boolean hasCorp = state.corporations().stream().anyMatch(c -> c.id().equals(targetCorporationId));
        return hasBase && hasCorp;
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        Corporation targetCorp = state.corporations().stream()
                .filter(c -> c.id().equals(targetCorporationId))
                .findFirst()
                .orElse(null);
        if (targetCorp == null) return state;

        double extorted = Math.min(extortionDemandCredits, targetCorp.liquidCapitalReserves() * 0.20);

        List<Corporation> updatedCorps = state.corporations().stream().map(c -> {
            if (c.id().equals(targetCorporationId)) {
                return new Corporation(
                        c.id(), c.name(), c.empireId(), c.headquartersEntityId(),
                        c.marketOrientation(), c.liquidCapitalReserves() - extorted,
                        c.ownedFacilityIds(), c.ownedShipIds(), c.claimedVeinIds()
                );
            }
            return c;
        }).toList();

        List<PirateBase> updatedBases = state.pirateBases().stream().map(b -> {
            if (b.id().equals(pirateBaseId)) {
                return new PirateBase(
                        b.id(), b.syndicateId(), b.systemId(), b.celestialLocationId(),
                        b.illicitCapitalStored() + extorted, b.pirateShipCount(), b.isHidden()
                );
            }
            return b;
        }).toList();

        return new GameState(
                state.turn(), state.status(), state.solarSystems(), state.empires(),
                updatedCorps, state.commercialHubs(), state.shadowSyndicates(),
                state.diplomaticRelations(), state.systemGovernors(), state.researchProjects(),
                state.technologyExchangeRoutes(), state.shipDesigns(), state.fleets(),
                state.geologicalDeposits(), state.powerGrids(), state.industrialFacilities(),
                state.expansionProjects(), state.orbitalStations(), state.spaceElevators(),
                state.constructionProjects(), state.sleeperAgents(), state.espionageOperations(),
                updatedBases
        );
    }
}
