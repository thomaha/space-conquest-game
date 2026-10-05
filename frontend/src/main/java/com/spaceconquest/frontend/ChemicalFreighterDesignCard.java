package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.DesignShipCommand;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.ChemicalFreighterCatalog;
import com.spaceconquest.engine.ship.PropulsionCatalog;
import com.spaceconquest.engine.ship.ShipBlueprintFactory;
import com.spaceconquest.engine.ship.ShipComponentCatalog;
import com.spaceconquest.engine.ship.ShipDesignSpecification;
import com.spaceconquest.engine.ship.ShipRole;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Orbital chemical freighter preset; previews are asynchronous and registration remains tick-owned. */
final class ChemicalFreighterDesignCard {
    private ChemicalFreighterDesignCard() {}

    static VBox create(GameState state, String owner, HumanController controller, Label feedback) {
        var box = new VBox(6);
        box.getChildren().add(new Label("Small-hold chemical freighter"));
        if (state == null) return box;
        var technologies = state.empires().stream().filter(empire -> empire.id().equals(owner))
                .map(empire -> empire.unlockedTechIds()).findFirst().orElse(List.of());
        var drive = new ComboBox<String>();
        drive.setId("chemical-freighter-drive");
        drive.getItems().addAll(ChemicalFreighterCatalog.DRIVES.stream()
                .filter(id -> PropulsionCatalog.researched(List.of(id), technologies)
                        && ShipComponentCatalog.powerResearched(ChemicalFreighterCatalog.modules(id), technologies)
                        && ChemicalFreighterCatalog.researched(ChemicalFreighterCatalog.modules(id), technologies)).toList());
        var name = new TextField("Orbital chemical freighter");
        var preview = new Label("Research solar power, industrial production and a chemical drive.");
        preview.setId("chemical-freighter-preview");
        preview.setWrapText(true);
        var register = new Button("Register chemical freighter blueprint");
        register.setId("register-chemical-freighter");
        register.setDisable(true);
        String id = "freighter_" + UUID.randomUUID();
        long[] version = {0};
        Runnable refresh = () -> {
            long current = ++version[0];
            register.setDisable(true);
            if (drive.getValue() == null) return;
            var spec = specification(id, name.getText(), owner, drive.getValue());
            preview.setText("Calculating freighter blueprint...");
            CompletableFuture.supplyAsync(() -> ShipBlueprintFactory.evaluate(state, spec)).whenComplete((result, failure) -> Platform.runLater(() -> {
                if (current != version[0]) return;
                if (failure != null || !result.valid()) {
                    preview.setText(failure == null ? String.join("; ", result.errors()) : "Freighter preview failed.");
                    return;
                }
                preview.setText("2,500 kg mixed cargo | 120,000 kg main tank | Solar and battery auxiliary power. "
                        + "Requires an operational orbital yard. Buy compatible fuel and charge batteries before departure. "
                        + "Unpowered tank conditioning vents propellant. Planetary orbital navigation remains provisional.");
                register.setDisable(controller == null);
            }));
        };
        drive.valueProperty().addListener((observable, old, selected) -> refresh.run());
        name.textProperty().addListener((observable, old, selected) -> refresh.run());
        register.setOnAction(event -> {
            ++version[0];
            box.setDisable(true);
            feedback.setText("Queued chemical freighter blueprint registration.");
            controller.stageTrackedCommand(new DesignShipCommand(specification(id, name.getText(), owner, drive.getValue())))
                    .whenComplete((outcome, failure) -> Platform.runLater(() -> feedback.setText(failure != null
                            ? "Freighter registration failed." : switch (outcome) {
                        case EXECUTED -> "Freighter blueprint registered. Build it at an orbital yard.";
                        case REJECTED -> "Freighter registration rejected: research or blueprint requirements changed.";
                        case CANCELLED -> "Freighter registration cancelled.";
                    })));
        });
        box.getChildren().addAll(new HBox(6, name, drive), preview, register);
        if (!drive.getItems().isEmpty()) drive.setValue(drive.getItems().getFirst());
        return box;
    }

    private static ShipDesignSpecification specification(String id, String name, String owner, String drive) {
        return new ShipDesignSpecification(id, name, owner, ShipRole.CARGO_TRANSPORT, "steel",
                ChemicalFreighterCatalog.modules(drive), "steel", .5);
    }
}
