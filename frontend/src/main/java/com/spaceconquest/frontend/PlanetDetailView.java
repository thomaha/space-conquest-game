package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.BuildFacilityCommand;
import com.spaceconquest.control.command.PlaceFacilityOnTileCommand;
import com.spaceconquest.control.command.StartProspectingMissionCommand;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.biome.BiomeAdjacencyProcessor;
import com.spaceconquest.engine.biome.PlanetBiomeGrid;
import com.spaceconquest.engine.biome.SurfaceTile;
import com.spaceconquest.engine.industry.GeologicalDeposit;
import com.spaceconquest.engine.industry.PowerGridState;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
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
    private final List<GeologicalDeposit> deposits = new ArrayList<>();
    private final List<PowerGridState> powerGrids = new ArrayList<>();

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

    public void updateData(List<GeologicalDeposit> newDeposits, List<PowerGridState> newGrids) {
        deposits.clear();
        if (newDeposits != null) deposits.addAll(newDeposits);

        powerGrids.clear();
        if (newGrids != null) powerGrids.addAll(newGrids);

        if (root.isVisible()) {
            renderContent();
        }
    }

    private void renderContent() {
        content.getChildren().clear();

        // 1. Planetary Quick Action Workbench
        content.getChildren().add(createActionWorkbench());

        // 2. Interactive Surface Biome Grid
        content.getChildren().add(createSurfaceBiomeGridSection());

        // 3. Power Grids Section
        content.getChildren().add(createPowerGridsSection());

        // 4. Geological Mineral Veins Section
        content.getChildren().add(createGeologicalDepositsSection());
    }

    private VBox createSurfaceBiomeGridSection() {
        VBox section = new VBox(8);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: rgba(20, 35, 60, 0.7); -fx-background-radius: 8; -fx-border-color: #3498db; -fx-border-width: 1; -fx-border-radius: 8;");

        Text header = new Text("Planetary surface biome grid and facility adjacency matrix (4x4)");
        header.setFill(Color.AQUA);
        header.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        section.getChildren().add(header);

        BiomeAdjacencyProcessor proc = new BiomeAdjacencyProcessor();
        Planet demoPlanet = new Planet("earth", "Earth", "Terrestrial world", 5.97e24, 1.0, 1.0, 0, 12742, "TERRESTRIAL", "Oxygen-Nitrogen", true, 0.71, List.of(), List.of(), List.of());
        PlanetBiomeGrid grid = proc.generateDefaultGrid(demoPlanet, deposits);

        GridPane tileGrid = new GridPane();
        tileGrid.setHgap(8);
        tileGrid.setVgap(8);

        for (int r = 0; r < grid.rows(); r++) {
            for (int c = 0; c < grid.columns(); c++) {
                SurfaceTile tile = grid.getTile(r, c);
                if (tile == null) continue;

                VBox tileCard = new VBox(4);
                tileCard.setPadding(new Insets(6));
                tileCard.setPrefSize(190, 75);

                String colorStyle = switch (tile.biomeType()) {
                    case SurfaceTile.BIOME_EQUATORIAL_DESERT -> "-fx-background-color: rgba(180, 130, 40, 0.6); -fx-border-color: #f1c40f;";
                    case SurfaceTile.BIOME_VOLCANIC_RIDGE -> "-fx-background-color: rgba(180, 50, 30, 0.6); -fx-border-color: #e74c3c;";
                    case SurfaceTile.BIOME_POLAR_ICE -> "-fx-background-color: rgba(60, 140, 200, 0.6); -fx-border-color: #3498db;";
                    case SurfaceTile.BIOME_OCEANIC_SHELF -> "-fx-background-color: rgba(30, 80, 160, 0.6); -fx-border-color: #2980b9;";
                    case SurfaceTile.BIOME_MOUNTAIN_RANGE -> "-fx-background-color: rgba(100, 100, 110, 0.6); -fx-border-color: #95a5a6;";
                    default -> "-fx-background-color: rgba(40, 140, 60, 0.6); -fx-border-color: #2ecc71;";
                };
                tileCard.setStyle(colorStyle + " -fx-background-radius: 6; -fx-border-width: 1; -fx-border-radius: 6;");

                Text tileName = new Text(String.format("Tile #%d [%s]", tile.tileIndex(), tile.biomeType().replace('_', ' ')));
                tileName.setFill(Color.WHITE);
                tileName.setFont(Font.font("Verdana", FontWeight.BOLD, 10));

                Text depositTxt = new Text(tile.hasDeposit() ? "Mineral vein colocated" : "No deposit");
                depositTxt.setFill(tile.hasDeposit() ? Color.GOLD : Color.LIGHTGRAY);
                depositTxt.setFont(Font.font("Verdana", 9));

                Button placeBtn = new Button("Build on tile");
                placeBtn.setStyle("-fx-background-color: #2c3e50; -fx-text-fill: white; -fx-font-size: 9px;");
                final int tIdx = tile.tileIndex();
                placeBtn.setOnAction(e -> {
                    if (humanController != null) {
                        humanController.stageCommand(new PlaceFacilityOnTileCommand(
                                demoPlanet.id(), tIdx, "solar_power_array", playerEmpireId, "PUBLIC_STATE", 50, "technician"
                        ));
                        feedbackLabel.setText("Commissioned facility on surface tile #" + tIdx + " (" + tile.biomeType() + ")");
                        feedbackLabel.setTextFill(Color.LIGHTGREEN);
                    }
                });

                tileCard.getChildren().addAll(tileName, depositTxt, placeBtn);
                tileGrid.add(tileCard, c, r);
            }
        }

        section.getChildren().add(tileGrid);
        return section;
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
