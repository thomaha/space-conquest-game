package com.spaceconquest.frontend;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.logistics.HubFuelCoverage;
import com.spaceconquest.engine.logistics.HubFuelStockSuggestions;
import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

/** Read-only coverage from the injected snapshot; background responses are tied to the selected fuel. */
final class HubFuelCoverageCard {
    private HubFuelCoverageCard() {}

    static VBox create(GameState state, CommercialHub hub, String selectedId, Consumer<String> selected) {
        return create(state, hub, selectedId, selected, ForkJoinPool.commonPool());
    }

    static VBox create(GameState state, CommercialHub hub, String selectedId, Consumer<String> selected, Executor executor) {
        var box = new VBox(6);
        box.setId("hub-fuel-coverage");
        if (state == null) return box;
        var fuel = new ComboBox<HubFuelStockSuggestions.Fuel>();
        fuel.setId("hub-fuel-coverage-mixture");
        fuel.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(HubFuelStockSuggestions.Fuel value) { return value == null ? "" : readable(value.label()); }
            @Override public HubFuelStockSuggestions.Fuel fromString(String value) { return null; }
        });
        fuel.getItems().addAll(HubFuelStockSuggestions.fuels().stream().filter(item -> item.fractions().keySet().stream()
                .anyMatch(id -> hub.activeOrders().containsKey(id) || hub.stockTargets().containsKey(id))).toList());
        if (fuel.getItems().isEmpty()) return box;
        var details = label("Checking fuel coverage...");
        details.setId("hub-fuel-coverage-details");
        var warnings = label("");
        warnings.setId("hub-fuel-coverage-warnings");
        warnings.setStyle("-fx-text-fill: salmon;");
        long[] version = {0};
        Runnable refresh = () -> {
            long request = ++version[0];
            var chosen = fuel.getValue();
            selected.accept(chosen.id());
            details.setText("Checking fuel coverage...");
            warnings.setText("");
            CompletableFuture.supplyAsync(() -> HubFuelCoverage.inspect(state, hub, chosen), executor).whenComplete((coverage, failure) ->
                    Platform.runLater(() -> {
                        if (request != version[0]) return;
                        details.setText(failure == null ? describe(coverage) : "Fuel coverage unavailable. Refresh the hub.");
                        warnings.setText(failure == null ? readable(String.join("\n", coverage.warnings())) : "");
                    }));
        };
        fuel.setValue(fuel.getItems().stream().filter(item -> item.id().equals(selectedId)).findFirst()
                .orElseGet(() -> fuel.getItems().stream().max(java.util.Comparator.comparingLong(item ->
                        item.fractions().keySet().stream().filter(hub.stockTargets()::containsKey).count())).orElseThrow()));
        fuel.valueProperty().addListener((observable, oldValue, newValue) -> refresh.run());
        box.getChildren().addAll(label("Fuel coverage"), fuel, details, warnings,
                label("Inbound cargo is paid freight already assigned here. Source stock and routes do not guarantee delivery; funding, capacity and safe travel still apply."));
        refresh.run();
        return box;
    }

    static String describe(HubFuelCoverage.Coverage coverage) {
        var text = new StringBuilder(String.format(Locale.ROOT,
                "Usable on hand: %,.3f kg | With paid inbound: %,.3f kg\nLimiting ingredients on hand: %s\n",
                coverage.usableKg(), coverage.positionUsableKg(), String.join(", ", coverage.limitingIngredients())));
        for (var item : coverage.ingredients()) {
            text.append(String.format(Locale.ROOT, "%s: on hand %,.3f kg | Paid inbound %,.3f kg\n",
                    item.materialId(), item.onHandKg(), item.inboundKg()));
            text.append(item.configured() ? String.format(Locale.ROOT, "  Reorder %,.3f kg | Target %,.3f kg | %s\n",
                    item.reorderKg(), item.targetKg(), item.requestedKg() > 1e-6 ? "Replenishment needed"
                            : item.refillRequested() && item.onHandKg() < item.targetKg() ? "Awaiting paid inbound" : "No outstanding request")
                    : String.format(Locale.ROOT, "  Automatic stockpile policy | Target %,.3f kg\n", item.targetKg()));
            text.append(String.format(Locale.ROOT, "  Outstanding request %,.3f kg | Known accessible source stock %,.3f kg\n",
                    item.requestedKg(), item.sourceStockKg()));
            var history = item.history();
            text.append(history.observedDays() == 0 ? "  No completed demand history\n"
                    : String.format(Locale.ROOT, "  Rolling withdrawals %,.3f kg/day | Deliveries %,.3f kg/day | %d observed days | %d stockout days\n",
                    history.dailyWithdrawalKg(), history.dailyDeliveryKg(), history.observedDays(), history.stockoutDays()));
        }
        var routes = coverage.suppliers();
        text.append("History: last 30 completed days; withdrawals include local use and outbound freight.\n");
        text.append(String.format(Locale.ROOT, "Currently directed supplier routes: %d | Other potential roaming routes: %d | Unavailable routes: %d",
                routes.directedRoutes(), routes.roamingRoutes(), routes.unavailableRoutes()));
        return readable(text.toString());
    }

    private static String readable(String text) { return text.replace('_', ' ').replace("rp1 kerosene", "RP-1 kerosene"); }

    private static Label label(String text) {
        var label = new Label(text);
        label.setWrapText(true);
        label.setStyle("-fx-text-fill: lightcyan;");
        return label;
    }
}
