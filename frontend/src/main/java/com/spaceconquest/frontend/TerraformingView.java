package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.StartTerraformingProjectCommand;
import com.spaceconquest.engine.terraforming.AtmosphericComposition;
import com.spaceconquest.engine.terraforming.GeoengineeringProject;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Interactive UI panel for planetary atmospheric engineering, greenhouse control, geoengineering dispatch and terraforming projects.
 */
public class TerraformingView {

    private VBox root;
    private VBox content;
    private Label feedbackLabel;
    private final Menubar menubar;
    private HumanController humanController;
    private String playerEmpireId = "terran_confederation";
    private final List<AtmosphericComposition> atmospheres = new ArrayList<>();
    private final List<GeoengineeringProject> projects = new ArrayList<>();

    public TerraformingView(Menubar menubar) {
        this.menubar = menubar;
        build();
    }

    public void setHumanController(HumanController controller) {
        this.humanController = controller;
    }

    public void setPlayerEmpireId(String empireId) {
        if (empireId != null && !empireId.isEmpty()) {
            this.playerEmpireId = empireId;
        }
    }

    private void build() {
        root = new VBox(15);
        content = new VBox(12);
        ScrollPane scrollPane = new ScrollPane(content);

        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: rgba(12, 25, 40, 0.96); " +
                "-fx-border-color: #27ae60; -fx-border-width: 2; " +
                "-fx-border-radius: 10; -fx-background-radius: 10;");
        root.setPrefSize(920, 700);

        Text title = new Text("Planetary terraforming and atmospheric geoengineering");
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

        feedbackLabel = new Label("Ready | Commission geoengineering and biological seeding projects.");
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

    public void updateData(List<AtmosphericComposition> newAtmos, List<GeoengineeringProject> newProjects) {
        atmospheres.clear();
        if (newAtmos != null) atmospheres.addAll(newAtmos);

        projects.clear();
        if (newProjects != null) projects.addAll(newProjects);

        if (root.isVisible()) {
            renderContent();
        }
    }

    private void renderContent() {
        content.getChildren().clear();

        // 1. Commission Geoengineering Project Workbench
        content.getChildren().add(createTerraformingWorkbench());

        // 2. Planetary Atmospheres Section
        content.getChildren().add(createAtmospheresSection());

        // 3. Active Geoengineering Projects Section
        content.getChildren().add(createProjectsSection());
    }

    private VBox createTerraformingWorkbench() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(12));
        section.setStyle("-fx-background-color: rgba(20, 45, 35, 0.75); -fx-background-radius: 8; -fx-border-color: #27ae60; -fx-border-width: 1; -fx-border-radius: 8;");

        Text title = new Text("Commission planetary geoengineering project");
        title.setFill(Color.LIGHTGREEN);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 15));

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);

        Label planetLbl = new Label("Target world:");
        planetLbl.setTextFill(Color.LIGHTCYAN);
        ComboBox<String> planetCombo = new ComboBox<>();
        planetCombo.getItems().addAll("mars", "venus", "ceres", "titan", "luna");
        planetCombo.setValue("mars");

        Label typeLbl = new Label("Geoengineering type:");
        typeLbl.setTextFill(Color.LIGHTCYAN);
        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll(
                GeoengineeringProject.TYPE_SOLAR_MIRROR,
                GeoengineeringProject.TYPE_GREENHOUSE_FACTORY,
                GeoengineeringProject.TYPE_CYANOBACTERIA_SEEDING,
                GeoengineeringProject.TYPE_CARBON_SEQUESTRATION,
                GeoengineeringProject.TYPE_MAGNETIC_FIELD_GENERATOR
        );
        typeCombo.setValue(GeoengineeringProject.TYPE_GREENHOUSE_FACTORY);

        Label pressLbl = new Label("Target pressure (atm):");
        pressLbl.setTextFill(Color.LIGHTCYAN);
        Spinner<Double> pressSpinner = new Spinner<>(0.1, 5.0, 1.0, 0.1);
        pressSpinner.setPrefWidth(80);

        Label tempLbl = new Label("Target temperature (K):");
        tempLbl.setTextFill(Color.LIGHTCYAN);
        Spinner<Double> tempSpinner = new Spinner<>(150.0, 400.0, 288.0, 5.0);
        tempSpinner.setPrefWidth(90);

        Button startBtn = new Button("Launch geoengineering project");
        startBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold;");
        startBtn.setOnAction(e -> {
            if (humanController != null) {
                humanController.stageCommand(new StartTerraformingProjectCommand(
                        playerEmpireId, planetCombo.getValue(), typeCombo.getValue(),
                        pressSpinner.getValue(), tempSpinner.getValue(),
                        Map.of("oxygen_gas", 0.21, "nitrogen_gas", 0.78, "carbon_dioxide", 0.01)
                ));
                feedbackLabel.setText("Dispatched " + typeCombo.getValue() + " project on " + planetCombo.getValue().toUpperCase());
                feedbackLabel.setTextFill(Color.LIGHTGREEN);
            }
        });

        grid.add(planetLbl, 0, 0);
        grid.add(planetCombo, 1, 0);
        grid.add(typeLbl, 2, 0);
        grid.add(typeCombo, 3, 0);

        grid.add(pressLbl, 0, 1);
        grid.add(pressSpinner, 1, 1);
        grid.add(tempLbl, 2, 1);
        grid.add(tempSpinner, 3, 1);

        HBox actions = new HBox(12, startBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        section.getChildren().addAll(title, grid, actions);
        return section;
    }

    private VBox createAtmospheresSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: rgba(20, 45, 35, 0.7); -fx-background-radius: 8; -fx-border-color: #2ecc71; -fx-border-width: 1; -fx-border-radius: 8;");

        Text header = new Text("Planetary atmospheres and biome habitation status");
        header.setFill(Color.LIGHTGREEN);
        header.setFont(Font.font("Verdana", FontWeight.BOLD, 15));
        section.getChildren().add(header);

        if (atmospheres.isEmpty()) {
            Text empty = new Text("No celestial bodies currently registered for atmospheric monitoring.");
            empty.setFill(Color.LIGHTGRAY);
            section.getChildren().add(empty);
        } else {
            for (AtmosphericComposition atmo : atmospheres) {
                VBox card = new VBox(6);
                card.setPadding(new Insets(8));
                card.setStyle("-fx-background-color: rgba(10, 30, 25, 0.6); -fx-background-radius: 6;");

                Text pTitle = new Text(String.format("Planet: %s | Biome: %s | Breathable: %s",
                        atmo.planetId().toUpperCase(), atmo.biomeType(), atmo.isBreathable() ? "YES (Habitable)" : "NO (Hostile)"));
                pTitle.setFill(atmo.isBreathable() ? Color.LIGHTGREEN : Color.SALMON);
                pTitle.setFont(Font.font("Verdana", FontWeight.BOLD, 13));

                Text env = new Text(String.format("Pressure: %.2f atm | Temp: %.1f K | Greenhouse: %.2f | Radiation: %.1f rad",
                        atmo.surfacePressureAtm(), atmo.surfaceTemperatureK(), atmo.greenhouseFactor(), atmo.radiationLevelRad()));
                env.setFill(Color.WHITE);
                env.setFont(Font.font("Verdana", 11));

                StringBuilder gases = new StringBuilder("Gases: ");
                for (Map.Entry<String, Double> g : atmo.gasRatios().entrySet()) {
                    gases.append(String.format("%s: %.1f%%  ", g.getKey(), g.getValue() * 100.0));
                }
                Text gasText = new Text(gases.toString());
                gasText.setFill(Color.LIGHTGRAY);
                gasText.setFont(Font.font("Verdana", 10));

                card.getChildren().addAll(pTitle, env, gasText);
                section.getChildren().add(card);
            }
        }

        return section;
    }

    private VBox createProjectsSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: rgba(20, 45, 35, 0.7); -fx-background-radius: 8; -fx-border-color: #f39c12; -fx-border-width: 1; -fx-border-radius: 8;");

        Text header = new Text("Active planetary geoengineering and seeding projects");
        header.setFill(Color.ORANGE);
        header.setFont(Font.font("Verdana", FontWeight.BOLD, 15));
        section.getChildren().add(header);

        if (projects.isEmpty()) {
            Text empty = new Text("No geoengineering projects active. Use start terraforming commands to deploy solar mirrors, greenhouse gas factories or cyanobacteria cultures.");
            empty.setFill(Color.LIGHTGRAY);
            section.getChildren().add(empty);
        } else {
            for (GeoengineeringProject proj : projects) {
                VBox card = new VBox(4);
                card.setPadding(new Insets(6));
                card.setStyle("-fx-background-color: rgba(10, 30, 25, 0.6); -fx-background-radius: 6;");

                Text pInfo = new Text(String.format("• [%s] %s on %s (Sponsor: %s) | Progress: %.0f/%.0f turns | Status: %s",
                        proj.projectType(), proj.id(), proj.planetId().toUpperCase(), proj.ownerEmpireId(),
                        proj.accumulatedProgress(), proj.requiredProgress(), proj.isCompleted() ? "COMPLETED" : "IN PROGRESS"));
                pInfo.setFill(Color.LIGHTCYAN);
                pInfo.setFont(Font.font("Verdana", 11));

                card.getChildren().add(pInfo);
                section.getChildren().add(card);
            }
        }

        return section;
    }
}
