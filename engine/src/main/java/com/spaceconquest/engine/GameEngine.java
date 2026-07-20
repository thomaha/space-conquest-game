package com.spaceconquest.engine;

public interface GameEngine {
    void start();
    void stop();
    void update();
    GameState getGameState();
}
