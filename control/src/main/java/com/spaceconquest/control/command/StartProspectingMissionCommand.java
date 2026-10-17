package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.SurveyOperations;

/** Establishes a standing survey team; discoveries require subsequent paid work. */
public record StartProspectingMissionCommand(String planetId, String surveyorEntityId) implements GameCommand {
    @Override public boolean validate(GameState state) {
        return state != null && SurveyOperations.maintain(state, surveyorEntityId, planetId, 1) != state;
    }
    @Override public GameState apply(GameState state) {
        return SurveyOperations.maintain(state, surveyorEntityId, planetId, 1);
    }
}
