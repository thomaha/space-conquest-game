package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.SetFleetShieldPolicyCommand;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Queued recharge intent with snapshot-backed eligibility, never direct electrical allocation. */
final class FleetShieldPolicyCard {
    private FleetShieldPolicyCard() {}
    static VBox create(GameState state, Fleet fleet, String owner, HumanController controller,
                       Label feedback, Supplier<GameState> currentSnapshot) {
        var modes = new ComboBox<FleetShieldPolicy.Mode>(); modes.getItems().setAll(FleetShieldPolicy.Mode.values());
        modes.setId("shield-policy-mode"); modes.setValue(fleet.shieldPolicy());
        var apply = new Button("Set shield policy"); apply.setId("shield-policy-apply");
        apply.setDisable(controller == null || !new SetFleetShieldPolicyCommand(owner, fleet.id(), fleet.shieldPolicy()).validate(state));
        modes.setDisable(apply.isDisabled());
        apply.setOnAction(event -> {
            if (modes.getValue() == null) return;
            controller.stageCommand(new SetFleetShieldPolicyCommand(owner, fleet.id(), modes.getValue()));
            feedback.setText("Shield policy queued for the next tick."); feedback.setTextFill(Color.LIGHTGREEN);
        });
        var reason = new Label("Checking shield recharge eligibility..."); reason.setWrapText(true);
        reason.setId("shield-policy-reason"); reason.setTextFill(Color.LIGHTCYAN);
        if (state != null) refresh(reason, state, currentSnapshot, () -> CompletableFuture.supplyAsync(() ->
                fleet.shieldPolicy() + ": " + FleetShieldPolicy.decision(state, fleet, FleetShieldPolicy.travelling(fleet)).reason()));
        var limits = new Label("Charging uses available power after required loads and weapons, protecting travel and arrival reserves. Full shields stop recharge. Conserve energy also defers recharge in combat.");
        limits.setWrapText(true); limits.setTextFill(Color.LIGHTGRAY);
        return new VBox(4, new HBox(8, new Label("Shield policy"), modes, apply), reason, limits);
    }
    static void refresh(Label label, GameState state, Supplier<GameState> currentSnapshot,
                        Supplier<CompletableFuture<String>> calculation) {
        var request = new Object(); label.setUserData(request);
        calculation.get().whenComplete((reason, failure) -> Platform.runLater(() -> {
            if (label.getUserData() != request || currentSnapshot.get() != state) return;
            label.setText(failure == null ? reason : "Shield eligibility unavailable until the next update.");
        }));
    }
}
