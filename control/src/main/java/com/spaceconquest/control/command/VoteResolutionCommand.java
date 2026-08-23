package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.community.GalacticCommunity;
import com.spaceconquest.engine.community.GalacticResolution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Command to cast a democratic vote (AYE, NAY, ABSTAIN) on an active Galactic Senate resolution.
 */
public record VoteResolutionCommand(
        String voterEmpireId,
        String resolutionId,
        String voteChoice
) implements GameCommand {

    @Override
    public boolean validate(GameState state) {
        if (state == null || voterEmpireId == null || resolutionId == null || voteChoice == null) {
            return false;
        }
        return state.galacticCommunity() != null && state.galacticCommunity().activeResolutions().stream()
                .anyMatch(r -> r.id().equalsIgnoreCase(resolutionId));
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        GalacticCommunity community = state.galacticCommunity();
        List<GalacticResolution> updatedActive = new ArrayList<>();

        for (GalacticResolution res : community.activeResolutions()) {
            if (res.id().equalsIgnoreCase(resolutionId)) {
                Map<String, String> votes = new HashMap<>(res.votes());
                votes.put(voterEmpireId, voteChoice);
                updatedActive.add(new GalacticResolution(
                        res.id(), res.title(), res.type(), res.proposerEmpireId(),
                        res.targetEmpireId(), res.sessionTurnsLeft(), res.status(), votes
                ));
            } else {
                updatedActive.add(res);
            }
        }

        GalacticCommunity updated = new GalacticCommunity(
                community.id(), community.name(), community.memberEmpireIds(),
                updatedActive, community.passedResolutions(), community.activeSanctions(),
                community.senateSessionInterval(), community.nextSenateSessionTurn()
        );

        return new GameState(
                state.turn(), state.status(), state.solarSystems(), state.empires(),
                state.corporations(), state.commercialHubs(), state.shadowSyndicates(),
                state.diplomaticRelations(), state.systemGovernors(), state.researchProjects(),
                state.technologyExchangeRoutes(), state.shipDesigns(), state.fleets(),
                state.geologicalDeposits(), state.powerGrids(), state.industrialFacilities(),
                state.expansionProjects(), state.orbitalStations(), state.spaceElevators(),
                state.constructionProjects(), state.sleeperAgents(), state.espionageOperations(),
                state.pirateBases(), state.terraformingProjects(), state.megastructures(), updated
        );
    }
}
