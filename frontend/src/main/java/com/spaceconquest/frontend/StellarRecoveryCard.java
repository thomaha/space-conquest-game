package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.CancelOrbitalTravelCommand;
import com.spaceconquest.control.command.RecoverOrbitalTravelCommand;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.util.concurrent.CompletableFuture;

/** Immutable snapshot previews run off the JavaFX thread; actions only enqueue commands. */
final class StellarRecoveryCard {
    private StellarRecoveryCard() {}

    static VBox create(GameState state, Fleet fleet, HumanController controller, Label feedback) {
        var box = new VBox(5);
        var actual = OrbitalRecovery.initialState(state, fleet);
        if (actual == null) {
            box.getChildren().add(new Label("Orbital recovery unavailable: the saved gravity frame cannot be resolved."));
            return box;
        }
        boolean recoverable = OrbitalRecovery.recoverable(fleet);
        boolean stellar = actual.gravity().frame().kind() == OrbitalFlight.CenterKind.STELLAR;
        String center = stellar ? "Stellar" : actual.gravity().frame().centerBodyId();
        var status = new Label(recoverable ? center + " coast: choose a circular recovery orbit."
                : center + " maneuver: thrust can be canceled while gravity continues.");
        status.setWrapText(true); box.getChildren().add(status);
        if (!recoverable) {
            var cancel = new Button("Cancel orbital maneuvers"); cancel.setId("cancel-stellar-travel");
            cancel.setDisable(controller == null);
            cancel.setOnAction(event -> controller.stageTrackedCommand(new CancelOrbitalTravelCommand(fleet.ownerEntityId(), fleet.id()))
                    .whenComplete((outcome, failure) -> Platform.runLater(() -> feedback.setText(failure == null
                            ? "Orbital cancellation: " + outcome + "." : "Orbital cancellation failed."))));
            box.getChildren().add(cancel); return box;
        }
        double unit = stellar ? ShipSolarEnvironment.AU_KM * 1000 : 1000;
        double offset = stellar ? 0 : actual.gravity().minimumRadiusMeters();
        double minimum = stellar ? .1 : .01, maximum = stellar ? 10000 : (actual.gravity().maximumRadiusMeters() - offset) / unit - .01;
        var radius = new Spinner<Double>(minimum, maximum, Math.clamp((VectorFlight.norm(actual.position()) - offset) / unit, minimum, maximum), stellar ? .01 : 10);
        var phase = new Spinner<Double>(-360, 360, Math.toDegrees(Math.atan2(actual.position().yMeters(), actual.position().xMeters())), 1);
        radius.setId(stellar ? "stellar-recovery-radius" : "orbital-recovery-altitude"); phase.setId("stellar-recovery-phase");
        var preview = new Label(); preview.setWrapText(true);
        var recover = new Button("Recover to circular orbit"); recover.setId("recover-stellar-travel");
        Runnable refresh = () -> refresh(preview, recover, state, fleet, controller, offset + radius.getValue() * unit, Math.toRadians(phase.getValue()));
        radius.valueProperty().addListener((obs, old, value) -> refresh.run());
        phase.valueProperty().addListener((obs, old, value) -> refresh.run());
        recover.setOnAction(event -> controller.stageTrackedCommand(new RecoverOrbitalTravelCommand(fleet.ownerEntityId(), fleet.id(),
                        offset + radius.getValue() * unit, Math.toRadians(phase.getValue()), actual.gravity().frame()))
                .whenComplete((outcome, failure) -> Platform.runLater(() -> feedback.setText(failure == null
                        ? "Orbital recovery: " + outcome + "." : "Orbital recovery failed."))));
        box.getChildren().addAll(new Label(stellar ? "Orbit radius (AU)" : "Parking altitude (km)"), radius, new Label("Orbit phase now (degrees)"), phase, preview, recover);
        refresh.run(); return box;
    }

    private static void refresh(Label preview, Button recover, GameState state, Fleet fleet,
                                HumanController controller, double radius, double phase) {
        Object request = new Object(); preview.setUserData(request); recover.setDisable(true);
        preview.setText("Calculating funded orbital recovery...");
        CompletableFuture.supplyAsync(() -> new RecoverOrbitalTravelCommand(fleet.ownerEntityId(), fleet.id(), radius, phase,
                        OrbitalRecovery.frame(fleet)).preview(state))
                .whenComplete((plan, failure) -> Platform.runLater(() -> {
                    if (preview.getUserData() != request) return;
                    boolean ready = failure == null && plan != null;
                    recover.setDisable(controller == null || !ready);
                    preview.setText(ready ? String.format("%.2f hours | %.1f kg propellant | Circular coast after arrival",
                            plan.days() * 24, plan.propellantKg().values().stream().mapToDouble(Double::doubleValue).sum())
                            : "No funded recovery found in the bounded search. Check orbit choice, fuel, power and passenger supplies.");
                }));
    }
}
