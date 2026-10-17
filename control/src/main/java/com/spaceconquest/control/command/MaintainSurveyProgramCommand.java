package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.SurveyOperations;

/** Requotes the standing allocation against current tick access, research, cash and physical stock. */
public record MaintainSurveyProgramCommand(String empireId, String bodyId, int desiredTeams) implements GameCommand {
    @Override public boolean validate(GameState state) {
        return state != null && desiredTeams >= 1 && desiredTeams <= 3
                && SurveyOperations.maintain(state, empireId, bodyId, desiredTeams) != state;
    }
    @Override public GameState apply(GameState state) {
        return state == null || desiredTeams < 1 || desiredTeams > 3 ? state
                : SurveyOperations.maintain(state, empireId, bodyId, desiredTeams);
    }
}
