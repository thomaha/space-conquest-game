package com.spaceconquest.frontend;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Manages the in-game simulation clock, speed controls, pause states and HUD clock labels.
 */
public class MenubarClockController {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String[] SPEED_NAMES = {"1 min/s", "1 hour/s", "6 hours/s", "12 hours/s", "1 day/s"};
    private static final int[] MINUTES_PER_TICK = {1, 60, 360, 720, 1440};

    private final Timeline clock = new Timeline();
    private final Label clockLabel = new Label();
    private final Label speedLabel = new Label();

    private LocalDateTime gameTime = LocalDateTime.of(2200, 1, 1, 8, 0);
    private int speedIndex = 1;
    private int savedSpeedIndex = 1;
    private boolean paused;
    private boolean manuallyPaused;
    private Runnable tickAction;

    public void init(Runnable onTick) {
        this.tickAction = onTick;
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

    public LocalDateTime getGameTime() {
        return gameTime;
    }

    public boolean isPaused() {
        return paused;
    }

    public void restoreTime(LocalDateTime time, int speed) {
        if (time != null) {
            gameTime = time;
        }
        speedIndex = Math.max(0, Math.min(SPEED_NAMES.length - 1, speed));
        savedSpeedIndex = speedIndex;
        updateClockLabels();
        restartClock();
    }

    public void changeSpeed(int change) {
        speedIndex = Math.max(0, Math.min(SPEED_NAMES.length - 1, speedIndex + change));
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
        if (!paused) {
            clock.getKeyFrames().setAll(new KeyFrame(Duration.seconds(1), e -> {
                gameTime = gameTime.plusMinutes(MINUTES_PER_TICK[speedIndex]);
                updateClockLabels();
                if (tickAction != null) {
                    tickAction.run();
                }
            }));
            clock.setCycleCount(Timeline.INDEFINITE);
            clock.play();
        }
    }

    public void updateClockLabels() {
        clockLabel.setText(gameTime.format(TIME_FORMAT));
        clockLabel.setTextFill(Color.WHITE);
        clockLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        speedLabel.setText(paused ? "PAUSED" : SPEED_NAMES[speedIndex]);
        speedLabel.setTextFill(paused ? Color.SALMON : Color.LIGHTGRAY);
        speedLabel.setStyle("-fx-font-size: 11px;");
    }

    public void stop() {
        clock.stop();
    }
}
