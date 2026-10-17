package com.spaceconquest.frontend;

import com.spaceconquest.control.ai.SupplyInvestmentStatus;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.IndustryRecipeCatalog;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Lazy read-only investment inspection; expensive forecasts never run on the JavaFX thread. */
final class SupplyInvestmentStatusCard {
    private SupplyInvestmentStatusCard() {}
    static TitledPane create(GameState state, String owner, Supplier<GameState> current, Set<String> expanded) {
        String key = "supply-investment"; var box = new VBox(6);
        var pane = new TitledPane("Supply-chain investment and blockers", box); pane.setId(key); pane.setExpanded(false);
        pane.expandedProperty().addListener((observable, old, open) -> {
            if (!open) { expanded.remove(key); box.setUserData(new Object()); return; }
            expanded.add(key);
            refresh(box, state, current, () -> CompletableFuture.supplyAsync(() -> SupplyInvestmentStatus.inspect(state, owner)));
        });
        pane.setExpanded(expanded.contains(key)); return pane;
    }
    static void refresh(VBox box, GameState state, Supplier<GameState> current, Supplier<CompletableFuture<SupplyInvestmentStatus>> calculation) {
        var request = new Object(); box.setUserData(request);
        box.getChildren().setAll(line("Checking supply-chain investment...", "investment-loading"));
        calculation.get().whenComplete((status, failure) -> Platform.runLater(() -> {
            if (current.get() != state || box.getUserData() != request) return;
            if (failure != null) box.getChildren().setAll(line("Investment status unavailable. Reopen after the next update.", "investment-error"));
            else render(box, status);
        }));
    }
    static void render(VBox box, SupplyInvestmentStatus status) {
        box.getChildren().setAll(line("Current requirements; ready proposals are rechecked when commands execute.", "investment-caption"));
        if (status.rows().isEmpty()) box.getChildren().add(line("No local supply-chain investment need found in this review.", "investment-empty"));
        for (var row : status.rows()) {
            String product = row.materialId().isBlank() ? "launch service" : row.materialId().replace('_', ' ');
            String app = row.applicationId().isBlank() ? "" : " | " + IndustryRecipeCatalog.applicationName(row.applicationId());
            box.getChildren().add(line(row.bodyId() + " | " + product + app + " | " + row.action() + ": " + row.reason(), "investment-row"));
        }
    }
    private static Label line(String text, String id) {
        var label = new Label(text); label.setId(id); label.setWrapText(true); label.setTextFill(javafx.scene.paint.Color.LIGHTCYAN); return label;
    }
}
