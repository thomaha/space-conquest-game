package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.*;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import java.util.List;

/** Dedicated supply inventory, paid loading and explicit next-voyage coasting orders. */
final class ShipSupplyStorageCard {
    private ShipSupplyStorageCard() {}
    static VBox create(GameState state, Fleet fleet, ShipInstance ship, ShipDesign design, HumanController controller, Label feedback) {
        VBox box = new VBox(5);
        if (design == null || ShipSupplyCatalog.totalCapacity(design) <= 0) return box;
        Label stock = new Label("Dedicated supply tanks (" + ShipSupplyCatalog.totalCapacity(design) + " kg): " + ship.supplyState().materialsKg()
                + " | Pump rate: " + ShipSupplyCatalog.transferKgPerHour(design) + " kg/h");
        stock.setWrapText(true);
        stock.setId("supply-inventory");
        box.getChildren().add(stock);
        if (!ship.supplyState().preservation().lostKgToday().isEmpty()) box.getChildren().add(new Label("Supply boil-off last tick (kg): "
                + ship.supplyState().preservation().lostKgToday()));
        Label status = new Label(ship.supplyState().outcome());
        status.setWrapText(true);
        box.getChildren().add(status);
        if (controller == null) return box;
        if (ship.supplyState().order() != null) {
            for (var order : ship.supplyState().orders()) {
                Button remove = new Button("Cancel delivery");
                remove.setOnAction(event -> stage(new CancelFleetSupplyCommand(ship.id(), order), box, controller, feedback));
                box.getChildren().add(new HBox(6, new Label("Reserved " + order.destination() + ": " + order.quantityKg()
                        + " kg " + order.fuelId() + " to " + order.receiverShipId() + ", coast delay " + order.coastDelayHours() + " h."), remove));
            }
            Button cancel = new Button("Cancel all scheduled supplies");
            cancel.setOnAction(event -> stage(new CancelFleetSupplyCommand(ship.id()), box, controller, feedback));
            box.getChildren().add(cancel);
        }
        if (fleet.hasInterstellarOrder() || fleet.location().inTransit()) return box;
        ComboBox<String> hub = new ComboBox<>();
        state.commercialHubs().stream().map(item -> item.entityId()).forEach(hub.getItems()::add);
        String siteId = fleet.location().current().entityId();
        if (hub.getItems().contains(siteId)) hub.setValue(siteId);
        else state.orbitalStations().stream().filter(station -> station.id().equals(siteId)).map(station -> station.planetOrbitId())
                .filter(hub.getItems()::contains).findFirst().ifPresent(hub::setValue);
        ComboBox<String> material = new ComboBox<>();
        material.getItems().addAll(ShipSupplyCatalog.materials(design));
        material.setValue(material.getItems().getFirst());
        Spinner<Double> kg = new Spinner<>(.001, 1e9, 1000.0, 100.0);
        kg.setEditable(true);
        Button buy = new Button("Buy bulk supply fuel");
        buy.setId("buy-supply-fuel");
        buy.setDisable(hub.getValue() == null || ship.supplyState().order() != null);
        hub.valueProperty().addListener((observable, old, selected) -> buy.setDisable(selected == null || ship.supplyState().order() != null));
        buy.setOnAction(event -> stage(new BuyShipSupplyFuelCommand(ship.id(), hub.getValue(), material.getValue(), kg.getValue()), box, controller, feedback));
        box.getChildren().add(new HBox(6, hub, material, kg, buy));
        if (fleet.location().isAt(FleetLocation.Site.deepSpace()) && ShipSupplyCatalog.transferKgPerHour(design) > 0)
            box.getChildren().add(schedule(fleet, ship, state, controller, feedback));
        else box.getChildren().add(new Label("Move the tanker into system space to schedule coasting supply before departure."));
        return box;
    }
    private static VBox schedule(Fleet fleet, ShipInstance ship, GameState state, HumanController controller, Label feedback) {
        VBox box = new VBox(5);
        ComboBox<String> receiver = new ComboBox<>();
        receiver.setId("scheduled-supply-receiver");
        fleet.ships().stream().filter(item -> !item.id().equals(ship.id())).map(ShipInstance::id).forEach(receiver.getItems()::add);
        ComboBox<ShipSupplyTransferCard.Supply> fuel = new ComboBox<>();
        fuel.setId("scheduled-supply-fuel");
        Spinner<Double> kg = new Spinner<>(.001, 1e9, 1.0, 1.0);
        kg.setId("scheduled-supply-kg");
        kg.setEditable(true);
        Spinner<Double> delay = new Spinner<>(0.0, 1e9, 0.0, 1.0);
        delay.setId("scheduled-supply-delay");
        delay.setEditable(true);
        receiver.valueProperty().addListener((observable, old, selected) -> {
            var target = fleet.ships().stream().filter(item -> item.id().equals(selected)).findFirst().orElse(null);
            var design = target == null ? null : FleetSupplySimulation.design(state, target);
            List<ShipSupplyTransferCard.Supply> choices = design == null ? List.of() : ShipSupplyTransferCard.supplies(design).stream()
                    .filter(item -> (item.destination() == ShipSupplyTransfer.Destination.PROPELLANT
                            ? PropulsionCatalog.drive(item.id()).propellantMaterials(1)
                            : item.destination() == ShipSupplyTransfer.Destination.DRIVE_REACTOR ? java.util.Map.of(item.id(), 1.0)
                            : design.powerProfile().fuels().get(item.id()).materials(1)).keySet().stream()
                            .allMatch(id -> ship.supplyState().materialsKg().getOrDefault(id, 0.0) > 0)).toList();
            fuel.getItems().setAll(choices);
            fuel.setValue(choices.isEmpty() ? null : choices.getFirst());
        });
        Button schedule = new Button("Schedule coasting supply");
        schedule.setId("schedule-fleet-supply");
        Runnable refresh = () -> schedule.setDisable(fuel.getValue() == null || !new ScheduleFleetSupplyCommand(ship.id(), receiver.getValue(),
                fuel.getValue().id(), kg.getValue(), delay.getValue(), fuel.getValue().destination()).validate(state));
        fuel.valueProperty().addListener((observable, old, selected) -> refresh.run());
        kg.valueProperty().addListener((observable, old, selected) -> refresh.run());
        delay.valueProperty().addListener((observable, old, selected) -> refresh.run());
        schedule.setDisable(true);
        schedule.setOnAction(event -> stage(new ScheduleFleetSupplyCommand(ship.id(), receiver.getValue(), fuel.getValue().id(), kg.getValue(), delay.getValue(),
                fuel.getValue().destination()),
                box, controller, feedback));
        box.getChildren().addAll(new Label("Next sublight voyage: receiver, supply type, mixture kg and delay after the first coast begins (hours)."),
                new HBox(6, receiver, fuel, kg, delay, schedule),
                new Label("Add several deliveries with separate pump intervals. Stock is reserved across the schedule; each refill checks the remaining journey."));
        if (!receiver.getItems().isEmpty()) receiver.setValue(receiver.getItems().getFirst());
        return box;
    }
    private static void stage(GameCommand command, VBox box, HumanController controller, Label feedback) {
        box.setDisable(true);
        feedback.setText("Queued supply request.");
        controller.stageTrackedCommand(command).whenComplete((outcome, failure) -> Platform.runLater(() -> feedback.setText(
                failure != null ? "Supply request failed." : switch (outcome) {
                    case EXECUTED -> "Supply request completed. Refresh the departure preview before travel.";
                    case REJECTED -> "Supply request rejected: check stock, capacity, location, ownership and existing commitments.";
                    case CANCELLED -> "Supply request cancelled.";
                })));
    }
}
