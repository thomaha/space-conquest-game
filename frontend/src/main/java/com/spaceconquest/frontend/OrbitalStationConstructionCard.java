package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.BuildOrbitalStationCommand;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Queued construction at a real selected planetary or lunar parking altitude. */
final class OrbitalStationConstructionCard {
    private record Body(String systemId, String bodyId, String name) {
        @Override public String toString() { return name + " (" + systemId + ")"; }
    }
    private OrbitalStationConstructionCard() {}
    static VBox create(GameState state, String owner, HumanController controller) {
        var box = new VBox(5);
        if (state == null || owner == null) return box;
        var systems = state.empires().stream().filter(empire -> owner.equals(empire.id()))
                .flatMap(empire -> empire.controlledSystemIds().stream()).toList();
        var bodies = new ComboBox<Body>(); bodies.setId("station-parking-body");
        state.solarSystems().stream().filter(system -> systems.contains(system.id())).forEach(system -> {
            system.planets().forEach(body -> bodies.getItems().add(new Body(system.id(), body.id(), body.name())));
            system.planets().forEach(parent -> parent.moons().forEach(moon ->
                    bodies.getItems().add(new Body(system.id(), moon.id(), moon.name() + " (" + parent.name() + ")"))));
        });
        var altitude = new Spinner<Double>(1, 1e9, 500, 500); altitude.setId("station-parking-altitude");
        altitude.setEditable(true);
        var name = new TextField("Orbital port");
        var feedback = new Label("Assembly requires paid materials and work. Add commerce and yard modules after construction.");
        feedback.setWrapText(true);
        var build = new Button("Queue orbital station"); build.setId("queue-altitude-station");
        Runnable refresh = () -> {
            var body = bodies.getValue();
            build.setDisable(controller == null || body == null || !command(owner, body, name.getText(), altitude.getValue()).validate(state));
        };
        bodies.valueProperty().addListener((observable, old, selected) -> refresh.run());
        altitude.valueProperty().addListener((observable, old, selected) -> refresh.run());
        name.textProperty().addListener((observable, old, selected) -> refresh.run());
        build.setOnAction(event -> {
            controller.stageTrackedCommand(command(owner, bodies.getValue(), name.getText(), altitude.getValue()))
                    .whenComplete((outcome, failure) -> Platform.runLater(() -> feedback.setText(failure == null
                            ? "Station construction: " + outcome : "Station construction failed.")));
            build.setDisable(true);
        });
        box.getChildren().addAll(new Label("Construct a planetary or lunar orbital station"),
                new HBox(6, name, bodies, new Label("Parking altitude km:"), altitude, build), feedback);
        if (!bodies.getItems().isEmpty()) bodies.setValue(bodies.getItems().getFirst());
        refresh.run();
        return box;
    }
    private static BuildOrbitalStationCommand command(String owner, Body body, String name, double altitude) {
        return new BuildOrbitalStationCommand(name, body.systemId(), body.bodyId(), owner,
                OrbitalStation.OWNERSHIP_PUBLIC_STATE, 40, "steel", 5, altitude);
    }
}
