package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.DesignShipCommand;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.*;
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

/** Small tanker preset workbench; authoritative registration still rebuilds the immutable specification. */
final class TankerDesignCard {
    private TankerDesignCard() {}
    static VBox create(GameState state, String owner, HumanController controller, Label feedback) {
        VBox box = new VBox(6);
        box.getChildren().add(new Label("Tanker design preset"));
        if (state == null) return box;
        List<String> researched = state.empires().stream().filter(empire -> empire.id().equals(owner))
                .map(empire -> empire.unlockedTechIds()).findFirst().orElse(List.of());
        ComboBox<String> drive = new ComboBox<>();
        drive.setId("tanker-drive");
        drive.getItems().addAll(PropulsionCatalog.MAIN_DRIVE_IDS.stream().filter(id -> !"mod_antimatter_drive".equals(id)
                && PropulsionCatalog.researched(List.of(id), researched)).toList());
        ComboBox<String> power = new ComboBox<>();
        power.setId("tanker-power");
        power.getItems().addAll(ShipComponentCatalog.POWER_MODULE_IDS.stream()
                .filter(id -> ShipComponentCatalog.powerResearched(List.of(id), researched)).toList());
        TextField name = new TextField("Fleet tanker");
        name.setId("tanker-name");
        Label preview = new Label();
        preview.setWrapText(true);
        preview.setId("tanker-preview");
        Button register = new Button("Register tanker blueprint");
        register.setId("register-tanker");
        register.setDisable(true);
        String id = "tanker_" + UUID.randomUUID();
        long[] version = {0};
        Runnable refresh = () -> {
            long current = ++version[0];
            register.setDisable(true);
            if (drive.getValue() == null || power.getValue() == null) { preview.setText("Research a compatible drive and electrical source."); return; }
            var spec = specification(id, name.getText(), owner, drive.getValue(), power.getValue());
            preview.setText("Calculating tanker blueprint…");
            CompletableFuture.supplyAsync(() -> ShipBlueprintFactory.evaluate(state, spec)).whenComplete((result, failure) -> Platform.runLater(() -> {
                if (current != version[0]) return;
                if (failure != null || !result.valid()) {
                    preview.setText(failure == null ? String.join("; ", result.errors()) : "Tanker preview failed."); return;
                }
                preview.setText("Supply capacity: " + ShipSupplyCatalog.totalCapacity(result.design()) + " kg | Ordinary cargo: "
                        + result.design().maxCargoMassKg() + " kg | Pumps: " + ShipSupplyCatalog.transferKgPerHour(result.design())
                        + " kg/h. New tanks are empty; purchase compatible fuel before departure.");
                register.setDisable(controller == null);
            }));
        };
        drive.valueProperty().addListener((observable, old, selected) -> refresh.run());
        power.valueProperty().addListener((observable, old, selected) -> refresh.run());
        name.textProperty().addListener((observable, old, selected) -> refresh.run());
        register.setOnAction(event -> {
            ++version[0]; box.setDisable(true);
            feedback.setText("Queued tanker blueprint registration.");
            var command = new DesignShipCommand(specification(id, name.getText(), owner, drive.getValue(), power.getValue()));
            controller.stageTrackedCommand(command).whenComplete((outcome, failure) -> Platform.runLater(() -> feedback.setText(
                    failure != null ? "Tanker registration failed." : switch (outcome) {
                        case EXECUTED -> "Tanker blueprint registered. Select it in the ship construction workbench.";
                        case REJECTED -> "Tanker registration rejected: research, design or yard capacity changed.";
                        case CANCELLED -> "Tanker registration cancelled.";
                    })));
        });
        box.getChildren().addAll(new HBox(6, name, drive, power), preview, register);
        if (!power.getItems().isEmpty()) power.setValue(power.getItems().getFirst());
        if (!drive.getItems().isEmpty()) drive.setValue(drive.getItems().getFirst());
        return box;
    }
    private static ShipDesignSpecification specification(String id, String name, String owner, String drive, String power) {
        return new ShipDesignSpecification(id, name, owner, ShipRole.CARGO_TRANSPORT, "steel",
                ShipSupplyCatalog.tankerModules(drive, power), "steel", .5);
    }
}
