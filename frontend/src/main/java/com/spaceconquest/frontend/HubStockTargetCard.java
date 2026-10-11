package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.SetHubStockTargetCommand;
import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.FleetPositioning;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Snapshot-backed inventory settings; edits are queued for the next simulation tick. */
final class HubStockTargetCard {
    private HubStockTargetCard() {}

    static VBox create(GameState state, CommercialHub hub, String empireId,
                       HumanController controller, Label feedback) {
        VBox box = new VBox(6);
        if (state == null || controller == null) return box;
        String system = FleetPositioning.systemForHub(state, hub);
        if (system == null) return box;
        if (state.empires().stream().noneMatch(empire -> empire.id().equals(empireId)
                && empire.controlledSystemIds().contains(system))) return box;
        ComboBox<String> material = new ComboBox<>();
        material.setId("hub-stock-material");
        material.getItems().addAll(state.commercialHubs().stream().flatMap(item -> item.activeOrders().keySet().stream())
                .distinct().sorted().toList());
        if (material.getItems().isEmpty()) return box;
        TextField threshold = new TextField();
        TextField target = new TextField();
        threshold.setId("hub-stock-threshold");
        target.setId("hub-stock-target");
        threshold.setPrefColumnCount(10);
        target.setPrefColumnCount(10);
        Label status = new Label();
        status.setStyle("-fx-text-fill: lightcyan;");
        Runnable selectMaterial = () -> {
            var setting = hub.stockTargets().get(material.getValue());
            threshold.setText(setting == null ? "0" : Double.toString(setting.reorderThresholdKg()));
            target.setText(setting == null ? "0" : Double.toString(setting.targetStockKg()));
            status.setText(setting == null ? "Automatic stockpile policy"
                    : setting.refillRequested() ? "Refill requested" : "Waiting for reorder threshold");
        };
        material.setOnAction(event -> selectMaterial.run());
        material.getSelectionModel().selectFirst();
        selectMaterial.run();
        Button save = new Button("Set stock target");
        save.setId("hub-stock-save");
        save.setOnAction(event -> {
            try {
                var command = new SetHubStockTargetCommand(empireId, hub.id(), material.getValue(),
                        Double.parseDouble(threshold.getText().trim()), Double.parseDouble(target.getText().trim()));
                if (!command.validate(state)) {
                    feedback.setText("Use finite quantities with 0 <= reorder threshold <= target. Combined targets must fit hub storage.");
                    return;
                }
                controller.stageCommand(command);
                save.setDisable(true);
                feedback.setText("Stock target queued for the next tick at " + hub.id() + ".");
            } catch (NumberFormatException exception) {
                feedback.setText("Enter stock quantities as numbers in kilograms.");
            }
        });
        Label help = new Label("Reorder below threshold; refill toward target. Inbound cargo counts. Set both to zero to restore automatic policy.");
        help.setWrapText(true);
        help.setStyle("-fx-text-fill: lightgray;");
        Label reorderLabel = new Label("Reorder kg");
        Label targetLabel = new Label("Target kg");
        reorderLabel.setStyle("-fx-text-fill: lightgray;");
        targetLabel.setStyle("-fx-text-fill: lightgray;");
        box.getChildren().addAll(help, new HBox(6, material, status),
                new HBox(6, reorderLabel, threshold, targetLabel, target, save));
        box.getChildren().add(HubFuelStockSuggestionCard.create(state, hub, empireId, controller, feedback));
        return box;
    }
}
