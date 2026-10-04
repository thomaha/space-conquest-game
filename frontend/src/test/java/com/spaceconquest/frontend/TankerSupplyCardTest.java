package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.ScheduleFleetSupplyCommand;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.ship.*;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class TankerSupplyCardTest {
    @BeforeAll static void initializeJavaFx() {
        try { Platform.startup(() -> {}); } catch (IllegalStateException ignored) { /* Already initialized. */ }
    }
    private <T> T fx(Callable<T> work) throws Exception {
        var task = new FutureTask<>(work); Platform.runLater(task); return task.get(10, TimeUnit.SECONDS);
    }
    private GameState state() {
        var owner = new Empire("owner", "Owner", "human", "Individualist", 1e6, 0, List.of(), List.of(), Map.of(),
                List.of("rocketry", "electricity", "industrial_production"), List.of());
        var p = ShipPowerProfile.capture(ShipComponentCatalog.workbenchModules("mod_chemical_rocket", false).stream()
                .map(ShipComponentCatalog::module).toList());
        var d = new ShipDesign("design", "Tanker", "owner", ShipRole.CARGO_TRANSPORT, "steel",
                List.of("mod_chemical_rocket", ShipSupplyCatalog.LIQUID_TANK, ShipSupplyCatalog.CRYOGENIC_TANK, ShipSupplyCatalog.TRANSFER_PUMP),
                "steel", 0, 10000, 0, 15000, 100, 1, 0, 600000, false, false, ShipManufacturingProfile.baseline(), p);
        var supplier = new ShipInstance("supplier", "design", "owner", 100, 0, 10000, Map.of())
                .withSupplyState(new ShipSupplyState(Map.of("rp1_kerosene", 280.0, "liquid_oxygen", 720.0), null, ""));
        var receiver = new ShipInstance("receiver", "design", "owner", 100, 0, 10000, Map.of());
        return GameState.builder().empires(List.of(owner)).shipDesigns(List.of(d)).fleets(List.of(new Fleet("fleet", "Supply fleet", "owner",
                "a", "", 0, 0, 0, false, "PASSIVE", List.of(supplier, receiver)))).build();
    }
    @Test void tankerRegistrationPreviewRunsAsynchronouslyAndOnlyStagesBlueprint() throws Exception {
        var state = state();
        var controller = new HumanController();
        CompletableFuture<VBox> ready = new CompletableFuture<>();
        Label feedback = fx(() -> {
            Label message = new Label();
            VBox card = TankerDesignCard.create(state, "owner", controller, message);
            Button register = (Button) card.lookup("#register-tanker");
            register.disabledProperty().addListener((observable, old, disabled) -> { if (!disabled) ready.complete(card); });
            if (!register.isDisabled()) ready.complete(card);
            return message;
        });
        var card = ready.get(10, TimeUnit.SECONDS);
        fx(() -> {
            assertTrue(((Label) card.lookup("#tanker-preview")).getText().contains("80000.0 kg"));
            ((Button) card.lookup("#register-tanker")).fire();
            assertEquals(1, controller.getCommandQueue().size());
            assertEquals(1, state.shipDesigns().size());
            return null;
        });
        var next = controller.getCommandQueue().drainAndExecute(state);
        assertEquals(2, next.shipDesigns().size());
        assertEquals(80000, ShipSupplyCatalog.totalCapacity(next.shipDesigns().getLast()));
        fx(() -> { assertTrue(feedback.getText().contains("registered")); return null; });
    }
    @Test void scheduleControlsStageImmutableOrderAndShowExecutionReceipt() throws Exception {
        var initial = state();
        var controller = new HumanController();
        var feedback = fx(() -> {
            var message = new Label();
            var fleet = initial.fleets().getFirst();
            var card = ShipSupplyStorageCard.create(initial, fleet, fleet.ships().getFirst(), initial.shipDesigns().getFirst(), controller, message);
            assertTrue(((Label) card.lookup("#supply-inventory")).getText().contains("liquid_oxygen"));
            Button schedule = (Button) card.lookup("#schedule-fleet-supply");
            assertFalse(schedule.isDisabled());
            schedule.fire();
            assertNull(fleet.ships().getFirst().supplyState().order());
            assertEquals(1, controller.getCommandQueue().size());
            return message;
        });
        var next = controller.getCommandQueue().drainAndExecute(initial);
        var order = next.fleets().getFirst().ships().getFirst().supplyState().order();
        assertNotNull(order);
        assertEquals("receiver", order.receiverShipId());
        assertEquals(1, order.quantityKg());
        assertEquals(1000, next.fleets().getFirst().ships().getFirst().supplyFuelMassKg());
        fx(() -> { assertTrue(feedback.getText().contains("completed")); return null; });
    }

    @Test void propulsionChoiceStagesTypedOrderWithoutChangingTheSnapshot() throws Exception {
        var initial = state();
        var controller = new HumanController();
        fx(() -> {
            var fleet = initial.fleets().getFirst();
            var card = ShipSupplyStorageCard.create(initial, fleet, fleet.ships().getFirst(), initial.shipDesigns().getFirst(), controller, new Label());
            var raw = (ComboBox<?>) card.lookup("#scheduled-supply-fuel");
            int propulsion = -1;
            for (int index = 0; index < raw.getItems().size(); index++)
                if (raw.getItems().get(index) instanceof ShipSupplyTransferCard.Supply supply
                        && supply.destination() == ShipSupplyTransfer.Destination.PROPELLANT) propulsion = index;
            assertTrue(propulsion >= 0);
            raw.getSelectionModel().select(propulsion);
            var schedule = (Button) card.lookup("#schedule-fleet-supply");
            assertFalse(schedule.isDisabled());
            schedule.fire();
            assertNull(fleet.ships().getFirst().supplyState().order());
            return null;
        });
        var next = controller.getCommandQueue().drainAndExecute(initial);
        var order = next.fleets().getFirst().ships().getFirst().supplyState().order();
        assertEquals(ShipSupplyTransfer.Destination.PROPELLANT, order.destination());
        assertEquals("mod_chemical_rocket", order.fuelId());
        assertEquals(initial.fleets().getFirst().ships().getLast().currentFuelKg(), next.fleets().getFirst().ships().getLast().currentFuelKg());
    }

    @Test void existingScheduleAllowsAnotherNonoverlappingDelivery() throws Exception {
        var initial = new ScheduleFleetSupplyCommand("supplier", "receiver", "rp1", 1, 0).apply(state());
        var controller = new HumanController();
        fx(() -> {
            var fleet = initial.fleets().getFirst();
            var card = ShipSupplyStorageCard.create(initial, fleet, fleet.ships().getFirst(), initial.shipDesigns().getFirst(), controller, new Label());
            var delay = (Spinner<?>) card.lookup("#scheduled-supply-delay");
            ((SpinnerValueFactory.DoubleSpinnerValueFactory) delay.getValueFactory()).setValue(1.0);
            var schedule = (Button) card.lookup("#schedule-fleet-supply");
            assertFalse(schedule.isDisabled());
            schedule.fire();
            assertEquals(1, fleet.ships().getFirst().supplyState().orders().size());
            return null;
        });
        var next = controller.getCommandQueue().drainAndExecute(initial);
        assertEquals(2, next.fleets().getFirst().ships().getFirst().supplyState().orders().size());
        assertEquals(1000, next.fleets().getFirst().ships().getFirst().supplyFuelMassKg());
    }

    @Test void successfulScheduledPreviewRendersDestinationAndReturnReservesAsynchronously() throws Exception {
        var initial = state();
        var power = new ShipPowerState(Map.of("rp1_kerosene", 1120.0, "liquid_oxygen", 2880.0),
                "rp1", "uranium", 0, true, 1, 1, 0, 0, 0, 0, 0);
        var fleet = initial.fleets().getFirst().withShips(initial.fleets().getFirst().ships().stream()
                .map(ship -> ship.withPowerState(power)).toList());
        var fueled = initial.withFleets(List.of(fleet)).withSolarSystems(List.of(
                new SolarSystem("a", "A", "", 0, 0, 0, 1, 1, "yellow", List.of(), List.of()),
                new SolarSystem("b", "B", "", 1e7 / InterstellarTravel.METERS_PER_LIGHT_YEAR, 0, 0, 1, 1, "yellow", List.of(), List.of())));
        var scheduled = new ScheduleFleetSupplyCommand("supplier", "receiver", "mod_chemical_rocket", 1, 0,
                ShipSupplyTransfer.Destination.PROPELLANT).apply(fueled);
        CompletableFuture<VBox> rendered = new CompletableFuture<>();
        fx(() -> {
            var box = new VBox();
            var move = new Button();
            box.getChildren().addListener((javafx.collections.ListChangeListener<javafx.scene.Node>) change -> {
                if (box.getChildren().stream().anyMatch(node -> node instanceof Label label
                        && label.getText().startsWith("At destination"))) rendered.complete(box);
            });
            FleetDeparturePreviewCard.refresh(box, move, scheduled, scheduled.fleets().getFirst(), "b");
            assertTrue(move.isDisabled());
            assertFalse(rendered.isDone());
            return null;
        });
        var box = rendered.get(10, TimeUnit.SECONDS);
        fx(() -> {
            assertTrue(box.getChildren().stream().anyMatch(node -> node instanceof Label label
                    && label.getText().startsWith("Return reserve:")));
            assertTrue(box.getChildren().stream().anyMatch(node -> node instanceof Label label
                    && label.getText().contains("Passenger food")));
            return null;
        });
        assertEquals(1, scheduled.fleets().getFirst().ships().getFirst().supplyState().orders().size());
        assertEquals(10000, scheduled.fleets().getFirst().ships().getFirst().currentFuelKg());
    }

    @Test void scheduledDeparturePreviewDefersCalculationAndRendersUnavailableResult() throws Exception {
        CompletableFuture<VBox> rendered = new CompletableFuture<>();
        fx(() -> {
            var scheduled = new ScheduleFleetSupplyCommand("supplier", "receiver", "rp1", 1, 0).apply(state());
            var box = new VBox();
            var move = new Button();
            box.getChildren().addListener((javafx.collections.ListChangeListener<javafx.scene.Node>) change -> {
                if (box.getChildren().stream().anyMatch(node -> node instanceof Label label && label.getText().startsWith("Departure unavailable")))
                    rendered.complete(box);
            });
            FleetDeparturePreviewCard.refresh(box, move, scheduled, scheduled.fleets().getFirst(), "missing");
            assertTrue(move.isDisabled());
            assertTrue(((Label) box.getChildren().getFirst()).getText().startsWith("Calculating scheduled supply"));
            assertFalse(rendered.isDone());
            return null;
        });
        var box = rendered.get(10, TimeUnit.SECONDS);
        fx(() -> { assertTrue(((Label) box.getChildren().getFirst()).getText().startsWith("Departure unavailable")); return null; });
    }
}
