package com.spaceconquest.frontend;

import com.spaceconquest.control.command.FleetServiceStatus;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.FleetServiceEvent;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class FleetServiceStatusCardTest {
    @BeforeAll static void start() throws Exception {
        var ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); } catch (IllegalStateException running) { ready.countDown(); }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
    }
    private void fx(Runnable action) throws Exception {
        var done = new CompletableFuture<Void>();
        Platform.runLater(() -> { try { action.run(); done.complete(null); } catch (Throwable failure) { done.completeExceptionally(failure); } });
        done.get(10, TimeUnit.SECONDS);
    }
    private FleetServiceStatus status(String phase) {
        return new FleetServiceStatus(phase, "Guard home", "port", List.of("Grid charge: no stored local electricity"),
                List.of(new FleetServiceEvent(3, "owner", "fleet", FleetServiceEvent.Kind.SUPPLIES_PURCHASED,
                        "Actual local service", Map.of("cannon_round", 5.0), 10, 2)));
    }
    @Test void rowsShowMissionDestinationBlockersAndCommittedReceiptWithoutActions() throws Exception {
        fx(() -> {
            var box = new VBox(); FleetServiceStatusCard.render(box, status("Waiting for funded service"));
            assertEquals("Paused mission: Guard home", ((Label) box.lookup("#service-mission")).getText());
            assertTrue(((Label) box.lookup("#service-destination")).getText().contains("port"));
            assertTrue(((Label) box.lookup("#service-blocker")).getText().contains("electricity"));
            assertEquals("Current recovery requirements", ((Label) box.lookup("#service-requirements")).getText());
            String receipt = ((Label) box.lookup("#service-event")).getText();
            assertTrue(receipt.contains("cannon_round")); assertTrue(receipt.contains("10.00 kWh")); assertTrue(receipt.contains("2.00 credits"));
            assertTrue(box.getChildren().stream().allMatch(node -> node instanceof Label));
        });
    }
    @Test void staleResultsAndFailuresCannotReplaceTheCurrentInspection() throws Exception {
        var state = new GameState(); var current = new AtomicReference<>(state); var box = new AtomicReference<VBox>();
        var old = new CompletableFuture<FleetServiceStatus>(); var newer = new CompletableFuture<FleetServiceStatus>();
        fx(() -> {
            box.set(new VBox()); FleetServiceStatusCard.refresh(box.get(), state, current::get, () -> old);
            FleetServiceStatusCard.refresh(box.get(), state, current::get, () -> newer);
        });
        newer.complete(status("Current")); old.complete(status("Stale"));
        fx(() -> assertEquals("Current", ((Label) box.get().lookup("#service-phase")).getText()));
        var pending = new CompletableFuture<FleetServiceStatus>();
        fx(() -> FleetServiceStatusCard.refresh(box.get(), state, current::get, () -> pending));
        current.set(state.withTurn(1)); pending.complete(status("Wrong snapshot"));
        fx(() -> assertNotNull(box.get().lookup("#service-loading")));
        fx(() -> FleetServiceStatusCard.refresh(box.get(), current.get(), current::get,
                () -> CompletableFuture.failedFuture(new IllegalStateException("fixture"))));
        fx(() -> assertNotNull(box.get().lookup("#service-error")));
    }
}
