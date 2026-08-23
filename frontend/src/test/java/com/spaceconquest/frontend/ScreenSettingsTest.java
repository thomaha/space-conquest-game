package com.spaceconquest.frontend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ScreenSettingsTest {

    @Test
    public void testScreenSettingsDefaults() {
        ScreenSettings settings = new ScreenSettings();
        assertEquals(1920, settings.getWidth());
        assertEquals(1080, settings.getHeight());
        assertFalse(settings.isFullscreen());
        assertEquals("1920x1080", settings.getResolutionString());

        ScreenSettings staticDefaults = ScreenSettings.defaultSettings();
        assertEquals(settings, staticDefaults);
        assertEquals(settings.hashCode(), staticDefaults.hashCode());
    }

    @Test
    public void testScreenSettingsCustomValuesAndValidation() {
        ScreenSettings settings = new ScreenSettings(2560, 1440, true);
        assertEquals(2560, settings.getWidth());
        assertEquals(1440, settings.getHeight());
        assertTrue(settings.isFullscreen());
        assertEquals("2560x1440", settings.getResolutionString());

        // Test non-positive width/height fallback to default
        ScreenSettings invalidSettings = new ScreenSettings(-100, 0, false);
        assertEquals(ScreenSettings.DEFAULT_WIDTH, invalidSettings.getWidth());
        assertEquals(ScreenSettings.DEFAULT_HEIGHT, invalidSettings.getHeight());

        settings.setWidth(-50);
        assertEquals(ScreenSettings.DEFAULT_WIDTH, settings.getWidth());

        settings.setHeight(-50);
        assertEquals(ScreenSettings.DEFAULT_HEIGHT, settings.getHeight());

        settings.setWidth(1280);
        settings.setHeight(720);
        settings.setFullscreen(false);
        assertEquals(1280, settings.getWidth());
        assertEquals(720, settings.getHeight());
        assertFalse(settings.isFullscreen());
    }

    @Test
    public void testScreenSettingsJsonSerialization() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ScreenSettings original = new ScreenSettings(1600, 900, true);

        String json = mapper.writeValueAsString(original);
        assertTrue(json.contains("\"width\":1600") || json.contains("\"width\" : 1600"));
        assertTrue(json.contains("\"height\":900") || json.contains("\"height\" : 900"));
        assertTrue(json.contains("\"fullscreen\":true") || json.contains("\"fullscreen\" : true"));

        ScreenSettings deserialized = mapper.readValue(json, ScreenSettings.class);
        assertEquals(original, deserialized);
    }

    @Test
    public void testScreenSettingsManagerPersistence(@TempDir Path tempDir) throws IOException {
        Path testConfigFile = tempDir.resolve("screen_settings.json");
        ScreenSettingsManager manager = new ScreenSettingsManager(testConfigFile);

        // Initially no file exists, should return default settings
        ScreenSettings initial = manager.getSettings();
        assertEquals(1920, initial.getWidth());
        assertEquals(1080, initial.getHeight());
        assertFalse(initial.isFullscreen());

        // Save customized settings
        ScreenSettings custom = new ScreenSettings(3840, 2160, true);
        manager.saveSettings(custom);

        assertTrue(Files.exists(testConfigFile));

        // Create a new manager pointing to the same file to verify persistence
        ScreenSettingsManager reloadedManager = new ScreenSettingsManager(testConfigFile);
        ScreenSettings loaded = reloadedManager.getSettings();

        assertEquals(3840, loaded.getWidth());
        assertEquals(2160, loaded.getHeight());
        assertTrue(loaded.isFullscreen());
        assertEquals(custom, loaded);
    }

    @Test
    public void testScreenSettingsManagerTrySaveAndCorruptFile(@TempDir Path tempDir) throws IOException {
        Path testConfigFile = tempDir.resolve("nested/dir/screen_settings.json");
        ScreenSettingsManager manager = new ScreenSettingsManager(testConfigFile);

        ScreenSettings settings = new ScreenSettings(1366, 768, false);
        boolean saved = manager.trySaveSettings(settings);
        assertTrue(saved);
        assertTrue(Files.exists(testConfigFile));

        // Write corrupt content to test error recovery
        Files.writeString(testConfigFile, "{ corrupt json ... invalid content }");

        ScreenSettingsManager corruptRecoveryManager = new ScreenSettingsManager(testConfigFile);
        ScreenSettings recovered = corruptRecoveryManager.loadSettings();

        assertNotNull(recovered);
        assertEquals(ScreenSettings.DEFAULT_WIDTH, recovered.getWidth());
        assertEquals(ScreenSettings.DEFAULT_HEIGHT, recovered.getHeight());
    }

    @Test
    public void testScreenSettingsManagerNullHandling(@TempDir Path tempDir) {
        Path testConfigFile = tempDir.resolve("screen_settings.json");
        ScreenSettingsManager manager = new ScreenSettingsManager(testConfigFile);

        assertThrows(IllegalArgumentException.class, () -> manager.saveSettings(null));
        assertFalse(manager.trySaveSettings(null));
    }

    @Test
    public void testUiScaleCalculation() {
        // Lower and standard resolutions remain at 1.0 baseline scale
        assertEquals(1.0, ScreenSettings.calculateUiScale(1280, 720), 0.001);
        assertEquals(1.0, ScreenSettings.calculateUiScale(1366, 768), 0.001);
        assertEquals(1.0, ScreenSettings.calculateUiScale(1600, 900), 0.001);
        assertEquals(1.0, ScreenSettings.calculateUiScale(1920, 1080), 0.001);
        assertEquals(1.0, ScreenSettings.calculateUiScale(1920, 1200), 0.001);

        // High resolutions scale up proportionally
        assertEquals(1.3333, ScreenSettings.calculateUiScale(2560, 1440), 0.001);
        assertEquals(1.3333, ScreenSettings.calculateUiScale(2560, 1600), 0.001);
        assertEquals(2.0, ScreenSettings.calculateUiScale(3840, 2160), 0.001);
        assertEquals(4.0, ScreenSettings.calculateUiScale(7680, 4320), 0.001);

        // Ultrawide scaling
        assertEquals(1.3333, ScreenSettings.calculateUiScale(3440, 1440), 0.001);
        assertEquals(2.0, ScreenSettings.calculateUiScale(5120, 2160), 0.001);

        // Invalid dimensions fallback to 1.0
        assertEquals(1.0, ScreenSettings.calculateUiScale(0, 0), 0.001);
        assertEquals(1.0, ScreenSettings.calculateUiScale(-1920, 1080), 0.001);

        // Instance method delegates correctly
        ScreenSettings standard = new ScreenSettings(1920, 1080, false);
        assertEquals(1.0, standard.getUiScale(), 0.001);

        ScreenSettings uhd4k = new ScreenSettings(3840, 2160, true);
        assertEquals(2.0, uhd4k.getUiScale(), 0.001);
    }

    @Test
    public void testMenubarScreenSettingsViewGetter() {
        Menubar menubar = new Menubar();
        assertNull(menubar.getScreenSettingsView());
    }
}
