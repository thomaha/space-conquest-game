package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;

/**
 * Interface representing a validated game command modifying the simulation state.
 */
public interface GameCommand {

    /**
     * Validates whether the command can be safely applied to the given state.
     *
     * @param state the current simulation state
     * @return true if valid, false otherwise
     */
    boolean validate(GameState state);

    /**
     * Applies the command and returns the resulting immutable GameState.
     *
     * @param state the current simulation state
     * @return updated GameState
     */
    GameState apply(GameState state);
}
