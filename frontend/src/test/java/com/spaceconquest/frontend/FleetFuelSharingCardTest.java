package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.ship.*;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
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

class FleetFuelSharingCardTest {
    private record Ui(GameState state, HumanController controller, VBox card, Label feedback) {}
    @BeforeAll static void initializeJavaFx() {
        try { Platform.startup(() -> {}); }
        catch (IllegalStateException ignored) { /* Toolkit already initialized. */ }
    }
    private <T> T fx(Callable<T> work) throws Exception {
        var task = new FutureTask<>(work);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
    private GameState state() {
        var p = new ShipPowerProfile(0, 100, 0, 500, 100, 200, 5000, 0, 2, 20, 0, .65,
                Map.of("rp1", new ShipPowerProfile.Fuel("rp1_kerosene", "liquid_oxygen", .28, 1.008)));
        var d = new ShipDesign("design", "Supply ship", "owner", ShipRole.CARGO_TRANSPORT, "steel", List.of("mod_chemical_rocket"),
                "steel", 0, 1000, 20000, 1000, 100, 1, 0, 2000, false, false, ShipManufacturingProfile.baseline(), p);
        var donor = new ShipInstance("donor", d.id(), "owner", 100, 0, 900, Map.of("rp1_kerosene", 2800.0, "liquid_oxygen", 7200.0))
                .withPowerState(new ShipPowerState(Map.of("rp1_kerosene", 280.0, "liquid_oxygen", 720.0), "rp1", "uranium", 0, false,
                        1, 1, 0, 0, 0, 0, 0));
        var receiver = new ShipInstance("receiver", d.id(), "owner", 100, 0, 100, Map.of());
        var foreign = new ShipInstance("foreign", d.id(), "other", 100, 0, 0, Map.of());
        return GameState.builder().solarSystems(List.of(new SolarSystem("a", "A", "", 0, 0, 0, 1.989e30, 1, "yellow", List.of(), List.of())))
                .shipDesigns(List.of(d)).fleets(List.of(new Fleet("fleet", "Supplies", "owner", "a", "", 0, 0, 0, false,
                        "PASSIVE", List.of(donor, receiver)), new Fleet("foreignFleet", "Foreign", "other", "a", "", 0, 0, 0, false,
                        "PASSIVE", List.of(foreign)))).build();
    }
    private Ui ready() throws Exception {
        CompletableFuture<Ui> ready = new CompletableFuture<>();
        fx(() -> {
            var state = state();
            var controller = new HumanController();
            var feedback = new Label();
            var card = FleetFuelSharingCard.create(state, "owner", controller, feedback);
            Ui ui = new Ui(state, controller, card, feedback);
            Button share = (Button) card.lookup("#fleet-share-fuel");
            share.disabledProperty().addListener((observable, old, disabled) -> { if (!disabled) ready.complete(ui); });
            if (!share.isDisabled()) ready.complete(ui);
            return null;
        });
        return ready.get(10, TimeUnit.SECONDS);
    }
    @Test void asyncPreviewFiltersForeignShipsStagesReviewedAllocationAndReportsCompletion() throws Exception {
        var ui = ready();
        fx(() -> {
            ListView<?> donors = (ListView<?>) ui.card().lookup("#fuel-suppliers");
            assertEquals(2, donors.getItems().size());
            Label preview = (Label) ui.card().lookup("#fuel-share-preview");
            assertTrue(preview.getText().contains("donor → receiver: 5000.000000 kg"));
            assertEquals(0, ui.controller().getCommandQueue().size());
            assertEquals(0, ui.state().fleets().getFirst().ships().getLast().generatorFuelMassKg());
            ((Button) ui.card().lookup("#fleet-share-fuel")).fire();
            assertEquals(1, ui.controller().getCommandQueue().size());
            assertTrue(ui.feedback().getText().startsWith("Queued"));
            return null;
        });
        // Simulation work executes on the test thread, not the JavaFX thread.
        var next = ui.controller().getCommandQueue().drainAndExecute(ui.state());
        assertEquals(5000, next.fleets().getFirst().ships().getLast().generatorFuelMassKg());
        assertEquals(0, ui.state().fleets().getFirst().ships().getLast().generatorFuelMassKg());
        fx(() -> { assertTrue(ui.feedback().getText().contains("completed")); return null; });
    }
    @Test void changedLocationRejectsReviewedCommandAndReportsActualOutcome() throws Exception {
        var ui = ready();
        fx(() -> { ((Button) ui.card().lookup("#fleet-share-fuel")).fire(); return null; });
        var own = ui.state().fleets().getFirst();
        var moving = ui.state().withFleets(List.of(own.withLocation(own.location().depart(FleetLocation.Site.orbit("body"), 2)),
                ui.state().fleets().getLast()));
        assertSame(moving, ui.controller().getCommandQueue().drainAndExecute(moving));
        fx(() -> { assertTrue(ui.feedback().getText().contains("rejected")); return null; });
    }
}
