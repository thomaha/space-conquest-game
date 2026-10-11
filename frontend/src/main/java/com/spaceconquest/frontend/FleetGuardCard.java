package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.*;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;

/** Snapshot-backed controls for an explicit hold order at the fleet's current site. */
final class FleetGuardCard {
    private FleetGuardCard() {}
    private record Target(FleetGuardOrder.TargetType type, String id, String label) {
        @Override public String toString() { return label; }
    }

    static HBox create(GameState state, Fleet fleet, String playerEmpireId, HumanController controller, Label feedback) {
        var targets = new ComboBox<Target>();
        if (state != null && playerEmpireId.equals(fleet.ownerEntityId())) {
            state.orbitalStations().forEach(station -> add(state, fleet, targets,
                    new Target(FleetGuardOrder.TargetType.STATION, station.id(), station.name())));
            state.solarSystems().stream().filter(system -> system.id().equals(fleet.currentSystemId())).forEach(system ->
                    system.planets().forEach(planet -> {
                        add(state, fleet, targets, new Target(FleetGuardOrder.TargetType.BODY, planet.id(), planet.name()));
                        planet.moons().forEach(moon -> add(state, fleet, targets,
                                new Target(FleetGuardOrder.TargetType.BODY, moon.id(), moon.name())));
                    }));
            state.fleets().forEach(target -> add(state, fleet, targets,
                    new Target(FleetGuardOrder.TargetType.FLEET, target.id(), target.name())));
        }
        targets.setPromptText("Object at current site");
        targets.getSelectionModel().selectFirst();
        var guard = new Button("Guard object");
        guard.setDisable(controller == null || targets.getItems().isEmpty());
        guard.setTooltip(new Tooltip("Hold at an owned object's current site. Move there first; moving targets require escort."));
        guard.setOnAction(event -> {
            var target = targets.getValue();
            if (target != null) controller.stageCommand(new GuardObjectCommand(playerEmpireId, fleet.id(), target.type(), target.id()));
            feedback.setText("Guard order queued; it takes effect next tick.");
            feedback.setTextFill(Color.LIGHTGREEN);
        });
        var cancel = new Button("Cancel guard");
        cancel.setDisable(controller == null || !new CancelGuardObjectCommand(playerEmpireId, fleet.id()).validate(state));
        cancel.setOnAction(event -> {
            controller.stageCommand(new CancelGuardObjectCommand(playerEmpireId, fleet.id()));
            feedback.setText("Guard cancellation queued.");
            feedback.setTextFill(Color.LIGHTGREEN);
        });
        var recovery = fleet.interceptOrder() == null ? null : fleet.interceptOrder().protection();
        var saved = recovery == null || recovery.repairIntent() == null ? null : recovery.repairIntent().guard();
        var status = new Label(fleet.guardOrder() != null ? "Guarding " + fleet.guardOrder().targetId()
                : saved == null ? "Holding orders: none" : "Guard paused for repair: " + saved.targetId());
        return new HBox(8, status, targets, guard, cancel);
    }

    private static void add(GameState state, Fleet fleet, ComboBox<Target> targets, Target target) {
        if (new GuardObjectCommand(fleet.ownerEntityId(), fleet.id(), target.type(), target.id()).validate(state))
            targets.getItems().add(target);
    }
}
