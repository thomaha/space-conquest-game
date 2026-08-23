package com.spaceconquest.engine;

/**
 * Manages simulation game clock, real-time speed scaling and turn progression.
 */
public class GameClock {

    public enum ClockSpeed {
        PAUSED(0.0, "Paused"),
        SPEED_1_MIN(1.0 / 60.0, "1 min/sec"),
        SPEED_1_HOUR(1.0, "1 hour/sec"),
        SPEED_1_DAY(24.0, "1 day/sec"),
        SPEED_5_DAYS(120.0, "5 days/sec");

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

    private ClockSpeed currentSpeed;
    private long currentTurn;
    private double accumulatedSeconds;
    private double hoursElapsed;

    public GameClock() {
        this(ClockSpeed.SPEED_1_DAY, 0, 0.0);
    }

    public GameClock(ClockSpeed initialSpeed, long initialTurn, double initialHours) {
        this.currentSpeed = (initialSpeed != null) ? initialSpeed : ClockSpeed.SPEED_1_DAY;
        this.currentTurn = initialTurn;
        this.accumulatedSeconds = 0.0;
        this.hoursElapsed = initialHours;
    }

    public void setSpeed(ClockSpeed speed) {
        if (speed != null) {
            this.currentSpeed = speed;
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
            currentSpeed = ClockSpeed.SPEED_1_DAY;
        } else {
            currentSpeed = ClockSpeed.PAUSED;
        }
    }

    /**
     * Updates clock by delta real-time seconds. Returns number of full turns advanced.
     */
    public int update(double deltaSeconds) {
        if (currentSpeed == ClockSpeed.PAUSED || deltaSeconds <= 0.0) {
            return 0;
        }

        double simulatedHours = deltaSeconds * currentSpeed.getRateMultiplier();
        hoursElapsed += simulatedHours;

        // 24 hours per turn
        int turnsAdvanced = (int) (hoursElapsed / 24.0);
        if (turnsAdvanced > 0) {
            currentTurn += turnsAdvanced;
            hoursElapsed = hoursElapsed % 24.0;
        }

        return turnsAdvanced;
    }

    public void stepTurn() {
        currentTurn++;
        hoursElapsed = 0.0;
    }

    public long getCurrentTurn() {
        return currentTurn;
    }

    public double getHoursElapsed() {
        return hoursElapsed;
    }

    public String getFormattedGameTime() {
        int day = (int) (hoursElapsed / 24.0) + 1;
        int hour = (int) (hoursElapsed % 24.0);
        return String.format("Turn %d | Day %d, %02d:00 | %s", currentTurn, day, hour, currentSpeed.getLabel());
    }
}
