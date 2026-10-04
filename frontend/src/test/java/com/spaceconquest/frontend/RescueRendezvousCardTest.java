package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetLocation;
import com.spaceconquest.engine.ship.FleetProcessor;
import com.spaceconquest.engine.ship.FlightMotion;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipManufacturingProfile;
import com.spaceconquest.engine.ship.ShipPowerProfile;
import com.spaceconquest.engine.ship.ShipPowerProcessor;
import com.spaceconquest.engine.ship.ShipPowerState;
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

class RescueRendezvousCardTest {
    @BeforeAll static void initializeJavaFx() {
        try { Platform.startup(() -> {}); }
        catch (IllegalStateException ignored) { /* Toolkit is already running. */ }
    }

    @Test void rescueStagesWithoutMovingShipsOrTakingCargoBeforeTheTick() throws Exception {
        var task = new FutureTask<>(() -> {
            var profile = new ShipPowerProfile(0, 0, 100, 0, 0, 0, 0, 10, 2, 20, 0, .65,
                    Map.of("uranium", new ShipPowerProfile.Fuel("refined_uranium", null, 1, 6e6)));
            var design = new ShipDesign("design", "Ship", "owner", ShipRole.EXPLORER, "steel",
                    List.of("mod_chemical_rocket"), "steel", 0, 1000, 1000, 100000, 100, 1, 0, 2000,
                    false, false, ShipManufacturingProfile.baseline(), profile);
            var donor = new ShipInstance("donor", "design", "owner", 100, 0, 2000,
                    Map.of("refined_uranium", 2.0)).withPowerState(new ShipPowerState(Map.of("refined_uranium", 1.0),
                    "rp1", "uranium", 0, false, 1, 1, 0, 0, 0, 0, 0));
            var receiver = new ShipInstance("receiver", "design", "owner", 100, 0, 1000, Map.of());
            var target = new Fleet("target", "Target", "owner", "a", "b", 0, 0, .5, false, "PASSIVE",
                    List.of(receiver), FleetLocation.at(FleetLocation.Site.deepSpace()), Fleet.MODE_POWER_INTERRUPTED,
                    20, 1e9, 1, 5, 100, Map.of(), new FlightMotion(5e8, 100, null, 0));
            var state = GameState.builder().shipDesigns(List.of(design)).fleets(List.of(
                    new Fleet("rescuer", "Rescuer", "owner", "a", "", 0, 0, 0, false, "PASSIVE", List.of(donor)), target))
                    .solarSystems(List.of(new SolarSystem("a", "A", "", 0, 0, 0, 1, 1, "yellow", List.of(), List.of()),
                            new SolarSystem("b", "B", "", 0, 0, 0, 1, 1, "yellow", List.of(), List.of()))).build();
            var controller = new HumanController();
            var feedback = new Label();
            var card = RescueRendezvousCard.create(state, target, receiver, design, controller, feedback);
            var row = (HBox) card.getChildren().getLast();
            var button = (Button) row.getChildren().getLast();
            button.fire();
            assertEquals(1, controller.getCommandQueue().size());
            assertEquals(2, donor.storedCargoKg().get("refined_uranium"));
            assertEquals("", state.fleets().getFirst().targetSystemId());
            assertTrue(feedback.getText().contains("Queued rescue"));
            assertTrue(button.isDisabled());
            var next = controller.getCommandQueue().drainAndExecute(state);
            assertEquals(Fleet.MODE_RECOVERY, next.fleets().getFirst().interstellarMode());
            assertEquals(2, next.fleets().getFirst().ships().getFirst().storedCargoKg().get("refined_uranium"));
            assertEquals("receiver", next.fleets().getFirst().flightMotion().trajectory().rescueOrder().receiverShipId());
            var approaching = RescueFeedbackCard.create(next, next.fleets().getLast().ships().getFirst());
            assertTrue(((Label) approaching.getChildren().getLast()).getText().contains("days to planned contact"));
            for (int day = 0; day < 30 && Fleet.MODE_RECOVERY.equals(next.fleets().getFirst().interstellarMode()); day++)
                next = next.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(next), List.of(), List.of()));
            var delivered = RescueFeedbackCard.create(next, next.fleets().getLast().ships().getFirst());
            assertTrue(((Label) delivered.getChildren().getLast()).getText().contains("delivered the requested supplies"));
            var receiverCard = ShipPowerCard.create(next, next.fleets().getLast(), next.fleets().getLast().ships().getFirst(),
                    design, controller, feedback);
            assertTrue(receiverCard.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                    .anyMatch(label -> label.getText().contains("Ready to resume")));
            return null;
        });
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }
}
