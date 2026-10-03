package com.spaceconquest.frontend;

import com.spaceconquest.control.command.MoveFleetCommand;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.ship.Fleet;
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
    void chemicalPreviewShowsLongTravelAndFuelWithoutMutatingTheSnapshot() throws Exception {
        onFxThread(() -> {
            var state = state(false, 15_000);
            var fleet = state.fleets().getFirst();
            var box = new VBox();
            var move = new Button();
            FleetDeparturePreviewCard.refresh(box, move, state, fleet, "b");
            assertFalse(move.isDisabled());
            assertTrue(text(box).contains("Sublight"));
            assertTrue(text(box).contains("years"));
            assertTrue(text(box).contains("1.000 light-years"));
            assertTrue(text(box).contains("includes braking"));
            assertTrue(text(box).contains("electrical endurance are not modeled"));
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
            var state = state(true, 0);
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
            assertFalse(move.isDisabled());
        });
    }
}
