package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.scenario.CustomEmpireBuilder;
import com.spaceconquest.engine.scenario.CustomEmpireProfile;

/**
 * Command to instantiate and register a custom sovereign empire into the game state.
 */
public record CreateCustomEmpireCommand(
        CustomEmpireProfile profile
) implements GameCommand {

    private static final CustomEmpireBuilder BUILDER = new CustomEmpireBuilder();

    @Override
    public boolean validate(GameState state) {
        if (state == null || profile == null) {
            return false;
        }
        return BUILDER.validateProfile(profile);
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }
        return BUILDER.applyToGameState(state, profile);
    }
}
