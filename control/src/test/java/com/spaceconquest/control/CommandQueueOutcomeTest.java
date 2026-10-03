package com.spaceconquest.control;

import com.spaceconquest.control.command.CommandOutcome;
import com.spaceconquest.control.command.CommandQueue;
import com.spaceconquest.control.command.GameCommand;
import com.spaceconquest.engine.GameState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommandQueueOutcomeTest {
    private GameCommand advanceAt(long turn) {
        return new GameCommand() {
            @Override public boolean validate(GameState state) { return state.turn() == turn; }
            @Override public GameState apply(GameState state) { return state.toBuilder().turn(turn + 1).build(); }
        };
    }

    @Test
    void receiptsReflectSequentialLiveValidationAndWaitForProcessing() {
        var queue = new CommandQueue();
        var first = queue.submitTracked(advanceAt(0));
        var second = queue.submitTracked(advanceAt(1));
        var rejected = queue.submitTracked(advanceAt(0));
        assertFalse(first.isDone());
        assertNull(queue.drainAndExecute(null));
        assertFalse(first.isDone());
        assertEquals(3, queue.size());
        var updated = queue.drainAndExecute(GameState.builder().build());
        assertEquals(2, updated.turn());
        assertEquals(CommandOutcome.EXECUTED, first.join());
        assertEquals(CommandOutcome.EXECUTED, second.join());
        assertEquals(CommandOutcome.REJECTED, rejected.join());
        assertTrue(queue.isEmpty());
    }

    @Test
    void clearingCancelsPendingReceiptsAndPreservesCompletedOnes() {
        var queue = new CommandQueue();
        var completed = queue.submitTracked(advanceAt(0));
        queue.drainAndExecute(GameState.builder().build());
        var cancelled = queue.submitTracked(advanceAt(1));
        queue.submit(advanceAt(1));
        queue.clear();
        assertTrue(queue.isEmpty());
        assertEquals(CommandOutcome.CANCELLED, cancelled.join());
        assertEquals(CommandOutcome.EXECUTED, completed.join());
        assertSame(CommandOutcome.CANCELLED,
                new HumanController(null).stageTrackedCommand(advanceAt(0)).join());
    }

    @Test
    void abortedBatchDoesNotReportUnpublishedChangesAsExecuted() {
        var queue = new CommandQueue();
        var first = queue.submitTracked(advanceAt(0));
        var failure = new IllegalStateException("Simulation failure");
        var broken = queue.submitTracked(new GameCommand() {
            @Override public boolean validate(GameState state) { return true; }
            @Override public GameState apply(GameState state) { throw failure; }
        });
        var remaining = queue.submitTracked(advanceAt(1));
        var state = GameState.builder().build();
        assertSame(failure, assertThrows(IllegalStateException.class, () -> queue.drainAndExecute(state)));
        assertEquals(0, state.turn());
        assertTrue(first.isCompletedExceptionally());
        assertTrue(broken.isCompletedExceptionally());
        assertTrue(remaining.isCompletedExceptionally());
        assertTrue(queue.isEmpty());
    }

    @Test
    void commandsStagedDuringProcessingWaitForTheNextDrain() {
        var queue = new CommandQueue();
        queue.submit(new GameCommand() {
            @Override public boolean validate(GameState state) { return true; }
            @Override public GameState apply(GameState state) {
                queue.submit(advanceAt(0));
                return state;
            }
        });
        var initial = GameState.builder().build();
        assertSame(initial, queue.drainAndExecute(initial));
        assertEquals(1, queue.size());
        assertEquals(1, queue.drainAndExecute(initial).turn());
    }
}
