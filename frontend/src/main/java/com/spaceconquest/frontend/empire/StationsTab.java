package com.spaceconquest.frontend.empire;

import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.SpaceElevator;
import com.spaceconquest.engine.macrostructure.StationModule;
import com.spaceconquest.engine.SolarRadiation;
import com.spaceconquest.control.command.AddStationModuleCommand;
import javafx.geometry.Insets;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Button;
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
        Text power = new Text(String.format("Power: %.1f/%.1f kW demand/generation",
                station.currentPowerDemandKw(), station.currentPowerGenerationKw()));
        power.setFill(Color.LIGHTCYAN);
        card.getChildren().add(power);
        var state = parent.getLatestGameState();
        if (state != null) {
            Text sunlight = new Text(String.format("Solar illumination: %.3f times Sol at 1 AU",
                    SolarRadiation.stationFactor(state.solarSystems(), station)));
            sunlight.setFill(Color.LIGHTCYAN);
            card.getChildren().add(sunlight);
            var command = new AddStationModuleCommand(station.id(), "Solar array (500 kW at Sol, 1 AU)",
                    StationModule.TYPE_SOLAR_ARRAY, 4, 2500, 0, 500, "technician", 0);
            if (parent.getHumanController() != null && command.validate(state)) {
                Button build = new Button("Build solar array (4 slots, 500 kW at Sol, 1 AU)");
                build.setOnAction(event -> {
                    parent.getHumanController().stageCommand(command);
                    build.setDisable(true);
                });
                card.getChildren().add(build);
            }
        }
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
