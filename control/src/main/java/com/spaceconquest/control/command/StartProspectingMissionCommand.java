package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.GeologicalDeposit;
import com.spaceconquest.engine.industry.ProspectingProcessor;

import java.util.List;

/**
 * Command to execute a stochastic geological prospecting survey on a planetary body.
 */
public record StartProspectingMissionCommand(
        String planetId,
        String surveyorEntityId
) implements GameCommand {

    public StartProspectingMissionCommand(String surveyorEntityId, String planetId, int staffCount) {
        this(planetId, surveyorEntityId);
    }

    @Override
    public boolean validate(GameState state) {
        if (state == null || planetId == null || surveyorEntityId == null) {
            return false;
        }
        return true;
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        ProspectingProcessor processor = new ProspectingProcessor();
        List<GeologicalDeposit> updatedDeposits = processor.executeProspectingSurvey(
                planetId,
                surveyorEntityId,
                0.40,
                1.0,
                state.geologicalDeposits()
        );

        return state.toBuilder()
                .geologicalDeposits(updatedDeposits)
                .build();
    }
}
