package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.GameState;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Snapshot-only selectors and guarded background recovery quotes; actions use tracked tick commands. */
final class ShipRetirementCard {
    private record Choice(String id, String label) { @Override public String toString() { return label; } }
    private ShipRetirementCard() {}
    static TitledPane create(GameState state, String owner, Supplier<GameState> current, HumanController controller) {
        var box = new VBox(6); var results = new VBox(5); var ships = new ComboBox<Choice>(); ships.setId("retirement-ship");
        var preview = new Button("Preview retirement"); var start = new Button("Start paid retirement"); start.setId("retirement-start"); start.setDisable(true);
        if (state != null) state.fleets().stream().filter(fleet -> owner.equals(fleet.ownerEntityId())).forEach(fleet -> fleet.ships().stream()
                .filter(ship -> owner.equals(ship.ownerEntityId()) && ship.currentHullHealth() > 0).forEach(ship -> ships.getItems().add(new Choice(ship.id(), fleet.name() + " / " + ship.id()))));
        if (!ships.getItems().isEmpty()) ships.getSelectionModel().selectFirst(); preview.setDisable(ships.getItems().isEmpty());
        ships.valueProperty().addListener((observable, old, value) -> { start.setDisable(true); results.setUserData(new Object()); results.getChildren().clear(); });
        preview.setOnAction(event -> {
            if (ships.getValue() == null) return; String id = ships.getValue().id(); start.setDisable(true);
            refresh(results, state, current, () -> CompletableFuture.supplyAsync(() -> ShipRetirementQuote.inspect(state, owner, id)), quote -> start.setDisable(controller == null || !quote.ready()));
        });
        start.setOnAction(event -> {
            if (controller == null || current.get() != state || ships.getValue() == null) return; start.setDisable(true);
            controller.stageTrackedCommand(new QueueShipRetirementCommand(owner, ships.getValue().id())).whenComplete((outcome, failure) -> Platform.runLater(() -> {
                if (current.get() == state) results.getChildren().setAll(line(failure == null && outcome == CommandOutcome.EXECUTED
                        ? "Paid retirement accepted. The ship remains unavailable during dismantling." : "Retirement was not accepted. Refresh and preview again.", "retirement-submission"));
            }));
            results.getChildren().setAll(line("Retirement queued for the next simulation tick.", "retirement-submission"));
        });
        box.getChildren().addAll(line("Paid dismantling removes this ship on completion and sells recovered materials to the local market. Disembark passengers first. Battery energy is dissipated during dismantling.", "retirement-description"),
                new HBox(8, ships, preview, start), results);
        var pane = new TitledPane("Ship retirement and salvage", box); pane.setExpanded(false);
        pane.expandedProperty().addListener((observable, old, expanded) -> { if (!expanded) { results.setUserData(new Object()); start.setDisable(true); } }); return pane;
    }
    static void refresh(VBox results, GameState state, Supplier<GameState> current, Supplier<CompletableFuture<ShipRetirementQuote>> calculation,
                        java.util.function.Consumer<ShipRetirementQuote> rendered) {
        var request = new Object(); results.setUserData(request); results.getChildren().setAll(line("Calculating retirement...", "retirement-loading"));
        calculation.get().whenComplete((quote, failure) -> Platform.runLater(() -> {
            if (results.getUserData() != request || current.get() != state) return;
            if (failure != null) results.getChildren().setAll(line("Unable to calculate retirement. Refresh the view.", "retirement-error"));
            else { render(results, quote); rendered.accept(quote); }
        }));
    }
    static void render(VBox results, ShipRetirementQuote quote) {
        results.getChildren().setAll(line(quote.ready() ? "Paid retirement is feasible." : "Retirement preparation required.", "retirement-status"));
        if (quote.sale() != null) results.getChildren().addAll(line("Recovered materials kg: " + new TreeMap<>(quote.sale().materialsKg()), "retirement-materials"),
                line(String.format(Locale.ROOT, "Estimated market proceeds %.2f credits after %.2f tariff; service fee %.2f credits; %.1f work hours; %.2f days including current queue.",
                        quote.sale().credits(), quote.sale().tariffCredits(), quote.serviceFeeCredits(), quote.workHours(), quote.estimatedDays()), "retirement-cost"));
        quote.blockers().forEach(reason -> results.getChildren().add(line(reason, "retirement-blocker")));
        results.getChildren().add(line("Recovery uses surviving captured hardware and remaining stores. Damage, market prices, buyer funds and storage are checked again during work. Cancellation refunds no spent work or fees.", "retirement-terms"));
    }
    private static Label line(String text, String id) { var value = new Label(text); value.setId(id); value.setWrapText(true); return value; }
}
