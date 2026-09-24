package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thread-safe staging queue for validated game commands dispatched by human or AI controllers.
 */
public class CommandQueue {
    private static final Logger logger = LogManager.getLogger(CommandQueue.class);
    private final Queue<GameCommand> queue = new ConcurrentLinkedQueue<>();

    /**
     * Submits a command to the queue.
     *
     * @param command the game command to stage
     */
    public void submit(GameCommand command) {
        if (command != null) {
            queue.add(command);
        }
    }

    /**
     * Drains all staged commands, validates them against the current simulation state,
     * applies valid commands sequentially, and returns the resulting immutable GameState.
     *
     * @param currentState the starting GameState
     * @return updated GameState after all valid commands have been applied
     */
    public GameState drainAndExecute(GameState currentState) {
        if (currentState == null) return null;

        GameState state = currentState;
        List<GameCommand> drained = new ArrayList<>();
        GameCommand cmd;
        while ((cmd = queue.poll()) != null) {
            drained.add(cmd);
        }

        for (GameCommand command : drained) {
            if (command.validate(state)) {
                logger.debug("Executing command: {}", command);
                state = command.apply(state);
            } else {
                logger.warn("Rejected invalid command: {}", command);
            }
        }

        return state;
    }

    public int processCommands(com.spaceconquest.engine.SpaceConquestEngine engine) {
        if (engine == null) return 0;
        int count = queue.size();
        GameState before = engine.getGameState();
        GameState updated = drainAndExecute(before);
        engine.applyGameState(updated);
        engine.recordCommandTreasuryChanges(before.empires(), updated.empires());
        return count;
    }

    public int size() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public void clear() {
        queue.clear();
    }
}
