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

        ScreenSettings initialSettings = settingsManager.getSettings();

        HBox header = buildHeader();
        GridPane grid = buildSettingsGrid(initialSettings);
        VBox statusBox = buildStatusBox();
        HBox actionsRow = buildActionsRow();
        HBox footer = buildFooter();

        attachListeners(initialSettings);

        root.getChildren().addAll(header, grid, statusBox, actionsRow, footer);
        root.setVisible(false);
    }

    private HBox buildHeader() {
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
        return header;
    }

    private GridPane buildSettingsGrid(ScreenSettings initialSettings) {
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(14);
        grid.setPadding(new Insets(10));

        Label presetLabel = new Label("Preset resolution:");
        presetLabel.setTextFill(Color.LIGHTGREEN);
        resolutionComboBox = new ComboBox<>();
        resolutionComboBox.setPrefWidth(260);
        resolutionComboBox.setCursor(javafx.scene.Cursor.HAND);
        resolutionComboBox.getItems().addAll(RESOLUTION_PRESETS.keySet());
        resolutionComboBox.getItems().add(CUSTOM_PRESET);

        Label widthLabel = new Label("Screen width (px):");
        widthLabel.setTextFill(Color.LIGHTCYAN);
        widthSpinner = new Spinner<>();
        widthSpinner.setPrefWidth(260);
        widthSpinner.setEditable(true);
        widthSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(640, 7680, initialSettings.getWidth(), 10));

        Label heightLabel = new Label("Screen height (px):");
        heightLabel.setTextFill(Color.LIGHTCYAN);
        heightSpinner = new Spinner<>();
        heightSpinner.setPrefWidth(260);
        heightSpinner.setEditable(true);
        heightSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(480, 4320, initialSettings.getHeight(), 10));

        Label fullscreenLabel = new Label("Display mode:");
        fullscreenLabel.setTextFill(Color.LIGHTSALMON);
        fullscreenCheckBox = new CheckBox("Launch in fullscreen mode");
        fullscreenCheckBox.setCursor(javafx.scene.Cursor.HAND);
        fullscreenCheckBox.setTextFill(Color.WHITE);
        fullscreenCheckBox.setSelected(initialSettings.isFullscreen());

        Label scaleIndicatorLabel = new Label("UI text scaling:");
        scaleIndicatorLabel.setTextFill(Color.LIGHTYELLOW);
        scaleLabel = new Label();
        scaleLabel.setTextFill(Color.LIGHTYELLOW);
        scaleLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

        grid.add(presetLabel, 0, 0);
        grid.add(resolutionComboBox, 1, 0);
        grid.add(widthLabel, 0, 1);
        grid.add(widthSpinner, 1, 1);
        grid.add(heightLabel, 0, 2);
        grid.add(heightSpinner, 1, 2);
        grid.add(fullscreenLabel, 0, 3);
        grid.add(fullscreenCheckBox, 1, 3);
        grid.add(scaleIndicatorLabel, 0, 4);
        grid.add(scaleLabel, 1, 4);

        return grid;
    }

    private void attachListeners(ScreenSettings initialSettings) {
        resolutionComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (internalUpdate || newVal == null) return;
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
    }

    private VBox buildStatusBox() {
        VBox statusBox = new VBox(6);
        Label infoLabel = new Label("Selected screen resolution is persisted and applied on game startup.");
        infoLabel.setTextFill(Color.LIGHTGRAY);
        infoLabel.setFont(Font.font("Verdana", 11));

        feedbackLabel = new Label("");
        feedbackLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 12));
        statusBox.getChildren().addAll(infoLabel, feedbackLabel);
        return statusBox;
    }

    private HBox buildActionsRow() {
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
        return actionsRow;
    }

    private HBox buildFooter() {
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
        return footer;
    }

    private void syncPresetComboBox(int width, int height) {
        for (Map.Entry<String, int[]> entry : RESOLUTION_PRESETS.entrySet()) {
            if (entry.getValue()[0] == width && entry.getValue()[1] == height) {
                internalUpdate = true;
                resolutionComboBox.setValue(entry.getKey());
                internalUpdate = false;
                return;
            }
        }
        internalUpdate = true;
        resolutionComboBox.setValue(CUSTOM_PRESET);
        internalUpdate = false;
    }

    private void updateScaleLabel() {
        if (widthSpinner != null && heightSpinner != null && scaleLabel != null) {
            int w = widthSpinner.getValue();
            int h = heightSpinner.getValue();
            double scale = ScreenSettings.calculateUiScale(w, h);
            scaleLabel.setText(String.format("%.2fx (%d%%)", scale, (int) Math.round(scale * 100)));
        }
    }

    public void applyAndSave() {
        int width = widthSpinner.getValue();
        int height = heightSpinner.getValue();
        boolean fullscreen = fullscreenCheckBox.isSelected();

        ScreenSettings newSettings = new ScreenSettings(width, height, fullscreen);
        boolean saved = settingsManager.trySaveSettings(newSettings);

        if (saved) {
            feedbackLabel.setText("Settings saved! Resolution " + width + "x" + height +
                    (fullscreen ? " (Fullscreen)" : "") + " will take effect on next game launch.");
            feedbackLabel.setTextFill(Color.LIGHTGREEN);
        } else {
            feedbackLabel.setText("Failed to save settings to disk.");
            feedbackLabel.setTextFill(Color.SALMON);
        }
    }

    public void resetToDefault() {
        ScreenSettings defaults = ScreenSettings.defaultSettings();
        internalUpdate = true;
        widthSpinner.getValueFactory().setValue(defaults.getWidth());
        heightSpinner.getValueFactory().setValue(defaults.getHeight());
        fullscreenCheckBox.setSelected(defaults.isFullscreen());
        syncPresetComboBox(defaults.getWidth(), defaults.getHeight());
        internalUpdate = false;
        updateScaleLabel();

        feedbackLabel.setText("Reset to default " + defaults.getWidth() + "x" + defaults.getHeight() + ".");
        feedbackLabel.setTextFill(Color.LIGHTBLUE);
    }

    public void show() {
        ScreenSettings current = settingsManager.getSettings();
        internalUpdate = true;
        widthSpinner.getValueFactory().setValue(current.getWidth());
        heightSpinner.getValueFactory().setValue(current.getHeight());
        fullscreenCheckBox.setSelected(current.isFullscreen());
        syncPresetComboBox(current.getWidth(), current.getHeight());
        internalUpdate = false;
        updateScaleLabel();

        feedbackLabel.setText("");
        root.setVisible(true);
        root.toFront();
    }

    public void hide() {
        root.setVisible(false);
        if (menubar != null) {
            menubar.closePage();
        }
    }

    public VBox getRoot() {
        return root;
    }

    public ComboBox<String> getResolutionComboBox() {
        return resolutionComboBox;
    }

    public Spinner<Integer> getWidthSpinner() {
        return widthSpinner;
    }

    public Spinner<Integer> getHeightSpinner() {
        return heightSpinner;
    }

    public CheckBox getFullscreenCheckBox() {
        return fullscreenCheckBox;
    }

    public Label getFeedbackLabel() {
        return feedbackLabel;
    }
}
