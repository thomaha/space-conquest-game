package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.TransferShipSuppliesCommand;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.PropulsionCatalog;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipSupplyTransfer;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import java.util.ArrayList;
import java.util.List;

/** Emergency supply controls built from the current immutable snapshot. */
final class ShipSupplyTransferCard {
    record Supply(ShipSupplyTransfer.Destination destination, String id, String label) {
        @Override public String toString() { return label; }
    }
    private ShipSupplyTransferCard() {}

    static VBox create(GameState state, Fleet fleet, ShipInstance receiver, ShipDesign design,
                       HumanController controller, Label feedback) {
        VBox box = new VBox(5);
        if (controller == null || design == null) return box;
        ComboBox<String> donor = new ComboBox<>();
        state.fleets().stream().filter(item -> ShipSupplyTransfer.coLocated(item, fleet))
                .flatMap(item -> item.ships().stream())
                .filter(ship -> !ship.id().equals(receiver.id()) && ship.ownerEntityId().equals(receiver.ownerEntityId()))
                .map(ShipInstance::id).sorted().forEach(donor.getItems()::add);
        List<Supply> supplies = supplies(design);
        if (donor.getItems().isEmpty() || supplies.isEmpty()) return box;
        donor.setValue(donor.getItems().getFirst());
        ComboBox<Supply> supply = new ComboBox<>();
        supply.getItems().addAll(supplies);
        supply.setValue(supplies.getFirst());
        ComboBox<ShipSupplyTransfer.Source> source = new ComboBox<>();
        source.getItems().addAll(ShipSupplyTransfer.Source.values());
        source.setValue(ShipSupplyTransfer.Source.CARGO);
        Spinner<Double> quantity = new Spinner<>(.001, 1e9, 1, 1);
        quantity.setEditable(true);
        Button transfer = new Button("Transfer supplies");
        transfer.setOnAction(event -> {
            Supply selected = supply.getValue();
            var command = new TransferShipSuppliesCommand(donor.getValue(), receiver.id(), source.getValue(),
                    selected.destination(), selected.id(), quantity.getValue());
            if (!command.validate(state)) {
                feedback.setText("Transfer requires compatible stock, receiver capacity and a shared site or velocity-matched rescue contact.");
                return;
            }
            controller.stageCommand(command);
            transfer.setDisable(true);
            feedback.setText("Queued supply transfer to " + receiver.id() + ". Check travel readiness after the next tick.");
        });
        box.getChildren().addAll(new Label("Emergency resupply: donor ship, supply, source and total mixture kg"),
                new HBox(6, donor, supply), new HBox(6, source, quantity, transfer));
        return box;
    }

    static List<Supply> supplies(ShipDesign design) {
        List<Supply> result = new ArrayList<>();
        var profile = design.powerProfile();
        if (profile != null) profile.fuels().entrySet().stream()
                .filter(entry -> entry.getValue().oxidizerId() == null ? profile.fissionKw() > 0 : profile.chemicalKw() > 0)
                .sorted(java.util.Map.Entry.comparingByKey()).forEach(entry -> result.add(new Supply(
                        ShipSupplyTransfer.Destination.ELECTRICAL_FUEL, entry.getKey(), "Electrical fuel: " + entry.getKey())));
        var drive = PropulsionCatalog.mainDrive(design.equippedModuleIds());
        if (drive != null) {
            result.add(new Supply(ShipSupplyTransfer.Destination.PROPELLANT, drive.moduleId(), "Propellant: " + drive.propellantLabel()));
            PropulsionCatalog.reactorFuels(drive.moduleId()).stream().filter(fuel -> fuel.kgPerPropellantKg() > 0)
                    .forEach(fuel -> result.add(new Supply(ShipSupplyTransfer.Destination.DRIVE_REACTOR,
                            fuel.materialId(), "Drive reactor: " + fuel.materialId())));
        }
        return result;
    }
}
