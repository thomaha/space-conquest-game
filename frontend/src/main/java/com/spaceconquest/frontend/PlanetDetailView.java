package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.BuildFacilityCommand;
import com.spaceconquest.control.command.PlaceFacilityOnTileCommand;
import com.spaceconquest.control.command.StartProspectingMissionCommand;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.frontend.components.SurfaceBiomeGridView;
import com.spaceconquest.engine.biome.PlanetBiomeGrid;
import com.spaceconquest.engine.biome.SurfaceTile;
import com.spaceconquest.engine.industry.GeologicalDeposit;
import com.spaceconquest.engine.industry.PowerGridState;
import com.spaceconquest.engine.megastructure.Megastructure;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Interactive UI panel displaying planetary geological prospecting data, power grid balances and surface commands.
 */
public class PlanetDetailView {
    private VBox root;
    private VBox content;
    private ScrollPane scrollPane;
    private Label feedbackLabel;
    private final Menubar menubar;
    private HumanController humanController;
    private String playerEmpireId = "terran_confederation";
    private PlanetaryBodyEntry selectedBody;
    private final List<GeologicalDeposit> deposits = new ArrayList<>();
    private final List<PowerGridState> powerGrids = new ArrayList<>();
    private final List<Megastructure> megastructures = new ArrayList<>();

    public PlanetDetailView(Menubar menubar) {
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

    public void setSelectedBody(PlanetaryBodyEntry body) {
        if (body != null && (this.selectedBody == null || !this.selectedBody.id().equals(body.id()))) {
            this.selectedBody = body;
            if (root != null && root.isVisible()) {
                renderContent();
            }
        }
    }

    public List<Megastructure> getMegastructures() {
        return megastructures;
    }

    private void build() {
        root = new VBox(15);
        content = new VBox(12);
        scrollPane = new ScrollPane(content);

        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: rgba(10, 18, 38, 0.96); " +
                "-fx-border-color: #2ed573; -fx-border-width: 2; " +
                "-fx-border-radius: 10; -fx-background-radius: 10;");
        root.setPrefSize(920, 700);

        Text title = new Text("Planetary geological survey and energy grid matrix");
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

        feedbackLabel = new Label("Ready | Conduct prospecting surveys and manage power infrastructure.");
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
        if (menubar != null && menubar.getMainApp() != null && menubar.getMainApp().getEngine() != null) {
            updateData(menubar.getMainApp().getEngine().getGameState());
        } else if (!root.isVisible()) {
            renderContent();
        }
        if (root != null) {
            root.setVisible(true);
            root.toFront();
        }
    }

    public void hide() {
        root.setVisible(false);
        if (menubar != null) {
            menubar.closePage();
        }
    }

    public void updateData(GameState state) {
        if (state == null) return;
        updateData(state.geologicalDeposits(), state.powerGrids(), state.megastructures());
    }

    public void updateData(List<GeologicalDeposit> newDeposits, List<PowerGridState> newGrids) {
        updateData(newDeposits, newGrids, null);
    }

    public void updateData(List<GeologicalDeposit> newDeposits, List<PowerGridState> newGrids, List<Megastructure> newMegastructures) {
        deposits.clear();
        if (newDeposits != null) deposits.addAll(newDeposits);

        powerGrids.clear();
        if (newGrids != null) powerGrids.addAll(newGrids);

        megastructures.clear();
        if (newMegastructures != null) megastructures.addAll(newMegastructures);

        renderContent();
    }

    private void renderContent() {
        content.getChildren().clear();

        // Use a GridPane to organize sections and avoid vertical stacking duplication/clutter
        GridPane mainGrid = new GridPane();
        mainGrid.setHgap(16);
        mainGrid.setVgap(16);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(50);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(50);
        mainGrid.getColumnConstraints().addAll(col1, col2);

        // 1. Planetary Quick Action Workbench
        mainGrid.add(createActionWorkbench(), 0, 0, 2, 1);

        // 2. Interactive Surface Biome Grid - Span both columns as requested
        VBox surfaceSection = createSurfaceBiomeGridSection();
        surfaceSection.setPrefHeight(450); // Larger height for detail view
        mainGrid.add(surfaceSection, 0, 1, 2, 1);

        // 3. Power Grids Section
        mainGrid.add(createPowerGridsSection(), 0, 2);

        // 4. Megastructures Section
        mainGrid.add(createMegastructuresSection(), 1, 2);

        // 5. Geological Mineral Veins Section
        mainGrid.add(createGeologicalDepositsSection(), 0, 3, 2, 1);

        content.getChildren().add(mainGrid);
    }

    private VBox createSurfaceBiomeGridSection() {
        if (selectedBody == null) {
            VBox placeholder = new VBox(new Label("No planetary body selected for surface scan."));
            placeholder.setPadding(new Insets(10));
            return placeholder;
        }

        SurfaceBiomeGridView gridView = new SurfaceBiomeGridView(selectedBody, humanController, playerEmpireId, (id, tileIdx) -> {
            if (humanController != null) {
                humanController.stageCommand(new PlaceFacilityOnTileCommand(
                        id, tileIdx, "solar_power_array", playerEmpireId, "PUBLIC_STATE", 50, "technician"
                ));
                feedbackLabel.setText("Commissioned facility on surface tile #" + tileIdx + " (" + selectedBody.name() + ")");
                feedbackLabel.setTextFill(Color.LIGHTGREEN);
            }
        });

        return gridView;
    }

    private VBox createActionWorkbench() {
        VBox section = new VBox(8);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: rgba(20, 45, 65, 0.75); -fx-background-radius: 8; -fx-border-color: #2ed573; -fx-border-width: 1; -fx-border-radius: 8;");

        Text title = new Text("Planetary development and prospecting actions");
        title.setFill(Color.LIGHTGREEN);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 14));

        HBox controls = new HBox(12);
        controls.setAlignment(Pos.CENTER_LEFT);

        ComboBox<String> planetCombo = new ComboBox<>();
        planetCombo.getItems().addAll("earth", "mars", "luna", "ceres", "titan", "venus");
        planetCombo.setValue("earth");

        Button surveyBtn = new Button("Launch geological survey");
        surveyBtn.setStyle("-fx-background-color: #2ed573; -fx-text-fill: black; -fx-font-weight: bold;");
        surveyBtn.setOnAction(e -> {
            if (humanController != null) {
                humanController.stageCommand(new StartProspectingMissionCommand(
                        playerEmpireId, planetCombo.getValue(), 5
                ));
                feedbackLabel.setText("Dispatched geological prospecting team to " + planetCombo.getValue().toUpperCase());
                feedbackLabel.setTextFill(Color.LIGHTGREEN);
            }
        });

        Button buildPowerBtn = new Button("Construct fusion power plant");
        buildPowerBtn.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-font-weight: bold;");
        buildPowerBtn.setOnAction(e -> {
            if (humanController != null) {
                humanController.stageCommand(new BuildFacilityCommand(
                        playerEmpireId, planetCombo.getValue(), "fusion_reactor", "technician", 1
                ));
                feedbackLabel.setText("Commissioned fusion power plant on " + planetCombo.getValue().toUpperCase());
                feedbackLabel.setTextFill(Color.LIGHTGREEN);
            }
        });

        controls.getChildren().addAll(new Label("Select planet:"), planetCombo, surveyBtn, buildPowerBtn);
        section.getChildren().addAll(title, controls);
        return section;
    }

    private VBox createPowerGridsSection() {
        VBox section = new VBox(8);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: rgba(20, 35, 60, 0.7); -fx-background-radius: 8; -fx-border-color: #ffa502; -fx-border-width: 1; -fx-border-radius: 8;");

        Text header = new Text("Connected planetary power grids");
        header.setFill(Color.GOLD);
        header.setFont(Font.font("Verdana", FontWeight.BOLD, 15));
        section.getChildren().add(header);

        if (powerGrids.isEmpty()) {
            Text empty = new Text("Standard baseline energy grid active (No critical power deficits).");
            empty.setFill(Color.LIGHTGRAY);
            section.getChildren().add(empty);
        } else {
            for (PowerGridState grid : powerGrids) {
                VBox card = new VBox(4);
                card.setPadding(new Insets(6));
                card.setStyle("-fx-background-color: rgba(15, 25, 45, 0.6); -fx-background-radius: 6;");

                Text gridInfo = new Text(String.format("Planet [%s] Generation: %.1f kW | Demand: %.1f kW | Net: %.1f kW | Battery: %.1f / %.1f kWh | %s",
                        grid.entityId().toUpperCase(), grid.totalGenerationKw(), grid.totalDemandKw(), grid.netBalanceKw(),
                        grid.currentStoredKwh(), grid.batteryCapacityKwh(),
                        grid.isDeficitBrownoutActive() ? "BROWNOUT ACTIVE" : "STABLE"));
                gridInfo.setFill(grid.isDeficitBrownoutActive() ? Color.RED : (grid.netBalanceKw() >= 0 ? Color.LIGHTGREEN : Color.ORANGE));
                gridInfo.setFont(Font.font("Verdana", 11));

                card.getChildren().add(gridInfo);
                section.getChildren().add(card);
            }
        }

        return section;
    }

    private VBox createMegastructuresSection() {
        VBox section = new VBox(8);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: rgba(20, 35, 60, 0.7); -fx-background-radius: 8; -fx-border-color: #f1c40f; -fx-border-width: 1; -fx-border-radius: 8;");

        Text header = new Text("Orbital megastructures and grand engineering (" + megastructures.size() + ")");
        header.setFill(Color.GOLD);
        header.setFont(Font.font("Verdana", FontWeight.BOLD, 15));
        section.getChildren().add(header);

        if (megastructures.isEmpty()) {
            Text empty = new Text("No orbital megastructures stationed or under construction at this celestial body.");
            empty.setFill(Color.LIGHTGRAY);
            section.getChildren().add(empty);
        } else {
            for (Megastructure mega : megastructures) {
                VBox card = new VBox(4);
                card.setPadding(new Insets(6));
                card.setStyle("-fx-background-color: rgba(15, 25, 45, 0.6); -fx-background-radius: 6; " +
                        "-fx-border-color: " + (mega.isOperational() ? "#2ecc71" : "#e67e22") + "; -fx-border-width: 1; -fx-border-radius: 6;");

                HBox cardHeader = new HBox(8);
                cardHeader.setAlignment(Pos.CENTER_LEFT);

                Text mName = new Text(mega.name() + " (" + mega.type() + ")");
                mName.setFill(Color.WHITE);
                mName.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

                Label statusBadge = new Label(mega.isOperational() ? "Operational" : "Under construction");
                statusBadge.setStyle("-fx-background-color: " + (mega.isOperational() ? "#27ae60" : "#d35400") + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 2 6 2 6; -fx-background-radius: 4; -fx-font-size: 9;");

                cardHeader.getChildren().addAll(mName, statusBadge);

                Text specText = new Text(String.format("Stage: %d / %d | Energy output: %,.0f kW | Habitable capacity: %,d%s",
                        mega.currentStage(), mega.totalStages(), mega.energyYieldKw(), mega.habitableCapacity(),
                        mega.isOperational() ? "" : String.format(" | Turns remaining: %.0f", Math.max(0, mega.requiredStageProgress() - mega.currentStageProgress()))));
                specText.setFill(Color.LIGHTCYAN);
                specText.setFont(Font.font("Verdana", 10));

                card.getChildren().addAll(cardHeader, specText);
                section.getChildren().add(card);
            }
        }

        return section;
    }

    private VBox createGeologicalDepositsSection() {
        VBox section = new VBox(8);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: rgba(20, 35, 60, 0.7); -fx-background-radius: 8; -fx-border-color: #2ed573; -fx-border-width: 1; -fx-border-radius: 8;");

        Text header = new Text("Subterranean geological mineral veins");
        header.setFill(Color.LIGHTGREEN);
        header.setFont(Font.font("Verdana", FontWeight.BOLD, 15));
        section.getChildren().add(header);

        if (deposits.isEmpty()) {
            Text empty = new Text("No geological veins currently mapped. Conduct prospecting surveys to uncover subterranean deposits.");
            empty.setFill(Color.LIGHTGRAY);
            section.getChildren().add(empty);
        } else {
            for (GeologicalDeposit dep : deposits) {
                VBox card = new VBox(4);
                card.setPadding(new Insets(6));
                card.setStyle("-fx-background-color: rgba(15, 25, 45, 0.6); -fx-background-radius: 6;");

                Text depInfo = new Text(String.format("• Deposit [%s] Planet: %s | Material: %s | Remaining: %.0f / %.0f kg (%.1f%%) | Purity: %.2fx | Status: %s | Owner: %s",
                        dep.id(), dep.planetId().toUpperCase(), dep.materialId(), dep.remainingVolumeKg(), dep.initialVolumeKg(),
                        dep.getDepletionPercentage(), dep.concentrationModifier(),
                        dep.isDiscovered() ? "DISCOVERED" : "HIDDEN", dep.ownerEntityId()));
                depInfo.setFill(dep.isDiscovered() ? Color.WHITE : Color.GRAY);
                depInfo.setFont(Font.font("Verdana", 11));

                card.getChildren().add(depInfo);
                section.getChildren().add(card);
            }
        }

        return section;
    }
}
