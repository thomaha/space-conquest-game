package com.spaceconquest.frontend;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Data model for screen resolution and display settings.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScreenSettings {

    public static final int DEFAULT_WIDTH = 1920;
    public static final int DEFAULT_HEIGHT = 1080;
    public static final boolean DEFAULT_FULLSCREEN = false;

    public static final int BASE_REFERENCE_WIDTH = 1920;
    public static final int BASE_REFERENCE_HEIGHT = 1080;

    private int width;
    private int height;
    private boolean fullscreen;

    public ScreenSettings() {
        this(DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_FULLSCREEN);
    }

    public ScreenSettings(@JsonProperty("width") int width,
                          @JsonProperty("height") int height,
                          @JsonProperty("fullscreen") boolean fullscreen) {
        this.width = width > 0 ? width : DEFAULT_WIDTH;
        this.height = height > 0 ? height : DEFAULT_HEIGHT;
        this.fullscreen = fullscreen;
    }

    public static ScreenSettings defaultSettings() {
        return new ScreenSettings(DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_FULLSCREEN);
    }

    /**
     * Calculates the proportional UI scale factor based on screen dimensions.
     * At lower or standard resolutions (<= 1080p), the scale remains 1.0.
     * At higher resolutions (e.g., 1440p, 4K), UI elements scale up proportionally
     * so text and dialogs remain readable.
     */
    public static double calculateUiScale(int width, int height) {
        if (width <= 0 || height <= 0) {
            return 1.0;
        }
        double scaleByWidth = (double) width / BASE_REFERENCE_WIDTH;
        double scaleByHeight = (double) height / BASE_REFERENCE_HEIGHT;
        double scale = Math.min(scaleByWidth, scaleByHeight);
        return Math.max(1.0, scale);
    }

    /**
     * Returns the UI scaling factor for this screen configuration.
     */
    public double getUiScale() {
        return calculateUiScale(width, height);
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width > 0 ? width : DEFAULT_WIDTH;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height > 0 ? height : DEFAULT_HEIGHT;
    }

    public boolean isFullscreen() {
        return fullscreen;
    }

    public void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
    }

    public String getResolutionString() {
        return width + "x" + height;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScreenSettings that = (ScreenSettings) o;
        return width == that.width && height == that.height && fullscreen == that.fullscreen;
    }

    @Override
    public int hashCode() {
        return Objects.hash(width, height, fullscreen);
    }

    @Override
    public String toString() {
        return "ScreenSettings{" +
                "width=" + width +
                ", height=" + height +
                ", fullscreen=" + fullscreen +
                '}';
    }
}
