package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.Fleet;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;

/** Snapshot contact choices; authoritative validation and physical planning happen on the tick. */
final class FleetInterceptCard {
    private FleetInterceptCard() {}
    private record Target(String id, String name) {
        @Override public String toString() { return name; }
    }
    static HBox create(GameState state, Fleet fleet, String owner, HumanController controller, Label feedback) {
        var targets = new ComboBox<Target>();
        if (state != null && owner.equals(fleet.ownerEntityId())) state.fleets().stream()
                .filter(target -> new InterceptFleetCommand(owner, fleet.id(), target.id()).validate(state))
                .forEach(target -> targets.getItems().add(new Target(target.id(), target.name())));
        targets.setPromptText("Detected hostile fleet");
        targets.getSelectionModel().selectFirst();
        var intercept = new Button("Intercept fleet");
        intercept.setDisable(controller == null || targets.getItems().isEmpty());
        intercept.setTooltip(new Tooltip("Approach a detected stationary target using available fuel and power. "
                + "A range advantage can use a funded stand-off parking orbit around the same body. "
                + "Straight-line coast or drift can be intercepted from deep space, including sideways approaches. "
                + "Stellar orbital coasts use a funded gravity trajectory. Planetary and lunar coasts can be "
                + "intercepted from parking or interrupted flight around the same body. "
                + "A range advantage can approach these coasts at a funded firing offset with matched arrival velocity. "
                + "Other moving targets wait. Lost contact ends pursuit. "
                + "Actual weapon range can permit fire before rendezvous; an already reachable target needs no new approach. "
                + "Committed flight continues physically during combat."));
        intercept.setOnAction(event -> {
            if (targets.getValue() == null) return;
            controller.stageCommand(new InterceptFleetCommand(owner, fleet.id(), targets.getValue().id()));
            feedback.setText("Intercept order queued for the next tick.");
            feedback.setTextFill(Color.LIGHTGREEN);
        });
        var recovery = fleet.interceptOrder() == null ? null : fleet.interceptOrder().protection();
        var repair = recovery != null && recovery.repairIntent() != null;
        var cancel = new Button("Cancel intercept");
        cancel.setDisable(controller == null || !new CancelInterceptFleetCommand(owner, fleet.id()).validate(state));
        cancel.setOnAction(event -> {
            controller.stageCommand(new CancelInterceptFleetCommand(owner, fleet.id()));
            feedback.setText("Cancellation queued; the current journey continues.");
            feedback.setTextFill(Color.LIGHTGREEN);
        });
        var status = new Label(fleet.interceptOrder() == null ? "Intercept: none" : !repair ? "Intercept order active"
                : recovery.stage() == com.spaceconquest.engine.ship.FleetProtectionAssignment.Stage.REPAIRING
                    ? recovery.serviceTargetId().isBlank() ? "Repairing onboard" : "Repairing at " + recovery.serviceTargetId()
                    : "Returning after repair");
        return new HBox(8, status, targets, intercept, cancel);
    }
}
