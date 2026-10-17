package com.spaceconquest.frontend;

import com.spaceconquest.control.command.FleetServiceStatus;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.Fleet;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Lazy background service diagnostics and committed history, guarded by snapshot identity. */
final class FleetServiceStatusCard {
    private FleetServiceStatusCard() {}
    static TitledPane create(GameState state, Fleet fleet, String owner, Supplier<GameState> current, Set<String> expanded) {
        String key = "service-" + fleet.id();
        var box = new VBox(5); var pane = new TitledPane("Weapon service and history", box);
        pane.setId(key); pane.setExpanded(false);
        pane.expandedProperty().addListener((observable, old, open) -> {
            if (!open) { expanded.remove(key); box.setUserData(new Object()); return; }
            expanded.add(key);
            refresh(box, state, current, () -> CompletableFuture.supplyAsync(() -> FleetServiceStatus.inspect(state, fleet, owner)));
        });
        pane.setExpanded(expanded.contains(key));
        return pane;
    }
    static void refresh(VBox box, GameState state, Supplier<GameState> current, Supplier<CompletableFuture<FleetServiceStatus>> calculation) {
        var request = new Object(); box.setUserData(request); box.getChildren().setAll(line("Inspecting service status...", "service-loading"));
        calculation.get().whenComplete((status, failure) -> Platform.runLater(() -> {
            if (current.get() != state || box.getUserData() != request) return;
            if (failure != null) box.getChildren().setAll(line("Service status unavailable. Reopen after the next update.", "service-error"));
            else render(box, status);
        }));
    }
    static void render(VBox box, FleetServiceStatus status) {
        box.getChildren().setAll(line(status.phase(), "service-phase"), line("Paused mission: " + status.mission(), "service-mission"),
                line("Service or travel destination: " + status.destination(), "service-destination"));
        box.getChildren().add(line("Current recovery requirements", "service-requirements"));
        status.blockers().forEach(reason -> box.getChildren().add(line(reason, "service-blocker")));
        if (status.blockers().isEmpty()) box.getChildren().add(line("No current recovery blocker. Service and return are checked again when the fleet acts.", "service-ready"));
        box.getChildren().add(line("Recent committed service events", "service-history"));
        if (status.history().isEmpty()) box.getChildren().add(line("No recorded service events. Older saves have no reconstructed history.", "service-history-empty"));
        for (int index = status.history().size() - 1; index >= 0; index--) {
            var event = status.history().get(index);
            String kind = event.kind().name().toLowerCase(Locale.ROOT).replace('_', ' ');
            box.getChildren().add(line("Turn " + event.turn() + ": " + kind + ". " + event.detail()
                    + (event.resourcesKg().isEmpty() ? "" : " | Resources kg: " + new TreeMap<>(event.resourcesKg()))
                    + (event.gridInputKwh() <= 0 ? "" : String.format(Locale.ROOT, " | Grid input %.2f kWh", event.gridInputKwh()))
                    + (event.netCreditsSpent() <= 0 ? "" : String.format(Locale.ROOT, " | Net spent %.2f credits", event.netCreditsSpent())), "service-event"));
        }
    }
    private static Label line(String text, String id) { var label = new Label(text); label.setId(id); label.setWrapText(true); return label; }
}
