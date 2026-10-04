package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetLocation;
import com.spaceconquest.engine.ship.ShipInstance;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class FleetOrganizationCardTest {
    @BeforeAll static void initializeJavaFx() {
        try { Platform.startup(() -> {}); }
        catch (IllegalStateException ignored) { /* Toolkit already initialized. */ }
    }
    private GameState state() {
        return GameState.builder().fleets(List.of(
                new Fleet("source", "Mixed", "owner", "a", "", 0, 0, 0, false, "ESCORT", List.of(ship("one"), ship("two"))),
                new Fleet("target", "Escort", "owner", "a", "", 0, 0, 0, false, "PASSIVE", List.of(ship("three"))),
                new Fleet("foreign", "Foreign", "other", "a", "", 0, 0, 0, false, "PASSIVE",
                        List.of(new ShipInstance("foreignShip", "design", "other", 100, 0, 0, Map.of()))))).build();
    }
    private ShipInstance ship(String id) { return new ShipInstance(id, "design", "owner", 100, 0, 10, Map.of("steel", 5.0)); }
    private <T> T fx(Callable<T> work) throws Exception {
        var task = new FutureTask<>(work);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    @Test void detachingShipsStagesWithoutMutationAndReportsActualExecution() throws Exception {
        Label feedback = fx(() -> {
            var initial = state();
            var controller = new HumanController();
            var message = new Label();
            var card = FleetOrganizationCard.create(initial, "owner", controller, message);
            ComboBox<?> source = (ComboBox<?>) card.lookup("#fleet-source");
            assertEquals(2, source.getItems().size());
            ListView<?> members = (ListView<?>) card.lookup("#fleet-members");
            members.getSelectionModel().clearAndSelect(1);
            ((TextField) card.lookup("#fleet-split-name")).setText("Supply ships");
            var detach = (Button) card.lookup("#fleet-split");
            assertFalse(detach.isDisabled());
            detach.fire();
            assertEquals(1, controller.getCommandQueue().size());
            assertEquals(2, initial.fleets().getFirst().ships().size());
            assertTrue(message.getText().startsWith("Queued request"));
            var next = controller.getCommandQueue().drainAndExecute(initial);
            var added = next.fleets().getLast();
            assertEquals("Supply ships", added.name());
            assertEquals(List.of("two"), added.ships().stream().map(ShipInstance::id).toList());
            assertEquals(1, next.fleets().getFirst().ships().size());
            return message;
        });
        fx(() -> { assertTrue(feedback.getText().startsWith("Completed request")); return null; });
    }

    @Test void movingAndMergingUseCurrentSelectionsAndRemoveEmptiedFleets() throws Exception {
        fx(() -> {
            for (String action : List.of("#fleet-transfer", "#fleet-merge")) {
                var initial = state();
                var controller = new HumanController();
                var card = FleetOrganizationCard.create(initial, "owner", controller, new Label());
                Button button = (Button) card.lookup(action);
                assertFalse(button.isDisabled());
                button.fire();
                var next = controller.getCommandQueue().drainAndExecute(initial);
                assertEquals(action.equals("#fleet-transfer") ? 2 : 3, next.fleets().stream()
                        .filter(fleet -> fleet.id().equals("target")).findFirst().orElseThrow().ships().size());
                assertEquals(action.equals("#fleet-transfer") ? 3 : 2, next.fleets().size());
                assertEquals(initial.fleets().getLast(), next.fleets().stream().filter(fleet -> fleet.id().equals("foreign")).findFirst().orElseThrow());
            }
            return null;
        });
    }

    @Test void staleLocationReportsRejectionAndLeavesActualStateUnchanged() throws Exception {
        Label feedback = fx(() -> {
            var initial = state();
            var controller = new HumanController();
            var message = new Label();
            var card = FleetOrganizationCard.create(initial, "owner", controller, message);
            ((Button) card.lookup("#fleet-transfer")).fire();
            var changed = initial.withFleets(initial.fleets().stream().map(fleet -> fleet.id().equals("target")
                    ? fleet.withLocation(FleetLocation.at(FleetLocation.Site.orbit("earth"))) : fleet).toList());
            assertSame(changed, controller.getCommandQueue().drainAndExecute(changed));
            return message;
        });
        fx(() -> { assertTrue(feedback.getText().contains("rejected")); return null; });
    }
}
