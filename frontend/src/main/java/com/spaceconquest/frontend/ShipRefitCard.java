package com.spaceconquest.frontend;

import com.spaceconquest.control.command.ShipRefitQuote;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.ShipModelComparison;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** User-requested background refit estimates with snapshot and request identity guards. */
final class ShipRefitCard {
    private ShipRefitCard() {}
    private record Choice(String id, String label) { @Override public String toString() { return label; } }
    static TitledPane create(GameState state, String owner, Supplier<GameState> current) {
        return create(state, owner, current, null);
    }
    static TitledPane create(GameState state, String owner, Supplier<GameState> current, com.spaceconquest.control.HumanController controller) {
        var box = new VBox(6); var results = new VBox(5); var ship = new ComboBox<Choice>(); var target = new ComboBox<Choice>();
        var start = new Button("Start paid refit"); start.setId("refit-start"); start.setDisable(true);
        ship.setId("refit-ship"); target.setId("refit-model");
        if (state != null) {
            state.fleets().stream().filter(fleet -> owner.equals(fleet.ownerEntityId())).forEach(fleet -> fleet.ships().stream()
                    .filter(item -> owner.equals(item.ownerEntityId()) && item.currentHullHealth() > 0).forEach(item -> ship.getItems().add(new Choice(item.id(), fleet.name() + " / " + item.id()))));
            state.shipDesigns().stream().filter(model -> owner.equals(model.ownerEntityId()) && !model.isProprietaryCorporateDesign())
                    .sorted(Comparator.comparing(model -> model.name())).forEach(model -> target.getItems().add(new Choice(model.id(), model.name())));
        }
        var preview = new Button("Preview refit"); preview.setDisable(ship.getItems().isEmpty() || target.getItems().isEmpty());
        if (!ship.getItems().isEmpty()) ship.getSelectionModel().selectFirst();
        if (!target.getItems().isEmpty()) target.getSelectionModel().selectFirst();
        preview.setOnAction(event -> {
            if (ship.getValue() == null || target.getValue() == null) return;
            String shipId = ship.getValue().id(), targetId = target.getValue().id();
            start.setDisable(true);
            refresh(results, state, current, () -> CompletableFuture.supplyAsync(() -> ShipRefitQuote.inspect(state, owner, shipId, targetId)),
                    quote -> start.setDisable(controller == null || !quote.ready()));
        });
        start.setOnAction(event -> {
            if (controller == null || current.get() != state || ship.getValue() == null || target.getValue() == null) return;
            start.setDisable(true);
            controller.stageTrackedCommand(new com.spaceconquest.control.command.QueueShipRefitCommand(owner, ship.getValue().id(), target.getValue().id()))
                    .whenComplete((outcome, failure) -> Platform.runLater(() -> {
                        if (current.get() != state) return;
                        results.getChildren().setAll(line(failure != null ? "Refit submission failed. Refresh and preview again."
                                : outcome == com.spaceconquest.control.command.CommandOutcome.EXECUTED ? "Paid refit order accepted."
                                : "Refit was not accepted. Refresh and preview the current requirements.", "refit-submission"));
                    }));
            results.getChildren().setAll(line("Refit request queued for the next simulation tick.", "refit-submission"));
        });
        ship.valueProperty().addListener((observable, old, selected) -> start.setDisable(true));
        target.valueProperty().addListener((observable, old, selected) -> start.setDisable(true));
        ship.valueProperty().addListener((observable, old, selected) -> clear(results)); target.valueProperty().addListener((observable, old, selected) -> clear(results));
        box.getChildren().addAll(line("Estimate changes at the ship's current yard before choosing an upgrade.", "refit-description"),
                new HBox(8, new Label("Ship:"), ship), new HBox(8, new Label("Target model:"), target, new HBox(8, preview, start)), results);
        if (state != null) state.shipConstructionOrders().stream().filter(order -> owner.equals(order.ownerEntityId()) && (order.refit() != null || order.retirement() != null))
                .forEach(order -> workRow(box, state, current, controller, owner, order));
        box.getChildren().add(ShipRetirementCard.create(state, owner, current, controller));
        var pane = new TitledPane("Ship upgrades and retirement", box); pane.setExpanded(false);
        pane.expandedProperty().addListener((observable, old, expanded) -> { if (!expanded) { results.setUserData(new Object()); start.setDisable(true); } });
        return pane;
    }
    private static void workRow(VBox box, GameState state, Supplier<GameState> current, com.spaceconquest.control.HumanController controller,
                                String owner, com.spaceconquest.engine.ship.ShipConstructionOrder order) {
        boolean refit = order.refit() != null; var cancel = new Button("Cancel work (no refund)"); cancel.setId("yard-work-cancel"); cancel.setDisable(controller == null);
        var status = line(String.format(Locale.ROOT, "%s: %s %.1f%% complete; ship unavailable until completion.",
                refit ? order.refit().shipId() : order.retirement().shipId(), refit ? "refit" : "retirement",
                Math.min(100, order.accumulatedWorkHours() * 100 / Math.max(1, order.requiredWorkHours()))), refit ? "refit-progress" : "retirement-progress");
        var details = line("Consumed materials kg: " + new TreeMap<>(order.consumedMaterialsKg()) + "; earned service fees: "
                + (order.yardContract() == null ? 0 : order.yardContract().paidFeesCredits()) + ". Cancellation preserves current ship condition and refunds nothing.", "yard-work-spent");
        cancel.setOnAction(event -> {
            if (controller == null || current.get() != state) return; cancel.setDisable(true);
            controller.stageTrackedCommand(new com.spaceconquest.control.command.CancelShipYardWorkCommand(owner, order.id()))
                    .whenComplete((outcome, failure) -> Platform.runLater(() -> { if (current.get() == state) status.setText(failure == null
                            && outcome == com.spaceconquest.control.command.CommandOutcome.EXECUTED ? "Yard work cancelled; spent inputs and fees were retained." : "Cancellation was not accepted. Refresh the view."); }));
        });
        box.getChildren().addAll(new HBox(8, status, cancel), details);
    }
    private static void clear(VBox box) { box.setUserData(new Object()); box.getChildren().clear(); }
    static void refresh(VBox box, GameState state, Supplier<GameState> current, Supplier<CompletableFuture<ShipRefitQuote>> calculation) {
        refresh(box, state, current, calculation, quote -> {});
    }
    private static void refresh(VBox box, GameState state, Supplier<GameState> current, Supplier<CompletableFuture<ShipRefitQuote>> calculation,
                                java.util.function.Consumer<ShipRefitQuote> rendered) {
        var request = new Object(); box.setUserData(request); box.getChildren().setAll(line("Calculating refit estimate...", "refit-loading"));
        calculation.get().whenComplete((quote, failure) -> Platform.runLater(() -> {
            if (box.getUserData() != request || current.get() != state) return;
            if (failure != null) box.getChildren().setAll(line("Unable to calculate this estimate. Refresh the view.", "refit-error"));
            else { render(box, quote); rendered.accept(quote); }
        }));
    }
    static void render(VBox box, ShipRefitQuote quote) {
        String status = quote.comparison() != null && quote.comparison().noChange() ? "No equipment or structural refit is needed."
                : quote.ready() ? "Current refit estimate is feasible." : "Refit preparation required.";
        box.getChildren().setAll(line(status, "refit-status"));
        if (quote.comparison() != null) {
            var changes = quote.comparison(); var counts = new EnumMap<ShipModelComparison.Kind, Long>(ShipModelComparison.Kind.class);
            changes.components().forEach(change -> counts.merge(change.kind(), 1L, Long::sum));
            box.getChildren().add(line("Retained " + counts.getOrDefault(ShipModelComparison.Kind.RETAINED, 0L) + "; replaced " + counts.getOrDefault(ShipModelComparison.Kind.REPLACED, 0L)
                    + "; added " + counts.getOrDefault(ShipModelComparison.Kind.ADDED, 0L) + "; removed " + counts.getOrDefault(ShipModelComparison.Kind.REMOVED, 0L), "refit-components"));
            box.getChildren().add(line(String.format(Locale.ROOT, "Work hours — equipment %.1f; removal and installation %.1f; structure %.1f; armor %.1f",
                    changes.equipment().workHours(), changes.installation().workHours(), changes.structure().workHours(), changes.armor().workHours()), "refit-work"));
            box.getChildren().add(line("Materials kg: " + new TreeMap<>(changes.total().materialsKg()), "refit-materials"));
            for (var change : changes.components()) {
                var item = change.target() == null ? change.source() : change.target();
                box.getChildren().add(line(change.kind().name().toLowerCase(Locale.ROOT) + ": " + item.hardware().name(), "refit-change"));
            }
        }
        box.getChildren().add(line(quote.estimatedCostCredits() == null ? "Full material cost cannot currently be funded from accessible stock."
                : String.format(Locale.ROOT, "Estimated credits — materials %.2f; service %.2f; yard operating estimate %.2f; total %.2f",
                quote.materialCostCredits(), quote.serviceFeeCredits(), quote.estimatedYardOperatingCredits(), quote.estimatedCostCredits()), "refit-cost"));
        if (Double.isFinite(quote.workDays())) box.getChildren().add(line(String.format(Locale.ROOT, "Paid-capacity estimate: %.2f work days; existing yard workload %.2f days", quote.workDays(), quote.queuedDays()), "refit-duration"));
        quote.blockers().forEach(reason -> box.getChildren().add(line(reason, "refit-blocker")));
        box.getChildren().add(line("Retained equipment keeps its damage. Removed parts have no assumed refund. Weapon supplies and charging are separate.", "refit-retention"));
    }
    private static Label line(String text, String id) { var label = new Label(text); label.setId(id); label.setWrapText(true); return label; }
}
