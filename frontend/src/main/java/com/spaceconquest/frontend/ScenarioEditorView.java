package com.spaceconquest.frontend;

import com.spaceconquest.engine.GameStartScenario;
import com.spaceconquest.engine.audio.AudioSynthesizer;
import com.spaceconquest.engine.scenario.CampaignSetup;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
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
 * Interactive UI panel for customizing campaign scenarios, starting eras, galaxy scale, AI distributions and victory conditions.
 * Serves as step 1 in the new game creation workflow.
 */
public class ScenarioEditorView {

    private VBox root;
    private final Menubar menubar;
    private final AudioSynthesizer audioSynthesizer;
    private CampaignSetup currentSetup = CampaignSetup.createDefault();
    private GameStartScenario selectedScenario = GameStartScenario.PRE_SPACE_FLIGHT;

    private Slider starCountSlider;
    private Slider nebulaSlider;
    private ComboBox<GameStartScenario> scenarioCombo;
    private Label scenarioDescriptionLabel;
    private ComboBox<String> aiCombo;
    private ComboBox<String> techTierCombo;
    private ComboBox<String> victoryCombo;

    public ScenarioEditorView(Menubar menubar, AudioSynthesizer audioSynthesizer) {
        this.menubar = menubar;
        this.audioSynthesizer = audioSynthesizer != null ? audioSynthesizer : new AudioSynthesizer();
        build();
    }

    private void build() {
        root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: rgba(15, 20, 35, 0.98); " +
                "-fx-border-color: #e67e22; -fx-border-width: 2; " +
                "-fx-border-radius: 10; -fx-background-radius: 10;");
        root.setPrefSize(820, 620);

        Text title = new Text("New campaign setup — scenario and galaxy configuration");
        title.setFill(Color.WHITE);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 19));

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
        grid.setVgap(12);
        grid.setPadding(new Insets(10));

        // 1. Starting Era / Scenario
        Label eraLabel = new Label("Starting era / scenario:");
        eraLabel.setTextFill(Color.LIGHTCYAN);
        scenarioCombo = new ComboBox<>();
        scenarioCombo.getItems().addAll(GameStartScenario.values());
        scenarioCombo.setValue(GameStartScenario.PRE_SPACE_FLIGHT);
        scenarioCombo.setPrefWidth(380);

        scenarioCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(GameStartScenario item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayName());
            }
        });
        scenarioCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(GameStartScenario item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayName());
            }
        });

        scenarioDescriptionLabel = new Label(scenarioCombo.getValue().description());
        scenarioDescriptionLabel.setTextFill(Color.LIGHTGRAY);
        scenarioDescriptionLabel.setWrapText(true);
        scenarioDescriptionLabel.setMaxWidth(380);
        scenarioDescriptionLabel.setFont(Font.font("Verdana", 11));

        scenarioCombo.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                selectedScenario = newV;
                scenarioDescriptionLabel.setText(newV.description());
            }
        });

        grid.add(eraLabel, 0, 0);
        grid.add(new VBox(4, scenarioCombo, scenarioDescriptionLabel), 1, 0);

        // 2. Star Count Slider
        Label starLabel = new Label("Galaxy star count (5 - 50):");
        starLabel.setTextFill(Color.LIGHTCYAN);
        starCountSlider = new Slider(5, 50, currentSetup.starSystemCount());
        starCountSlider.setShowTickLabels(true);
        starCountSlider.setShowTickMarks(true);
        starCountSlider.setMajorTickUnit(10);
        starCountSlider.setPrefWidth(380);
        grid.add(starLabel, 0, 1);
        grid.add(starCountSlider, 1, 1);

        // 3. Nebula Density
        Label nebLabel = new Label("Nebula density (0.0 - 1.0):");
        nebLabel.setTextFill(Color.LIGHTCYAN);
        nebulaSlider = new Slider(0.0, 1.0, currentSetup.nebulaDensity());
        nebulaSlider.setShowTickLabels(true);
        nebulaSlider.setShowTickMarks(true);
        nebulaSlider.setPrefWidth(380);
        grid.add(nebLabel, 0, 2);
        grid.add(nebulaSlider, 1, 2);

        // 4. AI Personality Distribution
        Label aiLabel = new Label("AI personality distribution:");
        aiLabel.setTextFill(Color.LIGHTCYAN);
        aiCombo = new ComboBox<>();
        aiCombo.getItems().addAll(CampaignSetup.AI_BALANCED, CampaignSetup.AI_AGGRESSIVE, CampaignSetup.AI_ISOLATIONIST, CampaignSetup.AI_MERCANTILE, CampaignSetup.AI_SCIENTIFIC);
        aiCombo.setValue(currentSetup.aiPersonalityDistribution());
        aiCombo.setPrefWidth(380);
        grid.add(aiLabel, 0, 3);
        grid.add(aiCombo, 1, 3);

        // 5. Starting Tech Tier
        Label techLabel = new Label("Starting tech tier (1 - 4):");
        techLabel.setTextFill(Color.LIGHTCYAN);
        techTierCombo = new ComboBox<>();
        techTierCombo.getItems().addAll("Tier 1: Basic sub-light", "Tier 2: Fusion drives", "Tier 3: Warp and metamaterials", "Tier 4: Nanotech and megastructures");
        techTierCombo.setValue("Tier 1: Basic sub-light");
        techTierCombo.setPrefWidth(380);
        grid.add(techLabel, 0, 4);
        grid.add(techTierCombo, 1, 4);

        // 6. Victory Condition
        Label vicLabel = new Label("Victory condition:");
        vicLabel.setTextFill(Color.LIGHTCYAN);
        victoryCombo = new ComboBox<>();
        victoryCombo.getItems().addAll(CampaignSetup.VICTORY_DOMINATION, CampaignSetup.VICTORY_ECONOMIC_MONOPOLY, CampaignSetup.VICTORY_MEGASTRUCTURE_ASCENSION, CampaignSetup.VICTORY_DIPLOMATIC_FEDERATION);
        victoryCombo.setValue(currentSetup.victoryConditionType());
        victoryCombo.setPrefWidth(380);
        grid.add(vicLabel, 0, 5);
        grid.add(victoryCombo, 1, 5);

        // Actions Footer
        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_RIGHT);

        Button backBtn = new Button("Back to game menu");
        backBtn.setStyle("-fx-background-color: #34495e; -fx-text-fill: white; -fx-font-weight: bold;");
        backBtn.setOnAction(e -> {
            hide();
            if (menubar != null && menubar.getGameMenuView() != null) {
                menubar.getGameMenuView().show();
            }
        });

        Button launchDefaultBtn = new Button("Quick start (Terran Confederation)");
        launchDefaultBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold;");
        launchDefaultBtn.setOnAction(e -> handleLaunchDefault());

        Button nextEmpireBtn = new Button("Next: custom empire and species architect →");
        nextEmpireBtn.setStyle("-fx-background-color: #8e44ad; -fx-text-fill: white; -fx-font-weight: bold;");
        nextEmpireBtn.setOnAction(e -> handleProceedToEmpireWizard());

        actions.getChildren().addAll(backBtn, launchDefaultBtn, nextEmpireBtn);

        root.getChildren().addAll(header, grid, actions);
        root.setVisible(false);
    }

    private void syncSetupFromControls() {
        int techTier = techTierCombo.getSelectionModel().getSelectedIndex() + 1;
        if (techTier <= 0) techTier = 1;
        selectedScenario = scenarioCombo.getValue() != null ? scenarioCombo.getValue() : GameStartScenario.PRE_SPACE_FLIGHT;
        currentSetup = new CampaignSetup(
                "Custom Campaign",
                (int) starCountSlider.getValue(),
                nebulaSlider.getValue(),
                aiCombo.getValue(),
                techTier,
                victoryCombo.getValue(),
                60
        );
    }

    private void handleLaunchDefault() {
        syncSetupFromControls();
        audioSynthesizer.triggerCue(AudioSynthesizer.EVENT_VICTORY_FANFARE);
        if (menubar != null) {
            if (menubar.getMainApp() != null) {
                menubar.getMainApp().createNewGalaxy(currentSetup.starSystemCount(), selectedScenario);
            }
            menubar.setPlayerEmpireId("terran_confederation");
        }
        hide();
    }

    private void handleProceedToEmpireWizard() {
        syncSetupFromControls();
        audioSynthesizer.triggerCue(AudioSynthesizer.EVENT_UI_CLICK);
        hide();
        if (menubar != null && menubar.getEmpireWizardView() != null) {
            menubar.getEmpireWizardView().show();
        }
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

    public CampaignSetup getCurrentSetup() {
        syncSetupFromControls();
        return currentSetup;
    }

    public GameStartScenario getSelectedScenario() {
        return selectedScenario;
    }
}
