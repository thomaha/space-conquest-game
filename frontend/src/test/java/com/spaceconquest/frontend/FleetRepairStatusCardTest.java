package com.spaceconquest.frontend;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.*;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class FleetRepairStatusCardTest {
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
    private FleetRepairStatus status(String mission) {
        return new FleetRepairStatus(
                List.of(new FleetRepairStatus.Ship("ship", 10, List.of(new FleetRepairStatus.Mount(1, "Sensor array", 50)), true, true)),
                List.of(new FleetRepairStatus.Provider("ship", 20, 20, List.of(), "ship / Sensor array", Map.of("refined_copper", 4.0))),
                10, new ShipRepairPriority.Context(true, FleetThreatAssessment.Action.NO_CONTACT, false),
                new FleetRepairStatus.Base("Base", 50, "ship", "Sensor array", 10, 28, Map.of(), List.of()), mission);
    }
    private String text(VBox box, String id) { return ((Label) box.lookup("#" + id)).getText(); }

    @Test void showsDamageCapacityMissingMaterialsBaseCostsAndSavedMissionAsReadOnlyLabels() throws Exception {
        var status = status("Guard earth");
        fx(() -> {
            var box = new VBox(); FleetRepairStatusCard.render(box, status);
            assertTrue(text(box, "repair-priority").contains("restore missing sensor coverage"));
            assertTrue(text(box, "repair-capacity").contains("20.0 points/day"));
            assertTrue(text(box, "repair-capacity").contains("10.0 points"));
            assertTrue(text(box, "repair-materials-ship").contains("4.00 kg refined copper"));
            assertTrue(text(box, "repair-ship-ship").contains("Sensor array #2: 50.0"));
            assertTrue(text(box, "repair-base-quote").contains("28.00 credits"));
            assertTrue(text(box, "repair-mission").contains("Guard earth"));
            assertTrue(box.lookupAll(".button").isEmpty());
        });
    }

    @Test void newerRequestsAndSnapshotsRejectStaleAsynchronousResults() throws Exception {
        var state = GameState.builder().build(); var current = new AtomicReference<>(state);
        var old = new CompletableFuture<FleetRepairStatus>(); var newer = new CompletableFuture<FleetRepairStatus>();
        var box = new AtomicReference<VBox>();
        fx(() -> {
            box.set(new VBox());
            FleetRepairStatusCard.refresh(box.get(), state, current::get, () -> old);
            FleetRepairStatusCard.refresh(box.get(), state, current::get, () -> newer);
        });
        newer.complete(status("Escort convoy"));
        fx(() -> assertTrue(text(box.get(), "repair-mission").contains("Escort convoy")));
        old.complete(status("Guard old target"));
        fx(() -> assertTrue(text(box.get(), "repair-mission").contains("Escort convoy")));
        var pending = new CompletableFuture<FleetRepairStatus>();
        fx(() -> FleetRepairStatusCard.refresh(box.get(), state, current::get, () -> pending));
        current.set(state.withTurn(1)); pending.complete(status("Stale mission"));
        fx(() -> { assertNotNull(box.get().lookup("#repair-loading")); assertNull(box.get().lookup("#repair-mission")); });
    }

    @Test void unavailableCalculationShowsARecoverableMessage() throws Exception {
        var state = GameState.builder().build(); var box = new AtomicReference<VBox>();
        fx(() -> {
            box.set(new VBox()); FleetRepairStatusCard.refresh(box.get(), state, () -> state,
                    () -> CompletableFuture.failedFuture(new IllegalStateException("fixture")));
        });
        fx(() -> assertTrue(text(box.get(), "repair-error").contains("unavailable")));
    }

    @Test void cardDefersWorkUntilExpandedAndKeepsExpansionAcrossSnapshotRefreshes() throws Exception {
        var fleet = new Fleet("fleet", "Fleet", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of());
        var state = GameState.builder().fleets(List.of(fleet)).build(); var expanded = new HashSet<String>();
        fx(() -> {
            var card = FleetRepairStatusCard.create(state, fleet, () -> state, expanded);
            assertFalse(card.isExpanded()); assertTrue(((VBox) card.getContent()).getChildren().isEmpty());
            card.setExpanded(true); assertTrue(expanded.contains(fleet.id()));
            var refreshed = FleetRepairStatusCard.create(state, fleet, () -> state, expanded);
            assertTrue(refreshed.isExpanded());
            refreshed.setExpanded(false); assertFalse(expanded.contains(fleet.id()));
        });
    }
}
