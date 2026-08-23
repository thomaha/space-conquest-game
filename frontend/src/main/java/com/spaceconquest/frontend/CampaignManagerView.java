package com.spaceconquest.frontend;

import com.spaceconquest.engine.GameStartScenario;
import com.spaceconquest.engine.SaveGameManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.io.File;

/**
 * Interactive UI panel for campaign configuration, seed selection and save game management.
 */
public class CampaignManagerView {

    private VBox root;
    private VBox content;
    private Label feedbackLabel;
    private final Menubar menubar;
    private Main mainApp;

    public CampaignManagerView(Menubar menubar) {
        this.menubar = menubar;
        build();
    }

    public void setMainApp(Main mainApp) {
        this.mainApp = mainApp;
    }

    private void build() {
        root = new VBox(15);
        content = new VBox(12);
        ScrollPane scrollPane = new ScrollPane(content);

        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: rgba(15, 20, 38, 0.97); " +
                "-fx-border-color: #6c5ce7; -fx-border-width: 2; " +
                "-fx-border-radius: 10; -fx-background-radius: 10;");
        root.setPrefSize(840, 650);

        Text title = new Text("Galactic campaign and save game manager");
        title.setFill(Color.WHITE);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 22));

        Button closeButton = new Button("Close");
        closeButton.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-weight: bold;");
        closeButton.setOnAction(e -> hide());

        HBox header = new HBox(title);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(spacer, closeButton);

        feedbackLabel = new Label("Ready | Manage campaign saves and custom galaxy setups.");
        feedbackLabel.setTextFill(Color.LIGHTCYAN);
        feedbackLabel.setFont(Font.font("Verdana", 11));

        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setPadding(new Insets(10));

        root.getChildren().addAll(header, feedbackLabel, scrollPane);
        root.setVisible(false);
    }

    public VBox getRoot() {
        return root;
    }

    public void show() {
        renderContent();
        root.setVisible(true);
        root.toFront();
    }

    public void hide() {
        root.setVisible(false);
        if (menubar != null) {
            menubar.closePage();
        }
    }

    private void renderContent() {
        content.getChildren().clear();

        // 1. Campaign Setup Section
        VBox setupSection = new VBox(10);
        setupSection.setPadding(new Insets(12));
        setupSection.setStyle("-fx-background-color: rgba(30, 40, 75, 0.7); -fx-background-radius: 8; -fx-border-color: #6c5ce7; -fx-border-width: 1; -fx-border-radius: 8;");

        Text setupTitle = new Text("Initialize new custom campaign");
        setupTitle.setFill(Color.LIGHTBLUE);
        setupTitle.setFont(Font.font("Verdana", FontWeight.BOLD, 15));

        Spinner<Integer> systemsSpinner = new Spinner<>(5, 100, 20);
        systemsSpinner.setEditable(true);
        systemsSpinner.setPrefWidth(120);

        ComboBox<GameStartScenario> scenarioCombo = new ComboBox<>();
        scenarioCombo.getItems().addAll(GameStartScenario.values());
        scenarioCombo.setValue(GameStartScenario.PRE_SPACE_FLIGHT);

        HBox systemsRow = new HBox(15, new Label("Solar systems:"), systemsSpinner, new Label("Scenario:"), scenarioCombo);
        systemsRow.setAlignment(Pos.CENTER_LEFT);

        Button startBtn = new Button("Quick launch new campaign");
        startBtn.setStyle("-fx-background-color: #00b894; -fx-text-fill: white; -fx-font-weight: bold;");
        startBtn.setOnAction(e -> {
            if (mainApp != null) {
                mainApp.createNewGalaxy(systemsSpinner.getValue(), scenarioCombo.getValue());
                feedbackLabel.setText("Initialized new galaxy with " + systemsSpinner.getValue() + " solar systems!");
                feedbackLabel.setTextFill(Color.LIGHTGREEN);
                hide();
            }
        });

        Button customizeScenarioBtn = new Button("Customize scenario and empire →");
        customizeScenarioBtn.setStyle("-fx-background-color: #8e44ad; -fx-text-fill: white; -fx-font-weight: bold;");
        customizeScenarioBtn.setOnAction(e -> {
            hide();
            if (menubar != null) {
                menubar.startNewGameSetup();
            }
        });

        HBox buttonRow = new HBox(12, startBtn, customizeScenarioBtn);
        setupSection.getChildren().addAll(setupTitle, systemsRow, buttonRow);
        content.getChildren().add(setupSection);

        // 2. Save Current Active Campaign Section
        VBox saveCurrentSection = new VBox(10);
        saveCurrentSection.setPadding(new Insets(12));
        saveCurrentSection.setStyle("-fx-background-color: rgba(30, 40, 75, 0.7); -fx-background-radius: 8; -fx-border-color: #2ecc71; -fx-border-width: 1; -fx-border-radius: 8;");

        Text saveCurrentTitle = new Text("Save active campaign");
        saveCurrentTitle.setFill(Color.LIGHTGREEN);
        saveCurrentTitle.setFont(Font.font("Verdana", FontWeight.BOLD, 15));

        TextField saveNameField = new TextField("imperial_campaign_01");
        saveNameField.setPrefWidth(200);

        Button saveBtn = new Button("Save campaign");
        saveBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold;");
        saveBtn.setOnAction(e -> {
            if (mainApp != null) {
                String saveName = saveNameField.getText().trim();
                if (!saveName.isEmpty()) {
                    mainApp.saveGame(saveName);
                    feedbackLabel.setText("Campaign saved successfully as: " + saveName);
                    feedbackLabel.setTextFill(Color.LIGHTGREEN);
                    renderContent();
                }
            }
        });

        Button quickSaveBtn = new Button("Quick save");
        quickSaveBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold;");
        quickSaveBtn.setOnAction(e -> {
            if (mainApp != null) {
                mainApp.quickSave();
                feedbackLabel.setText("Quick save written to quicksave.scsave");
                feedbackLabel.setTextFill(Color.LIGHTGREEN);
                renderContent();
            }
        });

        HBox saveActions = new HBox(12, new Label("Save name:"), saveNameField, saveBtn, quickSaveBtn);
        saveActions.setAlignment(Pos.CENTER_LEFT);

        saveCurrentSection.getChildren().addAll(saveCurrentTitle, saveActions);
        content.getChildren().add(saveCurrentSection);

        // 3. Saved Games Section
        VBox saveSection = new VBox(10);
        saveSection.setPadding(new Insets(12));
        saveSection.setStyle("-fx-background-color: rgba(30, 40, 75, 0.7); -fx-background-radius: 8; -fx-border-color: #0984e3; -fx-border-width: 1; -fx-border-radius: 8;");

        Text saveTitle = new Text("Available campaign saves");
        saveTitle.setFill(Color.LIGHTSKYBLUE);
        saveTitle.setFont(Font.font("Verdana", FontWeight.BOLD, 15));

        SaveGameManager saveMgr = new SaveGameManager();
        File saveDir = saveMgr.getSaveDirectory().toFile();
        File[] saveFiles = saveDir.listFiles((dir, name) -> name.endsWith(SaveGameManager.SAVE_EXTENSION) || name.endsWith(".json"));

        if (saveFiles == null || saveFiles.length == 0) {
            Text emptySaves = new Text("No existing save game files found in " + saveDir.getAbsolutePath());
            emptySaves.setFill(Color.LIGHTGRAY);
            saveSection.getChildren().addAll(saveTitle, emptySaves);
        } else {
            saveSection.getChildren().add(saveTitle);
            for (File file : saveFiles) {
                HBox fileRow = new HBox(15);
                fileRow.setAlignment(Pos.CENTER_LEFT);
                Text fn = new Text(file.getName() + " (" + (file.length() / 1024) + " KB)");
                fn.setFill(Color.WHITE);
                HBox.setHgrow(fn, Priority.ALWAYS);

                Button loadBtn = new Button("Load save");
                loadBtn.setStyle("-fx-background-color: #0984e3; -fx-text-fill: white; -fx-font-weight: bold;");
                loadBtn.setOnAction(e -> {
                    if (mainApp != null) {
                        mainApp.loadGame(file.getName().replace(SaveGameManager.SAVE_EXTENSION, "").replace(".json", ""));
                        feedbackLabel.setText("Loaded save: " + file.getName());
                        feedbackLabel.setTextFill(Color.LIGHTGREEN);
                        hide();
                    }
                });

                Button deleteBtn = new Button("Delete");
                deleteBtn.setStyle("-fx-background-color: #d63031; -fx-text-fill: white; -fx-font-size: 10px;");
                deleteBtn.setOnAction(e -> {
                    saveMgr.deleteSave(file.getName().replace(SaveGameManager.SAVE_EXTENSION, "").replace(".json", ""));
                    feedbackLabel.setText("Deleted save file: " + file.getName());
                    feedbackLabel.setTextFill(Color.ORANGE);
                    renderContent();
                });

                fileRow.getChildren().addAll(fn, loadBtn, deleteBtn);
                saveSection.getChildren().add(fileRow);
            }
        }

        content.getChildren().add(saveSection);
    }
}
