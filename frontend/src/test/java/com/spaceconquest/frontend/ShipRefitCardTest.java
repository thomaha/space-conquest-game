package com.spaceconquest.frontend;

import com.spaceconquest.control.command.ShipRefitQuote;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ShipRefitCardTest {
    @BeforeAll static void start() throws Exception {
        var ready = new CountDownLatch(1); try { Platform.startup(ready::countDown); } catch (IllegalStateException running) { ready.countDown(); }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
    }
    private void fx(Runnable action) throws Exception {
        var done = new CompletableFuture<Void>(); Platform.runLater(() -> { try { action.run(); done.complete(null); } catch (Throwable failure) { done.completeExceptionally(failure); } });
        done.get(10, TimeUnit.SECONDS);
    }
    private ShipRefitQuote quote(String target) {
        var empty = new ShipProductionReceipt(Map.of(), 0); var hardware = new ShipModule("test", "New equipment", "SMALL", 1, 1, 0, 0, 0, 1, Map.of(), Map.of());
        var installation = new ShipComponentInstallation("new", hardware, "", new ShipProductionReceipt(Map.of("steel", 20.0), 40), 2);
        var comparison = new ShipModelComparison(List.of(new ShipModelComparison.ComponentChange(ShipModelComparison.Kind.ADDED, null, installation)), false, false, 0,
                installation.production(), empty, empty, new ShipProductionReceipt(Map.of(), 2));
        return new ShipRefitQuote("ship", "old", target, "yard", "owner", comparison, ShipModuleDamage.empty(), "older".equals(target) ? 99.0 : 20.0, 5, 3, 1, 2, List.of());
    }
    @Test void displaySeparatesEquipmentWorkCostsAndPreservationWithoutStartingOrders() throws Exception {
        fx(() -> {
            var box = new VBox(); ShipRefitCard.render(box, quote("new"));
            assertTrue(((Label) box.lookup("#refit-components")).getText().contains("added 1"));
            assertTrue(((Label) box.lookup("#refit-work")).getText().contains("equipment 40.0"));
            assertTrue(((Label) box.lookup("#refit-cost")).getText().contains("total 28.00"));
            assertTrue(((Label) box.lookup("#refit-duration")).getText().contains("workload 2.00"));
            assertTrue(((Label) box.lookup("#refit-retention")).getText().contains("damage"));
            assertTrue(box.getChildren().stream().allMatch(node -> node instanceof Label));
        });
    }
    @Test void staleResultsCannotReplaceCurrentQuoteOrCrossSnapshotBoundaries() throws Exception {
        var state = new GameState(); var current = new AtomicReference<>(state); var box = new AtomicReference<VBox>();
        var old = new CompletableFuture<ShipRefitQuote>(); var newer = new CompletableFuture<ShipRefitQuote>();
        fx(() -> { box.set(new VBox()); ShipRefitCard.refresh(box.get(), state, current::get, () -> old); ShipRefitCard.refresh(box.get(), state, current::get, () -> newer); });
        newer.complete(quote("newer")); old.complete(quote("older"));
        fx(() -> assertTrue(((Label) box.get().lookup("#refit-cost")).getText().contains("total 28.00")));
        var pending = new CompletableFuture<ShipRefitQuote>(); fx(() -> ShipRefitCard.refresh(box.get(), state, current::get, () -> pending));
        current.set(state.withTurn(1)); pending.complete(quote("wrong-snapshot")); fx(() -> assertNotNull(box.get().lookup("#refit-loading")));
        current.set(state); var failure = new CompletableFuture<ShipRefitQuote>(); fx(() -> ShipRefitCard.refresh(box.get(), state, current::get, () -> failure));
        failure.completeExceptionally(new IllegalStateException("unavailable")); fx(() -> assertNotNull(box.get().lookup("#refit-error")));
    }
    @Test void selectorExcludesForeignShipsAndModels() throws Exception {
        var own = new ShipDesign("own", "Own model", "owner", ShipRole.COMBAT_SHIP, "steel", List.of(), "steel", 0, 100, 0, 0, 0, 1, 0, 0, false, false);
        var foreign = new ShipDesign("foreign", "Foreign model", "other", ShipRole.COMBAT_SHIP, "steel", List.of(), "steel", 0, 100, 0, 0, 0, 1, 0, 0, false, false);
        var fleets = List.of(new Fleet("owned", "Owned fleet", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(new ShipInstance("own-ship", "own", "owner", 100, 0, 0, Map.of()))),
                new Fleet("foreign", "Foreign fleet", "other", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(new ShipInstance("other-ship", "foreign", "other", 100, 0, 0, Map.of()))));
        var state = GameState.builder().shipDesigns(List.of(own, foreign)).fleets(fleets).build();
        fx(() -> {
            var pane = ShipRefitCard.create(state, "owner", () -> state); var content = (VBox) pane.getContent();
            var ships = (ComboBox<?>) content.lookup("#refit-ship"); var models = (ComboBox<?>) content.lookup("#refit-model");
            assertEquals(1, ships.getItems().size()); assertTrue(ships.getItems().getFirst().toString().contains("own-ship"));
            assertEquals(1, models.getItems().size()); assertEquals("Own model", models.getItems().getFirst().toString());
            assertTrue(state.shipConstructionOrders().isEmpty());
        });
    }
    @Test void savedOrdersRenderDowntimeProgressAndStartingRequiresAFeasibleQuote() throws Exception {
        var order = new ShipConstructionOrder("refit", "owner", "target", "sol", "yard", 25, 100, Map.of(), Map.of())
                .withRefit(new ShipRefitPlan("ship", "source", Map.of()));
        var state = GameState.builder().shipConstructionOrders(List.of(order)).build();
        var controller = new com.spaceconquest.control.HumanController();
        fx(() -> {
            var card = ShipRefitCard.create(state, "owner", () -> state, controller); var content = (VBox) card.getContent();
            assertTrue(((Label) content.lookup("#refit-progress")).getText().contains("25.0%"));
            assertTrue(((Label) content.lookup("#refit-progress")).getText().contains("unavailable"));
            assertTrue(((Button) content.lookup("#refit-start")).isDisabled());
            assertEquals(0, controller.getCommandQueue().size()); assertEquals(List.of(order), state.shipConstructionOrders());
        });
    }
}
