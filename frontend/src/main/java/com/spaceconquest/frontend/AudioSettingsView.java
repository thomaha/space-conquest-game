package com.spaceconquest.frontend;

import com.spaceconquest.engine.audio.AudioSynthesizer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/**
 * Interactive UI panel for configuring audio volume levels, sound effects and acoustic synthesizer options.
 */
public class AudioSettingsView {

    private VBox root;
    private final Menubar menubar;
    private final AudioSynthesizer audioSynthesizer;

    private Slider masterVolumeSlider;
    private Slider sfxVolumeSlider;
    private Slider musicVolumeSlider;
    private CheckBox muteCheckBox;

    public AudioSettingsView(Menubar menubar, AudioSynthesizer audioSynthesizer) {
        this.menubar = menubar;
        this.audioSynthesizer = audioSynthesizer != null ? audioSynthesizer : new AudioSynthesizer();
        build();
    }

    private void build() {
        root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: rgba(12, 18, 38, 0.97); " +
                "-fx-border-color: #2980b9; -fx-border-width: 2; " +
                "-fx-border-radius: 10; -fx-background-radius: 10;");
        root.setPrefSize(600, 480);

        Text title = new Text("Audio and acoustic settings");
        title.setFill(Color.WHITE);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 20));

        Button closeButton = new Button("Close");
        closeButton.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-weight: bold;");
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

        // 1. Master Volume
        Label masterLabel = new Label("Master volume:");
        masterLabel.setTextFill(Color.LIGHTGREEN);
        masterVolumeSlider = new Slider(0.0, 1.0, audioSynthesizer.getMasterVolume());
        masterVolumeSlider.setShowTickLabels(true);
        masterVolumeSlider.setShowTickMarks(true);
        masterVolumeSlider.setMajorTickUnit(0.2);
        masterVolumeSlider.valueProperty().addListener((obs, oldV, newV) -> audioSynthesizer.setMasterVolume(newV.doubleValue()));
        grid.add(masterLabel, 0, 0);
        grid.add(masterVolumeSlider, 1, 0);

        // 2. Sound Effects (SFX) Volume
        Label sfxLabel = new Label("Sound effects (SFX):");
        sfxLabel.setTextFill(Color.LIGHTCYAN);
        sfxVolumeSlider = new Slider(0.0, 1.0, audioSynthesizer.getSfxVolume());
        sfxVolumeSlider.setShowTickLabels(true);
        sfxVolumeSlider.setShowTickMarks(true);
        sfxVolumeSlider.setMajorTickUnit(0.2);
        sfxVolumeSlider.valueProperty().addListener((obs, oldV, newV) -> audioSynthesizer.setSfxVolume(newV.doubleValue()));
        grid.add(sfxLabel, 0, 1);
        grid.add(sfxVolumeSlider, 1, 1);

        // 3. Music & Ambient Volume
        Label musicLabel = new Label("Music and ambient:");
        musicLabel.setTextFill(Color.LIGHTCYAN);
        musicVolumeSlider = new Slider(0.0, 1.0, audioSynthesizer.getMusicVolume());
        musicVolumeSlider.setShowTickLabels(true);
        musicVolumeSlider.setShowTickMarks(true);
        musicVolumeSlider.setMajorTickUnit(0.2);
        musicVolumeSlider.valueProperty().addListener((obs, oldV, newV) -> audioSynthesizer.setMusicVolume(newV.doubleValue()));
        grid.add(musicLabel, 0, 2);
        grid.add(musicVolumeSlider, 1, 2);

        // 4. Mute Checkbox
        Label muteLabel = new Label("Mute audio output:");
        muteLabel.setTextFill(Color.LIGHTSALMON);
        muteCheckBox = new CheckBox("Mute all synthesized sound cues");
        muteCheckBox.setTextFill(Color.WHITE);
        muteCheckBox.setSelected(audioSynthesizer.isMuted());
        muteCheckBox.selectedProperty().addListener((obs, oldV, newV) -> {
            audioSynthesizer.setMuted(newV);
            if (menubar != null && menubar.getAudioPlaybackManager() != null) {
                menubar.getAudioPlaybackManager().setSoundEnabled(!newV);
            }
        });
        grid.add(muteLabel, 0, 3);
        grid.add(muteCheckBox, 1, 3);

        // 5. Test Audio Cues
        VBox testBox = new VBox(8);
        Label testLabel = new Label("Test synthesized cues:");
        testLabel.setTextFill(Color.LIGHTSKYBLUE);
        testLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

        HBox testButtonsRow1 = new HBox(10);
        Button btnClick = createCueButton("UI click", AudioSynthesizer.EVENT_UI_CLICK);
        Button btnLaser = createCueButton("Laser fire", AudioSynthesizer.EVENT_LASER_FIRE);
        Button btnKinetic = createCueButton("Kinetic fire", AudioSynthesizer.EVENT_KINETIC_FIRE);
        testButtonsRow1.getChildren().addAll(btnClick, btnLaser, btnKinetic);

        HBox testButtonsRow2 = new HBox(10);
        Button btnWarp = createCueButton("Warp transit", AudioSynthesizer.EVENT_WARP_TRANSIT);
        Button btnExplosion = createCueButton("Explosion", AudioSynthesizer.EVENT_EXPLOSION);
        Button btnFanfare = createCueButton("Victory fanfare", AudioSynthesizer.EVENT_VICTORY_FANFARE);
        testButtonsRow2.getChildren().addAll(btnWarp, btnExplosion, btnFanfare);

        testBox.getChildren().addAll(testLabel, testButtonsRow1, testButtonsRow2);

        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_RIGHT);
        Button backToMenuBtn = new Button("Back to game menu");
        backToMenuBtn.setStyle("-fx-background-color: #34495e; -fx-text-fill: white; -fx-font-weight: bold;");
        backToMenuBtn.setOnAction(e -> {
            hide();
            if (menubar != null && menubar.getGameMenuView() != null) {
                menubar.getGameMenuView().show();
            }
        });
        footer.getChildren().add(backToMenuBtn);

        root.getChildren().addAll(header, grid, testBox, footer);
        root.setVisible(false);
    }

    private Button createCueButton(String text, String cueEvent) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px;");
        btn.setOnAction(e -> audioSynthesizer.triggerCue(cueEvent));
        return btn;
    }

    public VBox getRoot() {
        return root;
    }

    public void show() {
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
