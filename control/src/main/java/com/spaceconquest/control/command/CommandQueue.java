package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CompletableFuture;

/**
 * Thread-safe staging queue for validated game commands dispatched by human or AI controllers.
 */
public class CommandQueue {
    private static final Logger logger = LogManager.getLogger(CommandQueue.class);
    private record Submission(GameCommand command, CompletableFuture<CommandOutcome> completion) {}
    private final Queue<Submission> queue = new ConcurrentLinkedQueue<>();

    /**
     * Submits a command to the queue.
     *
     * @param command the game command to stage
     */
    public void submit(GameCommand command) {
        if (command != null) {
            queue.add(new Submission(command, null));
        }
    }

    /** Stages a command with a receipt that completes during tick processing or queue cancellation. */
    public CompletableFuture<CommandOutcome> submitTracked(GameCommand command) {
        if (command == null) throw new IllegalArgumentException("A command is required");
        CompletableFuture<CommandOutcome> completion = new CompletableFuture<>();
        queue.add(new Submission(command, completion));
        return completion;
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
        List<Submission> drained = new ArrayList<>();
        Submission cmd;
        while ((cmd = queue.poll()) != null) {
            drained.add(cmd);
        }

        List<CommandOutcome> outcomes = new ArrayList<>();
        for (Submission submission : drained) {
            GameCommand command = submission.command();
            try {
                if (command.validate(state)) {
                    logger.debug("Executing command: {}", command);
                    state = command.apply(state);
                    outcomes.add(CommandOutcome.EXECUTED);
                } else {
                    logger.warn("Rejected invalid command: {}", command);
                    outcomes.add(CommandOutcome.REJECTED);
                }
            } catch (RuntimeException exception) {
                for (Submission aborted : drained)
                    if (aborted.completion() != null) aborted.completion().completeExceptionally(exception);
                throw exception;
            }
        }

        for (int index = 0; index < drained.size(); index++) complete(drained.get(index), outcomes.get(index));

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
        Submission submission;
        while ((submission = queue.poll()) != null) complete(submission, CommandOutcome.CANCELLED);
    }

    private static void complete(Submission submission, CommandOutcome outcome) {
        if (submission.completion() != null) submission.completion().complete(outcome);
    }
}
