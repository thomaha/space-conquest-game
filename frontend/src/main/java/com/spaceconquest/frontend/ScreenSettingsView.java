package com.spaceconquest.frontend;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Interactive UI panel for configuring screen resolution and display options.
 */
public class ScreenSettingsView {

    private static final Map<String, int[]> RESOLUTION_PRESETS = new LinkedHashMap<>();

    static {
        RESOLUTION_PRESETS.put("1280x720 (HD 16:9)", new int[]{1280, 720});
        RESOLUTION_PRESETS.put("1366x768 (WXGA 16:9)", new int[]{1366, 768});
        RESOLUTION_PRESETS.put("1600x900 (HD+ 16:9)", new int[]{1600, 900});
        RESOLUTION_PRESETS.put("1920x1080 (Full HD 16:9)", new int[]{1920, 1080});
        RESOLUTION_PRESETS.put("1920x1200 (WUXGA 16:10)", new int[]{1920, 1200});
        RESOLUTION_PRESETS.put("2560x1440 (2K QHD 16:9)", new int[]{2560, 1440});
        RESOLUTION_PRESETS.put("2560x1600 (WQXGA 16:10)", new int[]{2560, 1600});
        RESOLUTION_PRESETS.put("3840x2160 (4K UHD 16:9)", new int[]{3840, 2160});
    }

    private static final String CUSTOM_PRESET = "Custom";

    private VBox root;
    private final Menubar menubar;
    private final ScreenSettingsManager settingsManager;

    private ComboBox<String> resolutionComboBox;
    private Spinner<Integer> widthSpinner;
    private Spinner<Integer> heightSpinner;
    private CheckBox fullscreenCheckBox;
    private Label scaleLabel;
    private Label feedbackLabel;
    private boolean internalUpdate = false;

    public ScreenSettingsView(Menubar menubar, ScreenSettingsManager settingsManager) {
        this.menubar = menubar;
        this.settingsManager = settingsManager != null ? settingsManager : ScreenSettingsManager.getInstance();
        build();
    }

    private void build() {
        root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: rgba(12, 18, 38, 0.97); " +
                "-fx-border-color: #2980b9; -fx-border-width: 2; " +
                "-fx-border-radius: 10; -fx-background-radius: 10;");
        root.setPrefSize(600, 480);

        Text title = new Text("Screen and display settings");
        title.setFill(Color.WHITE);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 20));

        Button closeButton = new Button("Close");
        closeButton.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        closeButton.setCursor(javafx.scene.Cursor.HAND);
        closeButton.setOnAction(e -> hide());

        HBox header = new HBox(title);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(spacer, closeButton);

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(14);
        grid.setPadding(new Insets(10));

        ScreenSettings initialSettings = settingsManager.getSettings();

        // 1. Preset Resolution Selector
        Label presetLabel = new Label("Preset resolution:");
        presetLabel.setTextFill(Color.LIGHTGREEN);
        resolutionComboBox = new ComboBox<>();
        resolutionComboBox.setPrefWidth(260);
        resolutionComboBox.setStyle("-fx-cursor: hand;");
        resolutionComboBox.setCursor(javafx.scene.Cursor.HAND);
        resolutionComboBox.getItems().addAll(RESOLUTION_PRESETS.keySet());
        resolutionComboBox.getItems().add(CUSTOM_PRESET);

        grid.add(presetLabel, 0, 0);
        grid.add(resolutionComboBox, 1, 0);

        // 2. Custom Width Spinner
        Label widthLabel = new Label("Screen width (px):");
        widthLabel.setTextFill(Color.LIGHTCYAN);
        widthSpinner = new Spinner<>();
        widthSpinner.setPrefWidth(260);
        widthSpinner.setEditable(true);
        SpinnerValueFactory.IntegerSpinnerValueFactory widthFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(640, 7680, initialSettings.getWidth(), 10);
        widthSpinner.setValueFactory(widthFactory);

        grid.add(widthLabel, 0, 1);
        grid.add(widthSpinner, 1, 1);

        // 3. Custom Height Spinner
        Label heightLabel = new Label("Screen height (px):");
        heightLabel.setTextFill(Color.LIGHTCYAN);
        heightSpinner = new Spinner<>();
        heightSpinner.setPrefWidth(260);
        heightSpinner.setEditable(true);
        SpinnerValueFactory.IntegerSpinnerValueFactory heightFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(480, 4320, initialSettings.getHeight(), 10);
        heightSpinner.setValueFactory(heightFactory);

        grid.add(heightLabel, 0, 2);
        grid.add(heightSpinner, 1, 2);

        // 4. Fullscreen Mode
        Label fullscreenLabel = new Label("Display mode:");
        fullscreenLabel.setTextFill(Color.LIGHTSALMON);
        fullscreenCheckBox = new CheckBox("Launch in fullscreen mode");
        fullscreenCheckBox.setStyle("-fx-cursor: hand;");
        fullscreenCheckBox.setCursor(javafx.scene.Cursor.HAND);
        fullscreenCheckBox.setTextFill(Color.WHITE);
        fullscreenCheckBox.setSelected(initialSettings.isFullscreen());

        grid.add(fullscreenLabel, 0, 3);
        grid.add(fullscreenCheckBox, 1, 3);

        // 5. UI Scale Indicator
        Label scaleIndicatorLabel = new Label("UI text scaling:");
        scaleIndicatorLabel.setTextFill(Color.LIGHTYELLOW);
        scaleLabel = new Label();
        scaleLabel.setTextFill(Color.LIGHTYELLOW);
        scaleLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

        grid.add(scaleIndicatorLabel, 0, 4);
        grid.add(scaleLabel, 1, 4);

        // Synchronize Preset ComboBox with Spinners
        resolutionComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (internalUpdate || newVal == null) {
                return;
            }
            if (RESOLUTION_PRESETS.containsKey(newVal)) {
                int[] dims = RESOLUTION_PRESETS.get(newVal);
                internalUpdate = true;
                widthSpinner.getValueFactory().setValue(dims[0]);
                heightSpinner.getValueFactory().setValue(dims[1]);
                internalUpdate = false;
            }
        });

        widthSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!internalUpdate && newVal != null) {
                syncPresetComboBox(newVal, heightSpinner.getValue());
                updateScaleLabel();
            }
        });

        heightSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!internalUpdate && newVal != null) {
                syncPresetComboBox(widthSpinner.getValue(), newVal);
                updateScaleLabel();
            }
        });

        syncPresetComboBox(initialSettings.getWidth(), initialSettings.getHeight());
        updateScaleLabel();

        // Info and feedback
        VBox statusBox = new VBox(6);
        Label infoLabel = new Label("Selected screen resolution is persisted and applied on game startup.");
        infoLabel.setTextFill(Color.LIGHTGRAY);
        infoLabel.setFont(Font.font("Verdana", 11));

        feedbackLabel = new Label("");
        feedbackLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 12));
        statusBox.getChildren().addAll(infoLabel, feedbackLabel);

        // Action buttons
        HBox actionsRow = new HBox(12);
        actionsRow.setAlignment(Pos.CENTER_LEFT);

        Button saveBtn = new Button("Apply and save");
        saveBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; -fx-cursor: hand;");
        saveBtn.setCursor(javafx.scene.Cursor.HAND);
        saveBtn.setOnAction(e -> applyAndSave());

        Button resetBtn = new Button("Reset to default");
        resetBtn.setStyle("-fx-background-color: #7f8c8d; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; -fx-cursor: hand;");
        resetBtn.setCursor(javafx.scene.Cursor.HAND);
        resetBtn.setOnAction(e -> resetToDefault());

        actionsRow.getChildren().addAll(saveBtn, resetBtn);

        // Footer
        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_RIGHT);
        Button backToMenuBtn = new Button("Back to game menu");
        backToMenuBtn.setStyle("-fx-background-color: #34495e; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        backToMenuBtn.setCursor(javafx.scene.Cursor.HAND);
        backToMenuBtn.setOnAction(e -> {
            hide();
            if (menubar != null && menubar.getGameMenuView() != null) {
                menubar.getGameMenuView().show();
            }
        });
        footer.getChildren().add(backToMenuBtn);

        root.getChildren().addAll(header, grid, statusBox, actionsRow, footer);
        root.setVisible(false);
    }

    private void syncPresetComboBox(int width, int height) {
        internalUpdate = true;
        String matchedPreset = CUSTOM_PRESET;
        for (Map.Entry<String, int[]> entry : RESOLUTION_PRESETS.entrySet()) {
            int[] dims = entry.getValue();
            if (dims[0] == width && dims[1] == height) {
                matchedPreset = entry.getKey();
                break;
            }
        }
        resolutionComboBox.setValue(matchedPreset);
        internalUpdate = false;
    }

    private void updateScaleLabel() {
        if (scaleLabel == null || widthSpinner == null || heightSpinner == null) {
            return;
        }
        int w = widthSpinner.getValue() != null ? widthSpinner.getValue() : ScreenSettings.DEFAULT_WIDTH;
        int h = heightSpinner.getValue() != null ? heightSpinner.getValue() : ScreenSettings.DEFAULT_HEIGHT;
        double scale = ScreenSettings.calculateUiScale(w, h);
        if (scale <= 1.0) {
            scaleLabel.setText("1.00x (standard readable scale)");
        } else {
            scaleLabel.setText(String.format("%.2fx (texts and dialogues scaled for readability)", scale));
        }
    }

    public void applyAndSave() {
        int width = widthSpinner.getValue();
        int height = heightSpinner.getValue();
        boolean fullscreen = fullscreenCheckBox.isSelected();

        ScreenSettings newSettings = new ScreenSettings(width, height, fullscreen);
        boolean saved = settingsManager.trySaveSettings(newSettings);

        if (saved) {
            feedbackLabel.setTextFill(Color.LIGHTGREEN);
            feedbackLabel.setText(String.format("Saved resolution %dx%d%s (UI scale: %.2fx) successfully. Restart required.",
                    width, height, fullscreen ? " (fullscreen)" : "", newSettings.getUiScale()));
        } else {
            feedbackLabel.setTextFill(Color.LIGHTCORAL);
            feedbackLabel.setText("Failed to save screen settings.");
        }
    }

    public void resetToDefault() {
        ScreenSettings defaults = ScreenSettings.defaultSettings();
        internalUpdate = true;
        widthSpinner.getValueFactory().setValue(defaults.getWidth());
        heightSpinner.getValueFactory().setValue(defaults.getHeight());
        fullscreenCheckBox.setSelected(defaults.isFullscreen());
        internalUpdate = false;
        syncPresetComboBox(defaults.getWidth(), defaults.getHeight());
        updateScaleLabel();
        feedbackLabel.setTextFill(Color.LIGHTSKYBLUE);
        feedbackLabel.setText("Reset to default resolution: " + defaults.getResolutionString());
    }

    public void updateFromSettings(ScreenSettings settings) {
        if (settings == null) {
            return;
        }
        internalUpdate = true;
        widthSpinner.getValueFactory().setValue(settings.getWidth());
        heightSpinner.getValueFactory().setValue(settings.getHeight());
        fullscreenCheckBox.setSelected(settings.isFullscreen());
        internalUpdate = false;
        syncPresetComboBox(settings.getWidth(), settings.getHeight());
        updateScaleLabel();
    }

    public ScreenSettings getCurrentSelection() {
        return new ScreenSettings(
                widthSpinner != null ? widthSpinner.getValue() : ScreenSettings.DEFAULT_WIDTH,
                heightSpinner != null ? heightSpinner.getValue() : ScreenSettings.DEFAULT_HEIGHT,
                fullscreenCheckBox != null && fullscreenCheckBox.isSelected()
        );
    }

    public VBox getRoot() {
        return root;
    }

    public void show() {
        updateFromSettings(settingsManager.getSettings());
        root.setVisible(true);
        root.toFront();
        if (menubar != null) {
            menubar.openPage();
        }
    }

    public void hide() {
        root.setVisible(false);
        if (menubar != null) {
            menubar.closePage();
        }
    }
}
