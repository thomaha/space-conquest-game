package com.spaceconquest.engine;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Owns simulated time. A turn is one game day; display time may advance between turns.
 */
public class GameClock {

    public enum ClockSpeed {
        PAUSED(0.0, "Paused"),
        SPEED_1_MIN(1.0 / 60.0, "1 min/sec"),
        SPEED_1_HOUR(1.0, "1 hour/sec"),
        SPEED_6_HOURS(6.0, "6 hours/sec"),
        SPEED_12_HOURS(12.0, "12 hours/sec"),
        SPEED_1_DAY(24.0, "1 day/sec"),
        SPEED_5_DAYS(120.0, "5 days/sec"),
        SPEED_10_DAYS(240.0, "10 days/sec");

        private final double rateMultiplier;
        private final String label;

        ClockSpeed(double rateMultiplier, String label) {
            this.rateMultiplier = rateMultiplier;
            this.label = label;
        }

        public double getRateMultiplier() {
            return rateMultiplier;
        }

        public String getLabel() {
            return label;
        }
    }

    public static final LocalDateTime START_TIME = LocalDateTime.of(2027, 1, 1, 8, 0);

    private LocalDateTime campaignStartTime = START_TIME;
    private ClockSpeed currentSpeed;
    private ClockSpeed resumeSpeed;
    private long currentTurn;
    private double hoursElapsed;

    public GameClock() {
        this(ClockSpeed.SPEED_1_DAY, 0, 0.0);
    }

    public GameClock(ClockSpeed initialSpeed, long initialTurn, double initialHours) {
        this.currentSpeed = (initialSpeed != null) ? initialSpeed : ClockSpeed.SPEED_1_DAY;
        this.resumeSpeed = this.currentSpeed == ClockSpeed.PAUSED ? ClockSpeed.SPEED_1_DAY : this.currentSpeed;
        if (initialTurn < 0 || initialHours < 0 || initialHours >= 24 || !Double.isFinite(initialHours)) {
            throw new IllegalArgumentException("Invalid game time");
        }
        this.currentTurn = initialTurn;
        this.hoursElapsed = initialHours;
    }

    public void setSpeed(ClockSpeed speed) {
        if (speed != null) {
            this.currentSpeed = speed;
            if (speed != ClockSpeed.PAUSED) this.resumeSpeed = speed;
        }
    }

    public ClockSpeed getSpeed() {
        return currentSpeed;
    }

    public boolean isPaused() {
        return currentSpeed == ClockSpeed.PAUSED;
    }

    public void togglePause() {
        if (currentSpeed == ClockSpeed.PAUSED) {
            currentSpeed = resumeSpeed;
        } else {
            resumeSpeed = currentSpeed;
            currentSpeed = ClockSpeed.PAUSED;
        }
    }

    /**
     * Advances display time and returns the number of daily simulation turns due.
     * The caller must process each returned turn before publishing a world snapshot.
     */
    public int update(double deltaSeconds) {
        if (currentSpeed == ClockSpeed.PAUSED || deltaSeconds <= 0.0 || !Double.isFinite(deltaSeconds)) {
            return 0;
        }

        double simulatedHours = deltaSeconds * currentSpeed.getRateMultiplier();
        hoursElapsed += simulatedHours;

        // 24 hours per turn
        int turnsAdvanced = Math.toIntExact((long) Math.floor((hoursElapsed + 1e-9) / 24.0));
        if (turnsAdvanced > 0) {
            currentTurn += turnsAdvanced;
            hoursElapsed = Math.max(0.0, hoursElapsed - turnsAdvanced * 24.0);
        }

        return turnsAdvanced;
    }

    public void stepTurn() {
        currentTurn++;
        hoursElapsed = 0.0;
    }

    public void alignTurn(long turn) {
        if (turn < 0) throw new IllegalArgumentException("Turn cannot be negative");
        if (currentTurn != turn) {
            currentTurn = turn;
            hoursElapsed = 0.0;
        }
    }

    public void startNewCampaign(LocalDateTime startTime) {
        if (startTime == null) throw new IllegalArgumentException("Campaign start time is required");
        campaignStartTime = startTime;
        currentTurn = 0;
        hoursElapsed = 0.0;
    }

    public LocalDateTime getCampaignStartTime() {
        return campaignStartTime;
    }

    public void restore(LocalDateTime time, ClockSpeed speed) {
        if (time == null || time.isBefore(campaignStartTime)) {
            throw new IllegalArgumentException("Game time precedes campaign start");
        }
        Duration elapsed = Duration.between(campaignStartTime, time);
        currentTurn = elapsed.toDays();
        hoursElapsed = (elapsed.minusDays(currentTurn).toMillis() / 3_600_000.0);
        setSpeed(speed);
    }

    public LocalDateTime getGameTime() {
        return campaignStartTime.plusDays(currentTurn).plusSeconds(Math.round(hoursElapsed * 3_600.0));
    }

    public boolean beginsNewYearAtTurn(long turn) {
        return turn > 0 && campaignStartTime.plusDays(turn).getYear()
                != campaignStartTime.plusDays(turn - 1).getYear();
    }

    public boolean beginsElectionYearAtTurn(long turn) {
        return beginsNewYearAtTurn(turn)
                && (campaignStartTime.plusDays(turn).getYear() - campaignStartTime.getYear()) % 5 == 0;
    }

    public static boolean beginsNewYear(long turn) {
        return turn > 0 && START_TIME.plusDays(turn).getYear() != START_TIME.plusDays(turn - 1).getYear();
    }

    public static boolean beginsElectionYear(long turn) {
        return beginsNewYear(turn)
                && (START_TIME.plusDays(turn).getYear() - START_TIME.getYear()) % 5 == 0;
    }

    public long getCurrentTurn() {
        return currentTurn;
    }

    public double getHoursElapsed() {
        return hoursElapsed;
    }

    public String getFormattedGameTime() {
        return String.format("Turn %d | %s | %s", currentTurn, getGameTime(), currentSpeed.getLabel());
    }
}
