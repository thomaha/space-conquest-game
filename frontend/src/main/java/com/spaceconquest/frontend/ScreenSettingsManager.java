package com.spaceconquest.frontend;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Manages reading, writing and applying persisted screen configuration settings.
 */
public class ScreenSettingsManager {

    private static final Logger logger = LogManager.getLogger(ScreenSettingsManager.class);
    private static final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private static final Path DEFAULT_SETTINGS_PATH = Paths.get("config", "screen_settings.json");
    private static ScreenSettingsManager instance;

    private final Path settingsPath;
    private ScreenSettings currentSettings;

    public ScreenSettingsManager() {
        this(DEFAULT_SETTINGS_PATH);
    }

    public ScreenSettingsManager(Path settingsPath) {
        this.settingsPath = settingsPath != null ? settingsPath : DEFAULT_SETTINGS_PATH;
        this.currentSettings = loadSettings();
    }

    public static synchronized ScreenSettingsManager getInstance() {
        if (instance == null) {
            instance = new ScreenSettingsManager();
        }
        return instance;
    }

    public static synchronized void setInstance(ScreenSettingsManager customInstance) {
        instance = customInstance;
    }

    public Path getSettingsPath() {
        return settingsPath;
    }

    public ScreenSettings getSettings() {
        if (currentSettings == null) {
            currentSettings = loadSettings();
        }
        return currentSettings;
    }

    public double getUiScale() {
        return getSettings().getUiScale();
    }

    public ScreenSettings loadSettings() {
        File file = settingsPath.toFile();
        if (file.exists() && file.isFile()) {
            try {
                ScreenSettings loaded = mapper.readValue(file, ScreenSettings.class);
                if (loaded != null && loaded.getWidth() > 0 && loaded.getHeight() > 0) {
                    this.currentSettings = loaded;
                    return loaded;
                }
            } catch (IOException e) {
                logger.error("Failed to read screen settings from " + settingsPath + ", using defaults", e);
            }
        }
        ScreenSettings defaults = ScreenSettings.defaultSettings();
        this.currentSettings = defaults;
        return defaults;
    }

    public void saveSettings(ScreenSettings settings) throws IOException {
        if (settings == null) {
            throw new IllegalArgumentException("Screen settings cannot be null");
        }
        File file = settingsPath.toFile();
        if (file.getParentFile() != null) {
            Files.createDirectories(file.getParentFile().toPath());
        }
        mapper.writeValue(file, settings);
        this.currentSettings = settings;
        logger.info("Saved screen settings to " + settingsPath + ": " + settings);
    }

    public boolean trySaveSettings(ScreenSettings settings) {
        try {
            saveSettings(settings);
            return true;
        } catch (Exception e) {
            logger.error("Failed to save screen settings", e);
            return false;
        }
    }
}
