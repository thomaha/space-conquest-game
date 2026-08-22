package com.spaceconquest.control;

import com.spaceconquest.control.command.CommandQueue;
import com.spaceconquest.control.command.GameCommand;
import com.spaceconquest.engine.GameState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Human player controller handling UI command dispatch and game state updates.
 */
public class HumanController implements Controller {
    private static final Logger logger = LogManager.getLogger(HumanController.class);
    private final CommandQueue commandQueue;
    private GameState lastObservedState;

    public HumanController() {
        this(new CommandQueue());
    }

    public HumanController(CommandQueue commandQueue) {
        this.commandQueue = commandQueue;
    }

    public CommandQueue getCommandQueue() {
        return commandQueue;
    }

    public GameState getLastObservedState() {
        return lastObservedState;
    }

    public void dispatchCommand(GameCommand command) {
        if (commandQueue != null && command != null) {
            commandQueue.submit(command);
        }
    }

    public void stageCommand(GameCommand command) {
        dispatchCommand(command);
    }

    @Override
    public void onGameStateUpdate(GameState state) {
        this.lastObservedState = state;
        if (state != null) {
            logger.info("Human Controller received update: Turn {} [{}]", state.turn(), state.status());
        }
    }
}
