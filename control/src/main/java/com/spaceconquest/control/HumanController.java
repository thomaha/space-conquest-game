package com.spaceconquest.control;

import com.spaceconquest.engine.GameState;

public class HumanController implements Controller {
    @Override
    public void onGameStateUpdate(GameState state) {
        System.out.println("Human Controller received update: Turn " + state.turn() + " [" + state.status() + "]");
    }
}
