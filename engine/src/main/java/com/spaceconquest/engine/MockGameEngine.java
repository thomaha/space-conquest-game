package com.spaceconquest.engine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.concurrent.atomic.AtomicBoolean;
public class MockGameEngine implements GameEngine {
    private static final Logger logger = LogManager.getLogger(MockGameEngine.class);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private long turn = 0;

    @Override
    public void start() {
        running.set(true);
        logger.info("Game Engine Started.");
    }

    @Override
    public void stop() {
        running.set(false);
        logger.info("Game Engine Stopped.");
    }

    @Override
    public void update() {
        if (running.get()) {
            turn++;
            logger.info("Processing turn: {}", turn);
        }
    }

    @Override
    public GameState getGameState() {
        return new GameState(turn, running.get() ? "RUNNING" : "STOPPED");
    }
}
