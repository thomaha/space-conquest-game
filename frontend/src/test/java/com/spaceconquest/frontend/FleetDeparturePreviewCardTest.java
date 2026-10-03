package com.spaceconquest.frontend;

import com.spaceconquest.control.command.MoveFleetCommand;
import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.CommandQueue;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetLocation;
import com.spaceconquest.engine.ship.FlightMotion;
import com.spaceconquest.engine.ship.ShipPowerState;
import com.spaceconquest.engine.ship.ShipBlueprintFactory;
import com.spaceconquest.engine.ship.ShipComponentCatalog;
import com.spaceconquest.engine.ship.ShipDesignSpecification;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipRole;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class FleetDeparturePreviewCardTest {
    @BeforeAll
    static void initializeJavaFx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Toolkit already initialized by another view test.
        }
    }

    private GameState state(boolean warp, double fuel) {
        var empire = new Empire("empire", "Empire", "human", "Individualist", 100_000, 0,
                List.of("a"), List.of(), Map.of(), warp ? List.of("rocketry", "warp") : List.of("rocketry"), List.of());
        var state = GameState.builder().empires(List.of(empire)).solarSystems(List.of(
                new SolarSystem("a", "A", "", 0, 0, 0, 1, 1, "yellow", List.of(), List.of()),
                new SolarSystem("b", "B", "", 1, 0, 0, 1, 1, "yellow", List.of(), List.of()))).build();
        var request = new ShipDesignSpecification("blueprint", "Scout", "empire", ShipRole.EXPLORER,
                "steel", ShipComponentCatalog.workbenchModules("mod_chemical_rocket", false), "steel", 0);
        var evaluation = ShipBlueprintFactory.evaluate(state, request);
        assertTrue(evaluation.valid());
        var ship = new ShipInstance("ship", "blueprint", "empire", 100, 0, fuel, Map.of());
        var fleet = new Fleet("fleet", "Fleet", "empire", "a", null, 0, 0, 0, false, "PASSIVE", List.of(ship));
        return state.withShipDesigns(List.of(evaluation.design())).withFleets(List.of(fleet));
    }

    private String text(VBox box) {
        return box.getChildren().stream().map(Label.class::cast).map(Label::getText)
                .reduce("", (first, next) -> first + "\n" + next);
    }

    private void onFxThread(Runnable assertions) throws Exception {
        var task = new FutureTask<Void>(() -> { assertions.run(); return null; });
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }

    @Test
    void chemicalPreviewShowsLongTravelAndElectricalFailureWithoutMutatingTheSnapshot() throws Exception {
        onFxThread(() -> {
            var state = state(false, 15_000);
            var fleet = state.fleets().getFirst();
            var box = new VBox();
            var move = new Button();
            FleetDeparturePreviewCard.refresh(box, move, state, fleet, "b");
            assertTrue(move.isDisabled());
            assertTrue(text(box).contains("Sublight"));
            assertTrue(text(box).contains("years"));
            assertTrue(text(box).contains("1.000 light-years"));
            assertTrue(text(box).contains("includes braking"));
            assertTrue(text(box).contains("Electrical supply for ship: Insufficient"));
            assertEquals(15_000, state.fleets().getFirst().ships().getFirst().currentFuelKg());
            assertNull(state.fleets().getFirst().targetSystemId());
            var preview = new MoveFleetCommand("fleet", "b").preview(state);
            assertNull(preview.local());
            assertTrue(preview.totalDays() > 1_000_000);
        });
    }

    @Test
    void researchedWarpPreviewShowsFourDaysAndNoSublightBudget() throws Exception {
        onFxThread(() -> {
            var initial = state(true, 0);
            var ship = initial.fleets().getFirst().ships().getFirst();
            var power = new com.spaceconquest.engine.ship.ShipPowerState(
                    Map.of("rp1_kerosene", 1120.0, "liquid_oxygen", 2880.0), "rp1", "uranium",
                    0, true, 1, 1, 0, 0, 0, 0, 0);
            var state = initial.withFleets(List.of(initial.fleets().getFirst().withShips(List.of(ship.withPowerState(power)))));
            var box = new VBox();
            var move = new Button();
            FleetDeparturePreviewCard.refresh(box, move, state, state.fleets().getFirst(), "b");
            assertFalse(move.isDisabled());
            assertTrue(text(box).contains("Warp | 4 days total"));
            assertTrue(text(box).contains("0.0 kg crossing"));
            assertFalse(text(box).contains("Peak speed"));
        });
    }

    @Test
    void invalidDestinationOrEmptySublightTankDisablesMovementAndClearsOldPreview() throws Exception {
        onFxThread(() -> {
            var full = state(false, 15_000);
            var box = new VBox();
            var move = new Button();
            FleetDeparturePreviewCard.refresh(box, move, full, full.fleets().getFirst(), "b");
            FleetDeparturePreviewCard.refresh(box, move, full, full.fleets().getFirst(), "missing");
            assertTrue(move.isDisabled());
            assertFalse(text(box).contains("years"));
            var empty = state(false, 0);
            FleetDeparturePreviewCard.refresh(box, move, empty, empty.fleets().getFirst(), "b");
            assertTrue(move.isDisabled());
            assertTrue(text(box).contains("Departure unavailable"));
            FleetDeparturePreviewCard.refresh(box, move, full, full.fleets().getFirst(), "b");
            assertTrue(move.isDisabled());
        });
    }

    @Test void recoveryControlStagesMovementAndDisablesItWhenElectricalSupplyIsMissing() throws Exception {
        onFxThread(() -> {
            var initial = state(false, 15000);
            var original = initial.fleets().getFirst();
            var power = new ShipPowerState(Map.of("rp1_kerosene", 140.0, "liquid_oxygen", 360.0),
                    "rp1", "uranium", 0, true, 1, 1, 0, 0, 0, 0, 0);
            var ship = original.ships().getFirst().withPowerState(power);
            var fleet = new Fleet(original.id(), original.name(), original.ownerEntityId(), "a", "b",
                    0, 0, .01, false, "PASSIVE", List.of(ship), FleetLocation.at(FleetLocation.Site.deepSpace()),
                    Fleet.MODE_POWER_INTERRUPTED, 5, 1e6, 1, 1, 100, Map.of("ship", 100.0),
                    new FlightMotion(10000, 100, null, 0));
            var state = initial.withFleets(List.of(fleet));
            var queue = new CommandQueue();
            var feedback = new Label();
            var controller = new HumanController(queue);
            var box = ShipPowerCard.create(state, fleet, ship, state.shipDesigns().getFirst(), controller, feedback);
            var resume = box.getChildren().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> button.getText().equals("Plan recovery to destination")).findFirst().orElseThrow();
            assertFalse(resume.isDisabled());
            resume.fire();
            assertEquals(Fleet.MODE_POWER_INTERRUPTED, state.fleets().getFirst().interstellarMode());
            assertTrue(feedback.getText().contains("Queued recovery"));
            assertEquals(Fleet.MODE_RECOVERY, queue.drainAndExecute(state).fleets().getFirst().interstellarMode());
            var empty = ship.withPowerState(ShipPowerState.empty());
            var stranded = state.withFleets(List.of(fleet.withShips(List.of(empty))));
            box = ShipPowerCard.create(stranded, stranded.fleets().getFirst(), empty,
                    state.shipDesigns().getFirst(), controller, feedback);
            resume = box.getChildren().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> button.getText().equals("Plan recovery to destination")).findFirst().orElseThrow();
            assertTrue(resume.isDisabled());
        });
    }

    @Test void pausedWarpControlsShowRemainingProgressAndStageResumption() throws Exception {
        onFxThread(() -> {
            var initial = state(true, 0);
            var original = initial.fleets().getFirst();
            var power = new ShipPowerState(Map.of("rp1_kerosene", 1120.0, "liquid_oxygen", 2880.0),
                    "rp1", "uranium", 0, true, 1, 1, 0, 0, 0, 0, 0);
            var ship = original.ships().getFirst().withPowerState(power);
            var fleet = new Fleet(original.id(), original.name(), original.ownerEntityId(), "a", "b", 0, 0,
                    .25, false, "PASSIVE", List.of(ship), FleetLocation.at(FleetLocation.Site.deepSpace()),
                    Fleet.MODE_POWER_INTERRUPTED, 4, 0, 0, 1, 0, Map.of());
            var state = initial.withFleets(List.of(fleet));
            var queue = new CommandQueue();
            var feedback = new Label();
            var box = ShipPowerCard.create(state, fleet, ship, state.shipDesigns().getFirst(), new HumanController(queue), feedback);
            assertTrue(box.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                    .anyMatch(label -> label.getText().contains("Paused warp journey: 25.0% complete | 3 days remaining")));
            var resume = box.getChildren().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> button.getText().equals("Plan recovery to destination")).findFirst().orElseThrow();
            assertFalse(resume.isDisabled());
            resume.fire();
            assertTrue(feedback.getText().contains("resumption"));
            assertEquals(Fleet.MODE_POWER_INTERRUPTED, state.fleets().getFirst().interstellarMode());
            var resumed = queue.drainAndExecute(state).fleets().getFirst();
            assertTrue(resumed.isInWarp());
            assertEquals(.25, resumed.transitProgress());
            assertEquals(1, resumed.interstellarElapsedDays());
        });
    }
}
