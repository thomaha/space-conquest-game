package com.spaceconquest.frontend;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.galaxy.*;
import com.spaceconquest.engine.ship.*;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class SensorContactCardTest {
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
    private Empire empire(String id) {
        return new Empire(id, id, "human", "Individualist", 0, 0, List.of(), List.of(), Map.of(), List.of(), List.of());
    }
    private GameState world(boolean powered) {
        var design = new ShipDesign("own", "Own", "own", ShipRole.COMBAT_SHIP, "steel",
                List.of(SensorModuleCatalog.BASIC), "steel", 0, 1000, 1000, 1000, 100, 10, 0, 1000,
                false, false, ShipManufacturingProfile.baseline(), null);
        var ship = new ShipInstance("own-ship", "own", "own", 1000, 0, 100, Map.of());
        if (!powered) ship = ship.withModuleDamage(new ShipModuleDamage(Map.of(0, 100.0)));
        var own = new Fleet("own", "Own", "own", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship));
        var saved = new FleetProtectionAssignment(null, "", "own", own.location().current(), FleetProtectionAssignment.Stage.RETURNING, "", true);
        own = own.withInterceptOrder(new FleetInterceptOrder("own", "own", "sol", saved));
        var enemy = new Fleet("enemy", "Hidden new name", "enemy", "sol", "", 0, 0, 0, false, "PASSIVE",
                List.of(new ShipInstance("enemy-ship", "unknown", "enemy", 1000, 0, 100, Map.of())));
        var system = new SolarSystem("sol", "Sol", "", 0, 0, 0, 1.989e30, 1392700, "yellow", List.of(), List.of());
        var state = GameState.builder().turn(11).empires(List.of(empire("own"), empire("enemy"))).shipDesigns(List.of(design))
                .fleets(List.of(own, enemy)).solarSystems(List.of(system))
                .diplomaticRelations(List.of(new DiplomaticRelation("own", "enemy", "TOTAL_WAR", 0))).build();
        var p = SensorPosition.at(state, "sol", own.location());
        var contact = new SensorContact("enemy", "enemy", "sol",
                new LocalSiteGeometry.Point(p.xMeters() + 1000, p.yMeters() + 2000, p.zMeters() + 3000), 10, 1000);
        return state.withFogOfWarStates(List.of(new FogOfWarState("own", List.of("sol"), List.of(), List.of(), List.of(), List.of(), List.of(contact))));
    }

    @Test void lostRowsUseOnlyStoredCoordinatesAndExplainRetainedPrioritiesAndReturn() throws Exception {
        var state = world(false); var status = SensorContactCard.inspect(state, "own", "sol", state.fleets().getFirst());
        assertEquals(1, status.rows().size()); var row = status.rows().getFirst(); assertFalse(row.live());
        assertTrue(row.text().contains("Lost contact")); assertTrue(row.text().contains("1 day ago (day 10)"));
        assertTrue(row.text().contains("System coordinates:")); assertTrue(row.text().contains("z 3 km"));
        assertTrue(row.text().contains("86400 km radius"));
        assertFalse(row.text().contains("Hidden new name"));
        assertTrue(status.explanations().stream().anyMatch(text -> text.contains("escape power")));
        assertTrue(status.explanations().stream().anyMatch(text -> text.contains("Escape return waits")));
        var hiddenGone = state.withFleets(List.of(state.fleets().getFirst()));
        assertEquals(status.rows(), SensorContactCard.inspect(hiddenGone, "own", "sol", null).rows());
        assertEquals(state, world(false));
        fx(() -> {
            var box = new VBox(); SensorContactCard.render(box, status);
            assertEquals(row.text(), ((Label) box.lookup("#sensor-contact-enemy")).getText());
            assertTrue(box.lookupAll(".button").isEmpty());
        });
    }

    @Test void liveDetectionReplacesLostRowAndExpiryAndOtherViewersCannotExposeHistory() {
        var state = world(true); var live = SensorContactCard.inspect(state, "own", "sol", null);
        assertEquals(1, live.rows().size()); assertTrue(live.rows().getFirst().live());
        assertTrue(live.rows().getFirst().text().contains("Observed now (day 11)"));
        assertFalse(live.rows().getFirst().text().contains("Lost contact"));
        assertTrue(SensorContactCard.inspect(world(false).withTurn(13), "own", "sol", null).rows().isEmpty());
        assertTrue(SensorContactCard.inspect(world(false), "unaffiliated", "sol", null).rows().isEmpty());
        assertTrue(SensorContactCard.inspect(world(false), "own", "other-system", null).rows().isEmpty());
    }

    @Test void newerRequestsAndChangedSnapshotsRejectStaleResults() throws Exception {
        var state = world(false); var current = new AtomicReference<>(state); var box = new VBox();
        var old = new CompletableFuture<SensorContactCard.Status>(); var newer = new CompletableFuture<SensorContactCard.Status>();
        fx(() -> { SensorContactCard.refresh(box, state, current::get, () -> old); SensorContactCard.refresh(box, state, current::get, () -> newer); });
        newer.complete(new SensorContactCard.Status(List.of(new SensorContactCard.Row("new", "Current", true)), List.of()));
        fx(() -> assertNotNull(box.lookup("#sensor-contact-new")));
        old.complete(new SensorContactCard.Status(List.of(new SensorContactCard.Row("old", "Stale", false)), List.of()));
        fx(() -> assertNull(box.lookup("#sensor-contact-old")));
        var pending = new CompletableFuture<SensorContactCard.Status>();
        fx(() -> SensorContactCard.refresh(box, state, current::get, () -> pending));
        current.set(state.withTurn(12)); pending.complete(new SensorContactCard.Status(List.of(), List.of()));
        fx(() -> assertNotNull(box.lookup("#sensor-loading")));
    }

    @Test void expansionSurvivesRefreshAndFailuresShowFeedback() throws Exception {
        var state = world(false); var expanded = new HashSet<String>(); var box = new VBox();
        fx(() -> {
            var pane = SensorContactCard.create(state, "own", "sol", null, () -> state, expanded, "sol");
            assertFalse(pane.isExpanded()); pane.setExpanded(true); assertTrue(expanded.contains("sol"));
            assertTrue(SensorContactCard.create(state, "own", "sol", null, () -> state, expanded, "sol").isExpanded());
            pane.setExpanded(false); assertFalse(expanded.contains("sol"));
            SensorContactCard.refresh(box, state, () -> state, () -> CompletableFuture.failedFuture(new IllegalStateException("fixture")));
        });
        fx(() -> assertTrue(((Label) box.lookup("#sensor-error")).getText().contains("unavailable")));
    }
}
