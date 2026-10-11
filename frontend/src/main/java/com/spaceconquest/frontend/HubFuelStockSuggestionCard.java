package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.SetHubStockTargetsCommand;
import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.logistics.HubFuelStockSuggestions;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** Editable cosmetic planning inputs; a reviewed mixture is queued as one atomic command. */
final class HubFuelStockSuggestionCard {
    private HubFuelStockSuggestionCard() {}

    static VBox create(GameState state, CommercialHub hub, String empireId, HumanController controller, Label feedback) {
        var box = new VBox(6);
        var fuel = new ComboBox<HubFuelStockSuggestions.Fuel>();
        fuel.setId("hub-fuel-plan-mixture");
        var known = state.commercialHubs().stream().flatMap(item -> item.activeOrders().keySet().stream())
                .collect(java.util.stream.Collectors.toSet());
        fuel.getItems().addAll(HubFuelStockSuggestions.fuels().stream()
                .filter(item -> known.containsAll(item.fractions().keySet())).toList());
        if (fuel.getItems().isEmpty()) return box;
        fuel.getSelectionModel().selectFirst();
        TextField departure = input("hub-fuel-plan-departure", "0");
        TextField daily = input("hub-fuel-plan-daily", "0");
        TextField cycle = input("hub-fuel-plan-cycle", "0");
        TextField safety = input("hub-fuel-plan-safety", "2");
        Label details = label("Estimate inputs from the snapshot or enter your own. Quantities are total fuel mixture kg.");
        Label result = label("Preview suggested ingredient targets before queuing.");
        result.setId("hub-fuel-plan-result");
        Button estimate = new Button("Estimate inputs");
        estimate.setId("hub-fuel-plan-estimate");
        Button preview = new Button("Preview suggestion");
        preview.setId("hub-fuel-plan-preview");
        Button apply = new Button("Queue suggested targets");
        apply.setId("hub-fuel-plan-apply");
        apply.setDisable(true);
        long[] version = {0};
        SetHubStockTargetsCommand[] reviewed = {null};
        Runnable invalidate = () -> {
            version[0]++;
            reviewed[0] = null;
            apply.setDisable(true);
            result.setText("Inputs changed; preview the suggestion again.");
            if (estimate.isDisabled()) details.setText("Inputs changed; the previous estimate was discarded. Keep your values or estimate again.");
        };
        for (var field : new TextField[]{departure, daily, cycle, safety})
            field.textProperty().addListener((observable, oldValue, newValue) -> invalidate.run());
        fuel.setOnAction(event -> { invalidate.run(); details.setText("Fuel changed. Estimate inputs again or enter your own values."); });
        estimate.setOnAction(event -> {
            invalidate.run();
            long request = version[0];
            var selected = fuel.getValue();
            estimate.setDisable(true);
            details.setText("Estimating fleet refills and supplier turnaround...");
            CompletableFuture.supplyAsync(() -> HubFuelStockSuggestions.estimate(state, hub, selected))
                    .whenComplete((seed, failure) -> Platform.runLater(() -> {
                        estimate.setDisable(false);
                        if (request != version[0]) return;
                        if (failure != null) { details.setText("Estimate unavailable. Enter planning inputs manually."); return; }
                        departure.setText(Double.toString(seed.departureKg()));
                        daily.setText(Double.toString(seed.dailyKg()));
                        cycle.setText(Double.toString(seed.cycleDays()));
                        details.setText(seed.explanation());
                    }));
        });
        preview.setOnAction(event -> {
            invalidate.run();
            try {
                var inputs = new HubFuelStockSuggestions.Inputs(number(departure), number(daily), number(cycle), number(safety));
                var suggestion = HubFuelStockSuggestions.calculate(fuel.getValue(), inputs);
                var command = new SetHubStockTargetsCommand(empireId, hub.id(), suggestion.targets());
                result.setText(describe(suggestion) + (command.validate(state) ? ""
                        : "\nCannot queue: combined targets must fit hub storage and all ingredients must be known at a controlled hub."));
                if (command.validate(state)) { reviewed[0] = command; apply.setDisable(false); }
            } catch (IllegalArgumentException exception) { result.setText(exception.getMessage()); }
        });
        apply.setOnAction(event -> {
            if (reviewed[0] == null) return;
            controller.stageCommand(reviewed[0]);
            reviewed[0] = null;
            apply.setDisable(true);
            feedback.setText("Suggested fuel ingredient targets queued together for the next tick at " + hub.id() + ".");
        });
        box.getChildren().addAll(label("Suggested fuel buffers"), new HBox(6, fuel, estimate), details,
                new HBox(6, label("Departure kg"), departure, label("Demand kg/day"), daily),
                new HBox(6, label("Supplier cycle days"), cycle, label("Safety days"), safety),
                new HBox(6, preview, apply), result);
        return box;
    }

    private static String describe(HubFuelStockSuggestions.Suggestion suggestion) {
        String ingredients = suggestion.targets().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                .map(item -> String.format(Locale.ROOT, "%s: reorder %.3f kg / target %.3f kg", item.getKey(),
                        item.getValue().reorderThresholdKg(), item.getValue().targetStockKg()))
                .collect(java.util.stream.Collectors.joining("\n"));
        return "Reorder = departure + demand x (supplier cycle + safety). Target adds one cycle of demand or one departure, whichever is larger.\n"
                + ingredients + "\nPlanning estimate; supplier stock, delivery capacity, cash and safe departures are still required.";
    }
    private static double number(TextField field) { return Double.parseDouble(field.getText().trim()); }
    private static TextField input(String id, String value) {
        var field = new TextField(value);
        field.setId(id);
        field.setPrefColumnCount(8);
        return field;
    }
    private static Label label(String text) {
        var label = new Label(text);
        label.setWrapText(true);
        label.setStyle("-fx-text-fill: lightgray;");
        return label;
    }
}
