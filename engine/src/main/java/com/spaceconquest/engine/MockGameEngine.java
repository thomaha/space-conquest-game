package com.spaceconquest.engine;

import java.util.concurrent.atomic.AtomicBoolean;

public class MockGameEngine implements GameEngine {
    private final AtomicBoolean running = new AtomicBoolean(false);
    private long turn = 0;

    @Override
    public void start() {
        running.set(true);
        System.out.println("Game Engine Started.");
    }

    @Override
    public void stop() {
        running.set(false);
        System.out.println("Game Engine Stopped.");
    }

    @Override
    public void update() {
        if (running.get()) {
            turn++;
            System.out.println("Processing turn: " + turn);
        }
    }

    @Override
    public GameState getGameState() {
        return new GameState(turn, running.get() ? "RUNNING" : "STOPPED");
    }
}
