package com.spaceconquest.control;

import com.spaceconquest.engine.GameState;

public interface Controller {
    void onGameStateUpdate(GameState state);
    // Methods for the controller to request actions from the engine
}
