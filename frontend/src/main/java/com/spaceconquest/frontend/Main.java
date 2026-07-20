package com.spaceconquest.frontend;

import com.spaceconquest.control.Controller;
import com.spaceconquest.control.HumanController;
import com.spaceconquest.engine.GameEngine;
import com.spaceconquest.engine.MockGameEngine;

public class Main {
    public static void main(String[] args) {
        System.out.println("Starting Space Conquest Game...");

        GameEngine engine = new MockGameEngine();
        Controller player = new HumanController();

        engine.start();
        
        // Simple game loop simulation
        for (int i = 0; i < 3; i++) {
            engine.update();
            player.onGameStateUpdate(engine.getGameState());
        }

        engine.stop();
        System.out.println("Game Over.");
    }
}
