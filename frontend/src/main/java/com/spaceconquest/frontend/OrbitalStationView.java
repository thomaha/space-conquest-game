package com.spaceconquest.frontend;

import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.SpaceElevator;
import com.spaceconquest.engine.macrostructure.StationModule;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
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
 * Interactive UI panel displaying orbital space stations, attached module slots,
 * power grid balance, shipyard capabilities and space elevator tethers.
 */
public class OrbitalStationView {

    private VBox root;
    private VBox content;
    private final Menubar menubar;
    private final List<OrbitalStation> stations = new ArrayList<>();
    private final List<SpaceElevator> elevators = new ArrayList<>();

    public OrbitalStationView(Menubar menubar) {
        this.menubar = menubar;
        build();
    }

    private void build() {
        root = new VBox(15);
        content = new VBox(12);
        ScrollPane scrollPane = new ScrollPane(content);

        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: rgba(14, 22, 45, 0.96); " +
                "-fx-border-color: #3498db; -fx-border-width: 2; " +
                "-fx-border-radius: 10; -fx-background-radius: 10;");
        root.setPrefSize(920, 700);

        Text title = new Text("Orbital stations, shipyard slipways and space elevators");
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

        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setPadding(new Insets(10));

        root.getChildren().addAll(header, scrollPane);
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

    public void updateData(List<OrbitalStation> newStations, List<SpaceElevator> newElevators) {
        stations.clear();
        if (newStations != null) stations.addAll(newStations);

        elevators.clear();
        if (newElevators != null) elevators.addAll(newElevators);

        if (root.isVisible()) {
            renderContent();
        }
    }

    private void renderContent() {
        content.getChildren().clear();

        // 1. Orbital Stations Section
        content.getChildren().add(createStationsSection());

        // 2. Space Elevators Section
        content.getChildren().add(createElevatorsSection());
    }

    private VBox createStationsSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: rgba(25, 40, 70, 0.7); -fx-background-radius: 8; -fx-border-color: #3498db; -fx-border-width: 1; -fx-border-radius: 8;");

        Text header = new Text("Orbital space stations and starframe module arrays");
        header.setFill(Color.LIGHTCYAN);
        header.setFont(Font.font("Verdana", FontWeight.BOLD, 15));
        section.getChildren().add(header);

        if (stations.isEmpty()) {
            Text empty = new Text("No orbital space stations currently deployed. Use construction ships to deploy space stations.");
            empty.setFill(Color.LIGHTGRAY);
            section.getChildren().add(empty);
        } else {
            for (OrbitalStation station : stations) {
                VBox sBox = new VBox(6);
                sBox.setPadding(new Insets(8));
                sBox.setStyle("-fx-background-color: rgba(15, 30, 55, 0.6); -fx-background-radius: 6;");

                Text sTitle = new Text(String.format("Station: %s [%s] (System: %s, Orbit: %s)",
                        station.name(), station.id(), station.systemId().toUpperCase(), station.planetOrbitId()));
                sTitle.setFill(Color.LIGHTGREEN);
                sTitle.setFont(Font.font("Verdana", FontWeight.BOLD, 13));

                Text stats = new Text(String.format("Slots: %d/%d | Power: %.1f/%.1f kW | Shield: %.0f/%.0f | Hull: %.0f/%.0f | Armor: %s (%.1fcm) | Status: %s",
                        station.getAllocatedSlots(), station.totalSlots(), station.currentPowerDemandKw(), station.currentPowerGenerationKw(),
                        station.currentShieldHealth(), station.maxShieldHealth(), station.currentHullHealth(), station.maxHullHealth(),
                        station.armorMaterialId(), station.armorThicknessCm(), station.isOperational() ? "OPERATIONAL" : "OFFLINE"));
                stats.setFill(Color.WHITE);
                stats.setFont(Font.font("Verdana", 11));

                sBox.getChildren().addAll(sTitle, stats);

                for (StationModule mod : station.modules()) {
                    Text mText = new Text(String.format("  • [%s] %s (%d slots) | Power: -%.1f kW / +%.1f kW | %s (%d workers) | %s",
                            mod.type(), mod.name(), mod.slotSize(), mod.powerDrawKw(), mod.powerOutputKw(),
                            mod.workforceProfessionId(), mod.requiredWorkers(), mod.isOnline() ? "ONLINE" : "OFFLINE"));
                    mText.setFill(mod.isOnline() ? Color.LIGHTGRAY : Color.SALMON);
                    mText.setFont(Font.font("Verdana", 10));
                    sBox.getChildren().add(mText);
                }

                section.getChildren().add(sBox);
            }
        }

        return section;
    }

    private VBox createElevatorsSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: rgba(25, 40, 70, 0.7); -fx-background-radius: 8; -fx-border-color: #f1c40f; -fx-border-width: 1; -fx-border-radius: 8;");

        Text header = new Text("Planetary space elevators and geostationary tethers");
        header.setFill(Color.GOLD);
        header.setFont(Font.font("Verdana", FontWeight.BOLD, 15));
        section.getChildren().add(header);

        if (elevators.isEmpty()) {
            Text empty = new Text("No planetary space elevators currently constructed. Build space elevators to reduce launch costs to near-zero.");
            empty.setFill(Color.LIGHTGRAY);
            section.getChildren().add(empty);
        } else {
            for (SpaceElevator elevator : elevators) {
                VBox card = new VBox(4);
                card.setPadding(new Insets(6));
                card.setStyle("-fx-background-color: rgba(15, 30, 55, 0.6); -fx-background-radius: 6;");

                Text eInfo = new Text(String.format("• Space elevator [%s] Planet: %s | Owner: %s | Capacity: %,.0f kg/turn | Discount: %.0f%% | Integrity: %.1f%%",
                        elevator.id(), elevator.planetId().toUpperCase(), elevator.ownerEntityId(),
                        elevator.transitThroughputCapacityKgPerTurn(), elevator.surfaceToOrbitCostDiscount() * 100.0,
                        elevator.structuralIntegrityPercent()));
                eInfo.setFill(Color.LIGHTGREEN);
                eInfo.setFont(Font.font("Verdana", 11));

                card.getChildren().add(eInfo);
                section.getChildren().add(card);
            }
        }

        return section;
    }
}
