package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.CancelOrbitalTravelCommand;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.*;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import java.util.concurrent.CompletableFuture;

/** Snapshot-only orbital status and asynchronous shared departure checks. */
final class OrbitalTravelCard {
    private OrbitalTravelCard() {}

    static VBox status(Fleet fleet, HumanController controller, Label feedback) {
        var box = new VBox(4);
        var flight = fleet.location().orbitalFlight();
        if (flight == null) return box;
        var position = flight.position();
        var label = new Label(String.format("Orbital %s | %.1f days elapsed | %.1f km/s | %s",
                flight.status(), flight.elapsedSeconds() / 86400, position.speedMps() / 1000, flight.message()));
        label.setWrapText(true);
        var cancel = new Button(flight.atSource() ? "Cancel orbital waiting" : "Cancel orbital maneuvers");
        cancel.setId("cancel-orbital-travel");
        cancel.setDisable(controller == null);
        cancel.setOnAction(event -> {
            controller.stageTrackedCommand(new CancelOrbitalTravelCommand(fleet.ownerEntityId(), fleet.id()))
                    .whenComplete((outcome, failure) -> Platform.runLater(() -> feedback.setText(failure == null
                            ? "Orbital cancellation: " + outcome + ". Existing motion is retained." : "Orbital cancellation failed.")));
        });
        box.getChildren().addAll(label, OrbitalJourneyDiagram.create(flight), cancel);
        if (flight.failed()) box.getChildren().add(new Label(OrbitalTravel.recoveryProblem(fleet)));
        return box;
    }

    static void refresh(Label preview, Button move, GameState state, Fleet fleet, FleetLocation.Site target, boolean emergency) {
        Object request = new Object(); preview.setUserData(request);
        move.setDisable(true); preview.setText("Calculating local departure...");
        if (state == null || target == null || fleet.location().inTransit() || fleet.hasInterstellarOrder()) {
            preview.setText("Choose a stationary fleet and destination."); return;
        }
        Fleet planning = emergency ? fleet.withFuelPolicy(new FleetFuelPolicy(0, 0, 0)) : fleet;
        CompletableFuture.supplyAsync(() -> {
            var plan = LocalTravel.plan(state, planning, target);
            if (plan == null) return new Result(false, OrbitalTravel.applies(state, planning, target)
                    ? "Orbital departure unavailable: " + OrbitalTravel.preview(state, planning, target).problem()
                    : "Local departure unavailable: geometry or fuel requirements cannot be funded.");
            boolean ready = ShipPowerForecast.ready(ShipPowerForecast.departure(state, planning, target, plan, null));
            String details = FleetFuelReserveCard.localPreview(state, fleet, target, emergency);
            if (plan.orbital() != null) details = String.format("%s impulse prototype: wait %.1f days | coast %.1f days | funded maneuvers %.1f kg | %s",
                    plan.orbital().parkingTransfer() ? "Parking transfer" : plan.orbital().lunarTransfer() ? "Lunar transfer" : "Orbital", plan.orbital().waitSeconds() / 86400, plan.orbital().coastDurationSeconds() / 86400,
                    plan.propellantKg().values().stream().mapToDouble(Double::doubleValue).sum(), details);
            else details = "Existing local travel model: " + details;
            return new Result(ready, details + (ready ? " | Electricity and arrival reserve checked." : " | Electrical endurance is insufficient."));
        }).whenComplete((result, failure) -> Platform.runLater(() -> {
            if (preview.getUserData() != request) return;
            preview.setText(failure == null ? result.text() : "Local preview failed.");
            move.setDisable(failure != null || !result.ready());
        }));
    }

    private record Result(boolean ready, String text) {}
}
