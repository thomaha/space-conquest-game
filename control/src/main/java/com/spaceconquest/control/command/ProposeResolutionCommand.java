package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.community.GalacticCommunity;
import com.spaceconquest.engine.community.GalacticResolution;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Command to introduce a legislative resolution to the Galactic Senate assembly.
 */
public record ProposeResolutionCommand(
        String proposerEmpireId,
        String title,
        String resolutionType,
        String targetEmpireId
) implements GameCommand {

    @Override
    public boolean validate(GameState state) {
        return state != null && proposerEmpireId != null && resolutionType != null;
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        GalacticCommunity community = state.galacticCommunity();
        if (community == null) {
            List<String> memberIds = state.empires().stream().map(e -> e.id()).toList();
            community = new GalacticCommunity(
                    "galactic_community", "Galactic Senate",
                    memberIds, List.of(), List.of(), List.of(), 10, state.turn() + 10
            );
        }

        GalacticResolution res = new GalacticResolution(
                "res_" + resolutionType.toLowerCase() + "_" + UUID.randomUUID().toString().substring(0, 6),
                title != null ? title : "Senate Resolution " + resolutionType,
                resolutionType,
                proposerEmpireId,
                targetEmpireId != null ? targetEmpireId : "",
                5,
                GalacticResolution.STATUS_PROPOSED,
                Map.of(proposerEmpireId, GalacticResolution.VOTE_AYE)
        );

        List<GalacticResolution> active = new ArrayList<>(community.activeResolutions());
        active.add(res);

        GalacticCommunity updated = new GalacticCommunity(
                community.id(), community.name(), community.memberEmpireIds(),
                active, community.passedResolutions(), community.activeSanctions(),
                community.senateSessionInterval(), community.nextSenateSessionTurn()
        );

        return state.toBuilder()
                .galacticCommunity(updated)
                .build();
    }
}
