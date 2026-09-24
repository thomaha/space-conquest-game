package com.spaceconquest.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

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
        assertEquals(GameClock.ClockSpeed.SPEED_1_DAY, clock.getSpeed());
    }

    @Test
    void testStepTurnAndFormatting() {
        clock.stepTurn();
        assertEquals(1, clock.getCurrentTurn());
        String formatted = clock.getFormattedGameTime();
        assertNotNull(formatted);
        assertTrue(formatted.contains("Turn 1"));
    }

    @Test
    void partialDaysDoNotRunDailyTurns() {
        clock.setSpeed(GameClock.ClockSpeed.SPEED_6_HOURS);
        for (int i = 0; i < 3; i++) assertEquals(0, clock.update(1.0));
        assertEquals(LocalDateTime.of(2200, 1, 2, 2, 0), clock.getGameTime());
        assertEquals(1, clock.update(1.0));
        assertEquals(1, clock.getCurrentTurn());
        assertEquals(LocalDateTime.of(2200, 1, 2, 8, 0), clock.getGameTime());
    }

    @Test
    void minuteSpeedAccumulatesExactlyOneDayAndRestoreKeepsProgress() {
        clock.setSpeed(GameClock.ClockSpeed.SPEED_1_MIN);
        for (int i = 0; i < 1439; i++) assertEquals(0, clock.update(1.0));
        assertEquals(1, clock.update(1.0));

        clock.restore(LocalDateTime.of(2200, 1, 3, 20, 0), GameClock.ClockSpeed.SPEED_12_HOURS);
        assertEquals(2, clock.getCurrentTurn());
        assertEquals(12.0, clock.getHoursElapsed(), 1e-9);
        assertEquals(1, clock.update(1.0));
        assertEquals(LocalDateTime.of(2200, 1, 4, 8, 0), clock.getGameTime());
    }

    @Test
    void speedChangeAndPausePreservePartialDayProgress() {
        clock.setSpeed(GameClock.ClockSpeed.SPEED_6_HOURS);
        assertEquals(0, clock.update(1.0));
        clock.togglePause();
        assertEquals(0, clock.update(10.0));
        clock.togglePause();
        assertEquals(GameClock.ClockSpeed.SPEED_6_HOURS, clock.getSpeed());
        clock.setSpeed(GameClock.ClockSpeed.SPEED_12_HOURS);
        assertEquals(0, clock.update(1.0));
        assertEquals(1, clock.update(1.0));
        assertEquals(6.0, clock.getHoursElapsed(), 1e-9);
    }

    @Test
    void annualCadenceUsesTheCalendarIncludingLeapYears() {
        assertFalse(GameClock.beginsNewYear(364));
        assertTrue(GameClock.beginsNewYear(365));
        assertTrue(GameClock.beginsNewYear(365 + 365));
        assertFalse(GameClock.beginsNewYear(1825));
        assertTrue(GameClock.beginsNewYear(1826));
        assertFalse(GameClock.beginsElectionYear(365));
        assertTrue(GameClock.beginsElectionYear(1826));
    }
}
