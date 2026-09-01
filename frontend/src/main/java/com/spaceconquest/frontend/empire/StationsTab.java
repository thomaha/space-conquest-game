package com.spaceconquest.frontend.empire;

import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.SpaceElevator;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.List;

public class StationsTab {
    private final EmpireView parent;

    public StationsTab(EmpireView parent) {
        this.parent = parent;
    }

    public VBox buildStationsTabContent() {
        VBox container = new VBox(10);
        VBox.setVgrow(container, Priority.ALWAYS);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setPadding(new Insets(6));
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        VBox content = new VBox(14);
        scrollPane.setContent(content);

        List<OrbitalStation> stations = parent.getOrbitalStationsForPlayerEmpire();
        List<SpaceElevator> elevators = parent.getSpaceElevatorsForPlayerEmpire();

        Text title = new Text("Orbital assets and spaceborne infrastructure");
        title.setFill(Color.AQUA);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 16));
        content.getChildren().add(title);

        if (stations.isEmpty() && elevators.isEmpty()) {
            Text empty = new Text("No orbital assets deployed. Foundation of space-based industry requires orbital presence.");
            empty.setFill(Color.LIGHTGRAY);
            content.getChildren().add(empty);
        } else {
            for (SpaceElevator elevator : elevators) {
                content.getChildren().add(createSpaceElevatorCard(elevator));
            }
            for (OrbitalStation station : stations) {
                content.getChildren().add(createStationCard(station));
            }
        }

        container.getChildren().add(scrollPane);
        return container;
    }

    private VBox createStationCard(OrbitalStation station) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(12));
        card.setStyle("-fx-background-color: rgba(30, 45, 75, 0.7); -fx-background-radius: 8; -fx-border-color: #3498db; -fx-border-width: 1;");
        
        Text name = new Text(station.name() + " (Orbital Station)");
        name.setFill(Color.GOLD);
        name.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        
        Text details = new Text("Location: " + station.planetOrbitId() + " | Modules: " + (station.modules() != null ? station.modules().size() : 0));
        details.setFill(Color.WHITE);
        
        card.getChildren().addAll(name, details);
        return card;
    }

    private VBox createSpaceElevatorCard(SpaceElevator elevator) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(12));
        card.setStyle("-fx-background-color: rgba(45, 55, 90, 0.7); -fx-background-radius: 8; -fx-border-color: #f1c40f; -fx-border-width: 1;");
        
        Text name = new Text("Space Elevator [" + elevator.id() + "]");
        name.setFill(Color.GOLD);
        name.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        
        Text details = new Text("Terminal: " + elevator.planetId() + " | Operational: " + elevator.isOperational());
        details.setFill(Color.WHITE);
        
        card.getChildren().addAll(name, details);
        return card;
    }
}
