package com.spaceconquest.frontend;

import com.spaceconquest.engine.GameClock;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Schedules real-time pulses and displays the authoritative engine time.
 */
public class MenubarClockController {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final GameClock.ClockSpeed[] SPEEDS = {
            GameClock.ClockSpeed.SPEED_1_MIN,
            GameClock.ClockSpeed.SPEED_1_HOUR,
            GameClock.ClockSpeed.SPEED_6_HOURS,
            GameClock.ClockSpeed.SPEED_12_HOURS,
            GameClock.ClockSpeed.SPEED_1_DAY,
            GameClock.ClockSpeed.SPEED_5_DAYS,
            GameClock.ClockSpeed.SPEED_10_DAYS
    };

    private final Timeline clock = new Timeline();
    private final Label clockLabel = new Label();
    private final Label speedLabel = new Label();

    private LocalDateTime displayedTime = GameClock.START_TIME;
    private int speedIndex = 1;
    private int savedSpeedIndex = 1;
    private boolean paused;
    private boolean manuallyPaused;
    private Consumer<Double> pulseAction;
    private Consumer<GameClock.ClockSpeed> speedChangeAction;

    public void init(Consumer<Double> onPulse, Consumer<GameClock.ClockSpeed> onSpeedChange) {
        this.pulseAction = onPulse;
        this.speedChangeAction = onSpeedChange;
        updateClockLabels();
        restartClock();
    }

    public Label getClockLabel() {
        return clockLabel;
    }

    public Label getSpeedLabel() {
        return speedLabel;
    }

    public int getSpeedIndex() {
        return speedIndex;
    }

    public GameClock.ClockSpeed getSelectedSpeed() {
        return paused ? GameClock.ClockSpeed.PAUSED : SPEEDS[speedIndex];
    }

    public static GameClock.ClockSpeed speedForIndex(int index) {
        return SPEEDS[Math.max(0, Math.min(SPEEDS.length - 1, index))];
    }

    public boolean isPaused() {
        return paused;
    }

    public void restoreTime(LocalDateTime time, int speed) {
        if (time != null) {
            displayedTime = time;
        }
        speedIndex = Math.max(0, Math.min(SPEEDS.length - 1, speed));
        savedSpeedIndex = speedIndex;
        updateClockLabels();
        restartClock();
    }

    public void changeSpeed(int change) {
        speedIndex = Math.max(0, Math.min(SPEEDS.length - 1, speedIndex + change));
        updateClockLabels();
        restartClock();
    }

    public void togglePaused(Button pauseButton) {
        paused = !paused;
        manuallyPaused = paused;
        if (pauseButton != null) {
            pauseButton.setText(paused ? "▶" : "Ⅱ");
        }
        restartClock();
    }

    public void openPage() {
        if (!paused) {
            savedSpeedIndex = speedIndex;
            paused = true;
            restartClock();
            updateClockLabels();
        }
    }

    public void closePage() {
        if (!manuallyPaused) {
            paused = false;
            speedIndex = savedSpeedIndex;
            restartClock();
            updateClockLabels();
        }
    }

    public void restartClock() {
        clock.stop();
        if (speedChangeAction != null) {
            speedChangeAction.accept(getSelectedSpeed());
        }
        if (!paused) {
            clock.getKeyFrames().setAll(new KeyFrame(Duration.seconds(1), e -> {
                if (pulseAction != null) {
                    pulseAction.accept(1.0);
                }
            }));
            clock.setCycleCount(Timeline.INDEFINITE);
            clock.play();
        }
    }

    public void updateClockLabels() {
        clockLabel.setText(displayedTime.format(TIME_FORMAT));
        clockLabel.setTextFill(Color.WHITE);
        clockLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        speedLabel.setText(paused ? "PAUSED" : SPEEDS[speedIndex].getLabel());
        speedLabel.setTextFill(paused ? Color.SALMON : Color.LIGHTGRAY);
        speedLabel.setStyle("-fx-font-size: 11px;");
    }

    public void stop() {
        clock.stop();
    }

    public void displayTime(LocalDateTime time) {
        displayedTime = time;
        updateClockLabels();
    }
}
