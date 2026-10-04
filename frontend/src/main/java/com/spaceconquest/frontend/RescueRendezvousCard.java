package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.RescueFleetCommand;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.RescueOrder;
import com.spaceconquest.engine.ship.RescueRendezvous;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipSupplyTransfer;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Snapshot-only rescue request controls for a ship on an interrupted sublight crossing. */
final class RescueRendezvousCard {
    private record Donor(String fleetId, String shipId, String label) {
        @Override public String toString() { return label; }
    }
    private RescueRendezvousCard() {}

    static VBox create(GameState state, Fleet fleet, ShipInstance receiver, ShipDesign design,
                       HumanController controller, Label feedback) {
        VBox box = new VBox(5);
        if (controller == null || design == null || !RescueRendezvous.drifting(fleet)) return box;
        ComboBox<Donor> donor = new ComboBox<>();
        state.fleets().stream().filter(item -> !item.id().equals(fleet.id())
                && item.ownerEntityId().equals(fleet.ownerEntityId()) && item.currentSystemId().equals(fleet.currentSystemId())
                && (ShipSupplyTransfer.stationary(item) || RescueRendezvous.drifting(item)))
                .forEach(item -> item.ships().stream().filter(ship -> ship.ownerEntityId().equals(fleet.ownerEntityId()))
                        .forEach(ship -> donor.getItems().add(new Donor(item.id(), ship.id(), item.name() + ": " + ship.id()))));
        var supplies = ShipSupplyTransferCard.supplies(design);
        if (donor.getItems().isEmpty() || supplies.isEmpty()) return box;
        donor.setValue(donor.getItems().getFirst());
        ComboBox<ShipSupplyTransferCard.Supply> supply = new ComboBox<>();
        supply.getItems().addAll(supplies);
        supply.setValue(supplies.getFirst());
        ComboBox<ShipSupplyTransfer.Source> source = new ComboBox<>();
        source.getItems().addAll(ShipSupplyTransfer.Source.values());
        source.setValue(ShipSupplyTransfer.Source.CARGO);
        Spinner<Double> quantity = new Spinner<>(.001, 1e9, 1, 1);
        quantity.setEditable(true);
        Button launch = new Button("Launch rescue");
        launch.setOnAction(event -> {
            var selected = supply.getValue();
            var vessel = donor.getValue();
            var order = new RescueOrder(fleet.id(), vessel.shipId(), receiver.id(), source.getValue(),
                    selected.destination(), selected.id(), quantity.getValue());
            var command = new RescueFleetCommand(vessel.fleetId(), order);
            var preview = command.preview(state);
            if (preview == null) {
                feedback.setText("Rescue needs system-space departure or drift behind this ship, compatible supplies, propellant and electrical endurance.");
                return;
            }
            controller.stageCommand(command);
            launch.setDisable(true);
            feedback.setText(String.format(java.util.Locale.ROOT,
                    "Queued rescue: %.2f days to velocity-matched contact. Supply transfer is checked again on arrival.",
                    preview.trajectory().totalSeconds() / 86400));
        });
        box.getChildren().addAll(new Label("Rescue: donor fleet and ship, supply, source and total mixture kg"),
                new HBox(6, donor, supply), new HBox(6, source, quantity, launch));
        return box;
    }
}
