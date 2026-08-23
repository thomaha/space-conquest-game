package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.MoveFleetCommand;
import com.spaceconquest.control.command.QueueShipBuildCommand;
import com.spaceconquest.control.command.SetFleetStanceCommand;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.ShipInstance;
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
 * Interactive UI panel for monitoring fleet dispositions, warp transits, fuel reserves, shipyard commissioning and fleet stances.
 */
public class FleetManagementView {
    private VBox root;
    private VBox content;
    private ScrollPane scrollPane;
    private Label feedbackLabel;
    private final Menubar menubar;
    private HumanController humanController;
    private String playerEmpireId = "terran_confederation";
    private final List<Fleet> activeFleets = new ArrayList<>();

    public FleetManagementView(Menubar menubar) {
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
                "-fx-border-color: #e17055; -fx-border-width: 2; " +
                "-fx-border-radius: 10; -fx-background-radius: 10;");
        root.setPrefSize(920, 700);

        Text title = new Text("Imperial fleet command and naval operations");
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

        feedbackLabel = new Label("Ready | Dispatch fleet movement orders or queue starship construction.");
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

    public void updateFleets(List<Fleet> fleets) {
        activeFleets.clear();
        if (fleets != null) {
            activeFleets.addAll(fleets);
        }
        if (root.isVisible()) {
            renderContent();
        }
    }

    private void renderContent() {
        content.getChildren().clear();

        // 1. Shipyard Construction Queue Section
        content.getChildren().add(createShipyardQueueSection());

        // 2. Active Fleets Section
        content.getChildren().add(createActiveFleetsSection());
    }

    private VBox createShipyardQueueSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(12));
        section.setStyle("-fx-background-color: rgba(25, 45, 75, 0.75); -fx-background-radius: 8; -fx-border-color: #e17055; -fx-border-width: 1; -fx-border-radius: 8;");

        Text title = new Text("Orbital shipyard commissioning");
        title.setFill(Color.LIGHTCORAL);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 15));

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);

        Label sysLbl = new Label("Shipyard system:");
        sysLbl.setTextFill(Color.LIGHTCYAN);
        ComboBox<String> sysCombo = new ComboBox<>();
        sysCombo.getItems().addAll("sol", "alpha_centauri", "sirius", "vega", "proxima");
        sysCombo.setValue("sol");

        Label designLbl = new Label("Blueprint design:");
        designLbl.setTextFill(Color.LIGHTCYAN);
        ComboBox<String> designCombo = new ComboBox<>();
        designCombo.getItems().addAll("design_fighter_alpha", "design_cargo_freighter_01", "design_cruiser_alpha", "design_colony_ship_01");
        designCombo.setValue("design_cruiser_alpha");

        Button queueBtn = new Button("Queue ship construction");
        queueBtn.setStyle("-fx-background-color: #e17055; -fx-text-fill: white; -fx-font-weight: bold;");
        queueBtn.setOnAction(e -> {
            if (humanController != null) {
                humanController.stageCommand(new QueueShipBuildCommand(
                        playerEmpireId, designCombo.getValue(), sysCombo.getValue()
                ));
                feedbackLabel.setText("Queued ship construction for " + designCombo.getValue() + " in " + sysCombo.getValue().toUpperCase());
                feedbackLabel.setTextFill(Color.LIGHTGREEN);
            }
        });

        grid.add(sysLbl, 0, 0);
        grid.add(sysCombo, 1, 0);
        grid.add(designLbl, 2, 0);
        grid.add(designCombo, 3, 0);

        section.getChildren().addAll(title, grid, queueBtn);
        return section;
    }

    private VBox createActiveFleetsSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: rgba(20, 35, 60, 0.7); -fx-background-radius: 8;");

        Text summary = new Text("Active fleets under command: " + activeFleets.size());
        summary.setFill(Color.LIGHTSKYBLUE);
        summary.setFont(Font.font("Verdana", FontWeight.BOLD, 15));
        section.getChildren().add(summary);

        if (activeFleets.isEmpty()) {
            Text empty = new Text("No active military, cargo or mining fleets currently deployed.");
            empty.setFill(Color.LIGHTGRAY);
            section.getChildren().add(empty);
        } else {
            for (Fleet fleet : activeFleets) {
                section.getChildren().add(createFleetCard(fleet));
            }
        }

        return section;
    }

    private VBox createFleetCard(Fleet fleet) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(10));
        card.setStyle("-fx-background-color: rgba(30, 45, 75, 0.7); -fx-background-radius: 8; -fx-border-color: #fab1a0; -fx-border-width: 1; -fx-border-radius: 8;");

        HBox topRow = new HBox(12);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Text nameText = new Text(fleet.name() + " (" + fleet.ships().size() + " ships)");
        nameText.setFill(Color.WHITE);
        nameText.setFont(Font.font("Verdana", FontWeight.BOLD, 14));

        Text locationText = new Text("System: " + fleet.currentSystemId().toUpperCase() +
                (fleet.isInWarp() ? " -> in warp transit to " + fleet.targetSystemId().toUpperCase() + String.format(" (%.0f%%)", fleet.transitProgress() * 100.0) : " (in orbit)"));
        locationText.setFill(fleet.isInWarp() ? Color.GOLD : Color.LIGHTGREEN);
        locationText.setFont(Font.font("Verdana", 12));

        topRow.getChildren().addAll(nameText, locationText);

        HBox orderRow = new HBox(10);
        orderRow.setAlignment(Pos.CENTER_LEFT);

        ComboBox<String> targetCombo = new ComboBox<>();
        targetCombo.getItems().addAll("sol", "alpha_centauri", "sirius", "vega", "proxima");
        targetCombo.setValue(fleet.targetSystemId() != null ? fleet.targetSystemId() : "sol");

        Button moveBtn = new Button("Move fleet");
        moveBtn.setStyle("-fx-background-color: #0984e3; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px;");
        moveBtn.setOnAction(e -> {
            if (humanController != null) {
                humanController.stageCommand(new MoveFleetCommand(
                        fleet.id(), targetCombo.getValue()
                ));
                feedbackLabel.setText("Dispatched fleet " + fleet.name() + " on warp trajectory to " + targetCombo.getValue().toUpperCase());
                feedbackLabel.setTextFill(Color.LIGHTGREEN);
            }
        });

        ComboBox<String> stanceCombo = new ComboBox<>();
        stanceCombo.getItems().addAll("PASSIVE", "AGGRESSIVE", "PATROL", "ESCORT");
        stanceCombo.setValue(fleet.fleetStance() != null ? fleet.fleetStance() : "PASSIVE");

        Button stanceBtn = new Button("Set stance");
        stanceBtn.setStyle("-fx-background-color: #00b894; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px;");
        stanceBtn.setOnAction(e -> {
            if (humanController != null) {
                humanController.stageCommand(new SetFleetStanceCommand(
                        fleet.id(), stanceCombo.getValue()
                ));
                feedbackLabel.setText("Updated operational stance for fleet " + fleet.name() + " to " + stanceCombo.getValue());
                feedbackLabel.setTextFill(Color.LIGHTGREEN);
            }
        });

        orderRow.getChildren().addAll(new Label("Warp to:"), targetCombo, moveBtn, new Label("Stance:"), stanceCombo, stanceBtn);

        VBox shipList = new VBox(4);
        shipList.setPadding(new Insets(4, 0, 0, 10));
        for (ShipInstance ship : fleet.ships()) {
            Text shipText = new Text(String.format("• Ship [%s] Design: %s | Hull: %.0f HP | Shield: %.0f HP | Fuel: %.1f kg",
                    ship.id(), ship.designId(), ship.currentHullHealth(), ship.currentShieldHealth(), ship.currentFuelKg()));
            shipText.setFill(Color.GAINSBORO);
            shipText.setFont(Font.font("Verdana", 11));
            shipList.getChildren().add(shipText);
        }

        card.getChildren().addAll(topRow, orderRow, shipList);
        return card;
    }
}
