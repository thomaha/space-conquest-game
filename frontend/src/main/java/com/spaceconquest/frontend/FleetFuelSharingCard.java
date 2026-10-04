package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.ShareFleetFuelCommand;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetFuelSharing;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipSupplyTransfer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Spinner;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.StringConverter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Calculates distribution off the FX thread and stages the reviewed immutable allocation. */
final class FleetFuelSharingCard {
    private final GameState state;
    private final String owner;
    private final HumanController controller;
    private final Label feedback;
    private final ComboBox<Fleet> fleet = new ComboBox<>();
    private final ListView<ShipInstance> donors = ships("fuel-suppliers");
    private final ListView<ShipInstance> receivers = ships("fuel-receivers");
    private final ComboBox<ShipSupplyTransferCard.Supply> supply = new ComboBox<>();
    private final ComboBox<ShipSupplyTransfer.Source> source = new ComboBox<>();
    private final Spinner<Integer> target = new Spinner<>(1, 100, 100, 5);
    private final Spinner<Integer> reserve = new Spinner<>(0, 100, 25, 5);
    private final Label preview = label("");
    private final Button share = new Button("Share fuel");
    private long generation;
    private ShareFleetFuelCommand reviewed;

    private FleetFuelSharingCard(GameState state, String owner, HumanController controller, Label feedback) {
        this.state = state;
        this.owner = owner;
        this.controller = controller;
        this.feedback = feedback;
    }
    static VBox create(GameState state, String owner, HumanController controller, Label feedback) {
        return new FleetFuelSharingCard(state, owner, controller, feedback).build();
    }
    private VBox build() {
        VBox box = new VBox(7);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: rgba(25, 45, 75, 0.75); -fx-background-radius: 8;");
        box.getChildren().add(label("Share fleet fuel"));
        if (state == null) return box;
        fleet.setId("fuel-receiver-fleet");
        fleet.setConverter(new StringConverter<>() {
            @Override public String toString(Fleet item) { return item == null ? "" : item.name(); }
            @Override public Fleet fromString(String text) { return null; }
        });
        fleet.getItems().addAll(state.fleets().stream().filter(item -> Objects.equals(owner, item.ownerEntityId()) && !item.ships().isEmpty()).toList());
        if (fleet.getItems().isEmpty()) {
            box.getChildren().add(label("Commission a ship to create your first fleet."));
            return box;
        }
        source.setId("fuel-share-source");
        source.getItems().addAll(ShipSupplyTransfer.Source.values());
        source.setValue(ShipSupplyTransfer.Source.CARGO);
        supply.setId("fuel-share-supply");
        target.setId("fuel-target-percent");
        reserve.setId("fuel-reserve-percent");
        share.setId("fleet-share-fuel");
        share.setDisable(true);
        preview.setId("fuel-share-preview");
        fleet.valueProperty().addListener((observable, old, selected) -> populate());
        donors.getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener<ShipInstance>) change -> refresh());
        receivers.getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener<ShipInstance>) change -> refresh());
        supply.valueProperty().addListener((observable, old, selected) -> refresh());
        source.valueProperty().addListener((observable, old, selected) -> refresh());
        target.valueProperty().addListener((observable, old, selected) -> refresh());
        reserve.valueProperty().addListener((observable, old, selected) -> refresh());
        share.setOnAction(event -> stage(box));
        box.getChildren().addAll(new HBox(7, label("Receiving fleet:"), fleet),
                label("Select suppliers and receivers (Ctrl or Shift selects multiple). A ship cannot be both."),
                new HBox(7, new VBox(4, label("Suppliers at this site or coasting contact"), donors),
                        new VBox(4, label("Receiving fleet members"), receivers)),
                new HBox(7, source, supply), new HBox(7, label("Receiver fill %:"), target, label("Supplier propellant reserve %:"), reserve),
                label("Suppliers retain 48 hours of essential and cargo electricity and reactor feed for their remaining propellant. A percentage reserve does not guarantee a return journey."),
                preview, share);
        fleet.setValue(fleet.getItems().getFirst());
        return box;
    }
    private void populate() {
        Fleet selected = fleet.getValue();
        if (selected == null) return;
        donors.getItems().setAll(state.fleets().stream().filter(item -> ShipSupplyTransfer.coLocated(item, selected))
                .flatMap(item -> item.ships().stream()).filter(ship -> Objects.equals(owner, ship.ownerEntityId())).toList());
        receivers.getItems().setAll(selected.ships());
        supply.getItems().setAll(selected.ships().stream().map(ship -> state.shipDesigns().stream()
                        .filter(design -> design.id().equals(ship.designId())).findFirst().orElse(null))
                .filter(Objects::nonNull).flatMap(design -> ShipSupplyTransferCard.supplies(design).stream()).distinct().toList());
        supply.setValue(supply.getItems().isEmpty() ? null : supply.getItems().getFirst());
        donors.getSelectionModel().selectFirst();
        receivers.getSelectionModel().selectAll();
        if (!donors.getSelectionModel().isEmpty()) {
            String donorId = donors.getSelectionModel().getSelectedItem().id();
            for (int index = 0; index < receivers.getItems().size(); index++)
                if (receivers.getItems().get(index).id().equals(donorId)) receivers.getSelectionModel().clearSelection(index);
        }
        refresh();
    }
    private void refresh() {
        long version = ++generation;
        reviewed = null;
        share.setDisable(true);
        var selected = supply.getValue();
        if (selected == null || fleet.getValue() == null) {
            preview.setText("Select a receiving fleet with compatible fuel equipment.");
            return;
        }
        var request = new FleetFuelSharing.Request(owner, fleet.getValue().id(), ids(donors), ids(receivers), source.getValue(),
                selected.destination(), selected.id(), target.getValue() / 100.0, reserve.getValue() / 100.0);
        preview.setText("Calculating fuel distribution…");
        CompletableFuture.supplyAsync(() -> FleetFuelSharing.preview(state, request)).whenComplete((plan, failure) -> Platform.runLater(() -> {
            if (version != generation) return;
            if (failure != null) {
                preview.setText("Fuel preview failed. Refresh the fleet selection.");
                return;
            }
            preview.setText(describe(plan));
            reviewed = new ShareFleetFuelCommand(request, plan.transfers());
            share.setDisable(controller == null || !plan.available());
        }));
    }
    private void stage(VBox box) {
        if (reviewed == null || controller == null || share.isDisabled()) return;
        ++generation;
        box.setDisable(true);
        feedback.setText("Queued fleet fuel sharing.");
        controller.stageTrackedCommand(reviewed).whenComplete((outcome, failure) -> Platform.runLater(() -> feedback.setText(
                failure != null ? "Fuel sharing failed during execution." : switch (outcome) {
                    case EXECUTED -> "Fleet fuel sharing completed. Check the refreshed travel readiness before departure or recovery.";
                    case REJECTED -> "Fuel sharing rejected: stock, location, membership or protected reserves changed. Refresh the preview.";
                    case CANCELLED -> "Fleet fuel sharing cancelled.";
                })));
    }
    private static String describe(FleetFuelSharing.Plan plan) {
        StringBuilder text = new StringBuilder(String.format(Locale.ROOT, "Total transfer: %.6f kg", plan.totalKg()));
        for (var transfer : plan.transfers()) text.append(String.format(Locale.ROOT, "\n%s → %s: %.6f kg",
                transfer.donorShipId(), transfer.receiverShipId(), transfer.quantityKg()));
        for (var balance : plan.balances()) text.append(String.format(Locale.ROOT,
                "\n%s %s remaining: propulsion %.6f kg, electrical fuel %.6f kg, supply tanks %.6f kg, cargo %.6f kg%s",
                balance.supplier() ? "Supplier" : "Receiver", balance.shipId(), balance.propellantKg(), balance.electricalFuelKg(),
                balance.supplyFuelKg(), balance.cargoKg(), balance.shortfallKg() > 1e-8 ? String.format(Locale.ROOT, "; target shortfall %.6f kg", balance.shortfallKg()) : ""));
        for (String explanation : plan.explanations()) text.append("\n").append(explanation);
        return text.toString();
    }
    private static List<String> ids(ListView<ShipInstance> list) {
        return list.getSelectionModel().getSelectedItems().stream().map(ShipInstance::id).toList();
    }
    private static ListView<ShipInstance> ships(String id) {
        ListView<ShipInstance> list = new ListView<>();
        list.setId(id);
        list.setPrefSize(330, 125);
        list.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        list.setCellFactory(view -> new ListCell<>() {
            @Override protected void updateItem(ShipInstance ship, boolean empty) {
                super.updateItem(ship, empty);
                setText(empty || ship == null ? null : ship.id() + " | main tank " + String.format(Locale.ROOT, "%.1f kg", ship.currentFuelKg()));
            }
        });
        return list;
    }
    private static Label label(String text) {
        Label label = new Label(text);
        label.setTextFill(Color.LIGHTCYAN);
        label.setWrapText(true);
        return label;
    }
}
