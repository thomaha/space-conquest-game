package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.GameCommand;
import com.spaceconquest.control.command.MergeFleetsCommand;
import com.spaceconquest.control.command.RenameFleetCommand;
import com.spaceconquest.control.command.SplitFleetCommand;
import com.spaceconquest.control.command.TransferFleetShipsCommand;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.ShipInstance;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.StringConverter;
import java.util.List;
import java.util.UUID;

/** Membership workbench using snapshot previews and tracked tick commands. */
final class FleetOrganizationCard {
    private FleetOrganizationCard() {}

    static VBox create(GameState state, String owner, HumanController controller, Label feedback) {
        VBox box = new VBox(7);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: rgba(25, 45, 75, 0.75); -fx-background-radius: 8;");
        Label title = label("Organize fleets");
        box.getChildren().addAll(title, label("Combat and civilian ships can travel together. Fleet travel is limited by its least capable member."));
        if (state == null) return box;
        ComboBox<Fleet> source = fleets();
        source.setId("fleet-source");
        source.getItems().addAll(state.fleets().stream().filter(fleet -> owner.equals(fleet.ownerEntityId()) && !fleet.ships().isEmpty()).toList());
        if (source.getItems().isEmpty()) {
            box.getChildren().add(label("Commission a ship to create your first fleet."));
            return box;
        }
        ComboBox<Fleet> destination = fleets();
        destination.setId("fleet-destination");
        ListView<ShipInstance> ships = new ListView<>();
        ships.setId("fleet-members");
        ships.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        ships.setPrefHeight(135);
        ships.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(ShipInstance ship, boolean empty) {
                super.updateItem(ship, empty);
                setText(empty || ship == null ? null : state.shipDesigns().stream()
                        .filter(design -> design.id().equals(ship.designId())).findFirst()
                        .map(design -> design.name() + " | " + design.role() + " | " + ship.id()).orElse(ship.id()));
            }
        });
        TextField name = new TextField();
        name.setId("fleet-split-name");
        name.setPromptText("New fleet name");
        TextField renamed = new TextField();
        renamed.setId("fleet-rename-name");
        String newId = "fleet_" + UUID.randomUUID();
        Button transfer = new Button("Move selected ships");
        transfer.setId("fleet-transfer");
        Button merge = new Button("Merge into destination");
        merge.setId("fleet-merge");
        Button split = new Button("Detach selected into new fleet");
        split.setId("fleet-split");
        Button rename = new Button("Rename fleet");
        rename.setId("fleet-rename");
        Label availability = label("");
        Runnable refresh = () -> {
            Fleet selected = source.getValue(), target = destination.getValue();
            if (selected == null) return;
            List<String> ids = selected(ships);
            String targetId = target == null ? null : target.id();
            String splitProblem = new SplitFleetCommand(owner, selected.id(), ids, newId, name.getText()).problem(state);
            String moveProblem = new TransferFleetShipsCommand(owner, selected.id(), targetId, ids).problem(state);
            String mergeProblem = new MergeFleetsCommand(owner, selected.id(), targetId).problem(state);
            transfer.setDisable(controller == null || moveProblem != null);
            split.setDisable(controller == null || splitProblem != null);
            merge.setDisable(controller == null || mergeProblem != null);
            rename.setDisable(controller == null || !new RenameFleetCommand(owner, selected.id(), renamed.getText()).validate(state));
            availability.setText("Selected: " + ids.size() + "/" + selected.ships().size() + " ships. "
                    + (splitProblem == null ? "Can detach selected ships. " : splitProblem + " ")
                    + (moveProblem == null ? "Can move selected ships to destination." : moveProblem));
        };
        Runnable populate = () -> {
            Fleet selected = source.getValue();
            if (selected == null) return;
            ships.getItems().setAll(selected.ships());
            ships.getSelectionModel().selectFirst();
            destination.getItems().setAll(source.getItems().stream().filter(fleet -> !fleet.id().equals(selected.id())).toList());
            destination.setValue(destination.getItems().isEmpty() ? null : destination.getItems().getFirst());
            name.setText(selected.name() + " detachment");
            renamed.setText(selected.name());
            refresh.run();
        };
        source.valueProperty().addListener((observable, old, selected) -> populate.run());
        destination.valueProperty().addListener((observable, old, selected) -> refresh.run());
        ships.getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener<ShipInstance>) change -> refresh.run());
        name.textProperty().addListener((observable, old, value) -> refresh.run());
        renamed.textProperty().addListener((observable, old, value) -> refresh.run());
        List<Button> actions = List.of(transfer, merge, split, rename);
        transfer.setOnAction(event -> stage(new TransferFleetShipsCommand(owner, source.getValue().id(), destination.getValue().id(),
                selected(ships)), "move selected ships", state, controller, feedback, actions));
        merge.setOnAction(event -> stage(new MergeFleetsCommand(owner, source.getValue().id(), destination.getValue().id()),
                "merge fleets", state, controller, feedback, actions));
        split.setOnAction(event -> stage(new SplitFleetCommand(owner, source.getValue().id(), selected(ships), newId, name.getText()),
                "detach selected ships", state, controller, feedback, actions));
        rename.setOnAction(event -> stage(new RenameFleetCommand(owner, source.getValue().id(), renamed.getText()),
                "rename fleet", state, controller, feedback, actions));
        Button selectAll = new Button("Select all ships");
        selectAll.setOnAction(event -> ships.getSelectionModel().selectAll());
        box.getChildren().addAll(new HBox(7, label("Source fleet:"), source, renamed, rename),
                label("Select ships below (Ctrl or Shift selects multiple). Removing a ship creates a separate fleet."), ships, selectAll,
                new HBox(7, label("Destination fleet:"), destination, transfer, merge), new HBox(7, name, split), availability);
        source.setValue(source.getItems().getFirst());
        return box;
    }

    private static List<String> selected(ListView<ShipInstance> ships) {
        return ships.getSelectionModel().getSelectedItems().stream().map(ShipInstance::id).toList();
    }
    private static void stage(GameCommand command, String action, GameState state, HumanController controller,
                              Label feedback, List<Button> buttons) {
        if (controller == null || !command.validate(state)) {
            feedback.setText("Fleet organization unavailable. Refresh the fleet selection and check location, ownership and commitments.");
            return;
        }
        buttons.forEach(button -> button.setDisable(true));
        feedback.setText("Queued request to " + action + ".");
        controller.stageTrackedCommand(command).whenComplete((outcome, failure) -> Platform.runLater(() -> {
            feedback.setText(failure != null ? "Fleet organization failed during execution."
                    : switch (outcome) {
                        case EXECUTED -> "Completed request to " + action + ".";
                        case REJECTED -> "Fleet organization rejected: membership, ownership, location or commitments changed before execution.";
                        case CANCELLED -> "Fleet organization request cancelled.";
                    });
        }));
    }
    private static ComboBox<Fleet> fleets() {
        ComboBox<Fleet> combo = new ComboBox<>();
        combo.setConverter(new StringConverter<>() {
            @Override public String toString(Fleet fleet) { return fleet == null ? "" : fleet.name() + " (" + fleet.ships().size() + " ships)"; }
            @Override public Fleet fromString(String text) { return null; }
        });
        return combo;
    }
    private static Label label(String text) {
        Label label = new Label(text);
        label.setTextFill(Color.LIGHTCYAN);
        label.setWrapText(true);
        return label;
    }
}
