package com.spaceconquest.engine.combat;

import com.spaceconquest.engine.GameState;

/** Replaceable boundary between daily fleet movement and the current combat implementation. */
@FunctionalInterface
public interface FleetEncounterResolver {
    GameState resolveEncounters(GameState state);
}
