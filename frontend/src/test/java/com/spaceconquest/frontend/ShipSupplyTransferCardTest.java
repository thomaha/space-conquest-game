package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipManufacturingProfile;
import com.spaceconquest.engine.ship.ShipPowerProfile;
import com.spaceconquest.engine.ship.ShipRole;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class ShipSupplyTransferCardTest {
    @BeforeAll static void initializeJavaFx() {
        try { Platform.startup(() -> {}); }
        catch (IllegalStateException ignored) { /* Toolkit already initialized by another view test. */ }
    }

    @Test void transferButtonStagesWithoutMutatingSnapshotAndExecutesAtTheTick() throws Exception {
        var task = new FutureTask<>(() -> {
            var profile = new ShipPowerProfile(0, 100, 0, 0, 0, 0, 1000, 0, 2, 0, 0, .65,
                    Map.of("rp1", new ShipPowerProfile.Fuel("rp1_kerosene", "liquid_oxygen", .28, 1.008)));
            var design = new ShipDesign("design", "Ship", "owner", ShipRole.EXPLORER, "steel", List.of(),
                    "steel", 0, 1000, 1000, 1000, 100, 1, 0, 0, false, false, ShipManufacturingProfile.baseline(), profile);
            var donor = new ShipInstance("donor", "design", "owner", 100, 0, 0,
                    Map.of("rp1_kerosene", 28.0, "liquid_oxygen", 72.0));
            var receiver = new ShipInstance("receiver", "design", "owner", 100, 0, 0, Map.of());
            var fleet = new Fleet("fleet", "Fleet", "owner", "a", "", 0, 0, 0, false, "PASSIVE", List.of(donor, receiver));
            var state = GameState.builder().fleets(List.of(fleet)).shipDesigns(List.of(design)).solarSystems(List.of(
                    new SolarSystem("a", "A", "", 0, 0, 0, 1.989e30, 1, "yellow", List.of(), List.of()))).build();
            var controller = new HumanController();
            var feedback = new Label();
            var card = ShipSupplyTransferCard.create(state, fleet, receiver, design, controller, feedback);
            var row = (HBox) card.getChildren().getLast();
            var button = (Button) row.getChildren().getLast();
            button.fire();
            assertEquals(1, controller.getCommandQueue().size());
            assertEquals(0, receiver.generatorFuelMassKg());
            assertTrue(feedback.getText().contains("Queued supply transfer"));
            assertTrue(button.isDisabled());
            var next = controller.getCommandQueue().drainAndExecute(state);
            assertEquals(1, next.fleets().getFirst().ships().get(1).generatorFuelMassKg());
            assertEquals(27.72, next.fleets().getFirst().ships().getFirst().storedCargoKg().get("rp1_kerosene"), 1e-9);
            return null;
        });
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }
}
