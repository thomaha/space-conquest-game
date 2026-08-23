package com.spaceconquest.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameClockTest {

    private GameClock clock;

    @BeforeEach
    void setUp() {
        clock = new GameClock(GameClock.ClockSpeed.SPEED_1_DAY, 0, 0.0);
    }

    @Test
    void testClockTurnAdvancement() {
        assertEquals(0, clock.getCurrentTurn());
        assertEquals(0.0, clock.getHoursElapsed());

        // At 1 day/sec (24 hours/sec), 1 real second advances 1 turn (24 hours)
        int turnsAdvanced = clock.update(1.0);
        assertEquals(1, turnsAdvanced);
        assertEquals(1, clock.getCurrentTurn());
    }

    @Test
    void testClockPauseAndResume() {
        assertFalse(clock.isPaused());

        clock.togglePause();
        assertTrue(clock.isPaused());

        // When paused, update advances 0 turns
        int turns = clock.update(5.0);
        assertEquals(0, turns);
        assertEquals(0, clock.getCurrentTurn());

        clock.togglePause();
        assertFalse(clock.isPaused());
    }

    @Test
    void testStepTurnAndFormatting() {
        clock.stepTurn();
        assertEquals(1, clock.getCurrentTurn());
        String formatted = clock.getFormattedGameTime();
        assertNotNull(formatted);
        assertTrue(formatted.contains("Turn 1"));
    }
}
