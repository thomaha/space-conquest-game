package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.Fleet;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;

/** Escort ships join the selected convoy; authoritative formation changes run on the tick. */
final class FleetEscortCard {
    private FleetEscortCard() {}
    private record Target(String id, String name) { @Override public String toString() { return name; } }

    static HBox create(GameState state, Fleet fleet, String owner, HumanController controller, Label feedback) {
        var targets = new ComboBox<Target>();
        if (state != null && owner.equals(fleet.ownerEntityId())) state.fleets().stream()
                .filter(target -> new EscortFleetCommand(owner, fleet.id(), target.id()).validate(state))
                .forEach(target -> targets.getItems().add(new Target(target.id(), target.name())));
        targets.setPromptText("Fleet to escort");
        targets.getSelectionModel().selectFirst();
        var join = new Button("Escort fleet");
        join.setDisable(controller == null || targets.getItems().isEmpty());
        join.setTooltip(new Tooltip("Join an owned fleet at this site. The convoy travels together at its slowest ship's pace."));
        join.setOnAction(event -> {
            var target = targets.getValue();
            if (target == null) return;
            controller.stageCommand(new EscortFleetCommand(owner, fleet.id(), target.id()));
            feedback.setText("Escort formation queued for next tick.");
            feedback.setTextFill(Color.LIGHTGREEN);
        });
        var cancel = new Button("Detach escort");
        cancel.setDisable(controller == null || !new CancelEscortFleetCommand(owner, fleet.id()).validate(state));
        cancel.setOnAction(event -> {
            controller.stageCommand(new CancelEscortFleetCommand(owner, fleet.id()));
            feedback.setText("Escort detachment queued; paid travel progress is retained.");
            feedback.setTextFill(Color.LIGHTGREEN);
        });
        var recovery = fleet.interceptOrder() == null ? null : fleet.interceptOrder().protection();
        var saved = recovery == null || recovery.repairIntent() == null ? null : recovery.repairIntent().escort();
        var status = new Label(fleet.escortOrder() != null ? "Escorted by " + fleet.escortOrder().escortFleetName()
                : saved == null ? "Escort: none" : "Escort paused for repair: " + saved.escortFleetName());
        return new HBox(8, status, targets, join, cancel);
    }
}
