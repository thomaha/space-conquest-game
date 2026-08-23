package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;

/**
 * Command to enact emergency planetary martial law, mobilizing reserve militia and suppressing civil unrest.
 */
public record EnactMartialLawCommand(
        String empireId,
        String systemId
) implements GameCommand {

    public EnactMartialLawCommand(String planetId, String empireId, int durationTurns) {
        this(empireId, planetId);
    }

    @Override
    public boolean validate(GameState state) {
        if (state == null || empireId == null || systemId == null) {
            return false;
        }
        return state.empires().stream().anyMatch(e -> e.id().equals(empireId) && e.controlledSystemIds().contains(systemId));
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }
        return state;
    }
}
