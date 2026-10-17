package com.spaceconquest.frontend;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class FleetWeaponStatusCardTest {
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
    private List<FleetWeaponStatusCard.Entry> status(String reason) {
        var weapon = new ShipWeaponReadiness.Weapon("mod_ship_gun", "Cannon", List.of(new ShipWeaponReadiness.Mount(1, 1),
                new ShipWeaponReadiness.Mount(2, 0)), "cannon_round", 5, 9, 1, List.of(1, 2), 1, reason);
        var readiness = new ShipWeaponReadiness("ship", List.of(weapon), "Current-store preview; range and targeting checked in combat.");
        var quote = new AmmunitionResupplyQuote("ship", "mod_ship_gun", "earth", "cannon_round", 5, 20, 10.0, 2.0,
                "Purchase plus orbital lift", new LoadOrbitalCargoCommand("ship", "earth", "cannon_round", 5));
        return List.of(new FleetWeaponStatusCard.Entry(readiness, List.of(quote)));
    }
    @Test void panelShowsSharedRoundsDisabledMountsCostsAndQueuesOneTrackedPurchase() throws Exception {
        var state = GameState.builder().build(); var queued = new ArrayList<GameCommand>();
        var result = new CompletableFuture<CommandOutcome>(); var feedback = new AtomicReference<Label>();
        fx(() -> {
            var box = new VBox(); feedback.set(new Label());
            FleetWeaponStatusCard.render(box, status("Limited by shared ammunition"), state, () -> state,
                    command -> { queued.add(command); return result; }, feedback.get());
            assertTrue(((Label) box.lookup("#weapon-ready-ship-mod_ship_gun")).getText().contains("disabled"));
            assertTrue(((Label) box.lookup("#weapon-ammo-ship-mod_ship_gun")).getText().contains("1 whole round"));
            assertTrue(((Label) box.lookup("#weapon-ammo-ship-mod_ship_gun")).getText().contains("[1, 2]"));
            var price = ((Label) box.lookup("#weapon-resupply-ship-mod_ship_gun")).getText();
            assertTrue(price.contains("20.00 kg")); assertTrue(price.contains("purchase 10.00")); assertTrue(price.contains("loading 2.00"));
            var buy = (Button) box.lookup("#weapon-load-ship-mod_ship_gun"); buy.fire(); buy.fire();
            assertEquals(1, queued.size()); assertEquals(new LoadOrbitalCargoCommand("ship", "earth", "cannon_round", 5), queued.getFirst());
            assertTrue(buy.isDisabled()); assertTrue(feedback.get().getText().contains("Queued"));
        });
        result.complete(CommandOutcome.EXECUTED);
        fx(() -> assertTrue(feedback.get().getText().contains("completed")));
        assertTrue(state.fleets().isEmpty());
    }
    @Test void staleRequestsAndSnapshotsCannotPublishOrQueueOldQuotes() throws Exception {
        var state = GameState.builder().build(); var current = new AtomicReference<>(state);
        var old = new CompletableFuture<List<FleetWeaponStatusCard.Entry>>(); var newer = new CompletableFuture<List<FleetWeaponStatusCard.Entry>>();
        var box = new AtomicReference<VBox>(); var queued = new ArrayList<GameCommand>();
        fx(() -> {
            box.set(new VBox());
            FleetWeaponStatusCard.refresh(box.get(), state, current::get, () -> old, null, new Label());
            FleetWeaponStatusCard.refresh(box.get(), state, current::get, () -> newer,
                    command -> { queued.add(command); return CompletableFuture.completedFuture(CommandOutcome.EXECUTED); }, new Label());
        });
        newer.complete(status("Ready")); old.complete(status("Old quote"));
        fx(() -> assertTrue(((Label) box.get().lookup("#weapon-ready-ship-mod_ship_gun")).getText().contains("Ready")));
        current.set(state.withTurn(1));
        fx(() -> { ((Button) box.get().lookup("#weapon-load-ship-mod_ship_gun")).fire(); assertTrue(queued.isEmpty()); });
        var pending = new CompletableFuture<List<FleetWeaponStatusCard.Entry>>();
        fx(() -> FleetWeaponStatusCard.refresh(box.get(), state, current::get, () -> pending, null, new Label()));
        pending.complete(status("Stale"));
        fx(() -> { assertNotNull(box.get().lookup("#weapon-loading")); assertNull(box.get().lookup("#weapon-ready-ship-mod_ship_gun")); });
    }
    @Test void expansionIsLazyPersistsAndCalculationFailuresAreRecoverable() throws Exception {
        var fleet = new Fleet("fleet", "Fleet", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of());
        var state = GameState.builder().fleets(List.of(fleet)).build(); var expanded = new HashSet<String>();
        var box = new AtomicReference<VBox>();
        fx(() -> {
            var card = FleetWeaponStatusCard.create(state, fleet, "owner", null, new Label(), () -> state, expanded);
            assertFalse(card.isExpanded()); assertTrue(((VBox) card.getContent()).getChildren().isEmpty());
            card.setExpanded(true); assertTrue(expanded.contains("fleet"));
            var refreshed = FleetWeaponStatusCard.create(state, fleet, "owner", null, new Label(), () -> state, expanded);
            assertTrue(refreshed.isExpanded()); refreshed.setExpanded(false); assertFalse(expanded.contains("fleet"));
            box.set(new VBox()); FleetWeaponStatusCard.refresh(box.get(), state, () -> state,
                    () -> CompletableFuture.failedFuture(new IllegalStateException("fixture")), null, new Label());
        });
        fx(() -> assertTrue(((Label) box.get().lookup("#weapon-error")).getText().contains("unavailable")));
    }
    @Test void blockedQuotesDisablePurchasesAndTickRejectionExplainsChangedAvailability() throws Exception {
        var state = GameState.builder().build(); var feedback = new AtomicReference<Label>();
        var pending = new CompletableFuture<CommandOutcome>();
        fx(() -> {
            feedback.set(new Label()); var box = new VBox(); var entry = status("Empty magazine").getFirst();
            var blocked = new AmmunitionResupplyQuote("ship", "mod_ship_gun", "earth", "cannon_round", 5, 4, 10.0, null,
                    "Local stock cannot supply one whole round", null);
            FleetWeaponStatusCard.render(box, List.of(new FleetWeaponStatusCard.Entry(entry.readiness(), List.of(blocked))),
                    state, () -> state, command -> { fail("Blocked purchase must not queue"); return pending; }, feedback.get());
            assertTrue(((Button) box.lookup("#weapon-load-ship-mod_ship_gun")).isDisabled());
            assertTrue(((Label) box.lookup("#weapon-resupply-ship-mod_ship_gun")).getText().contains("whole round"));
            FleetWeaponStatusCard.render(box, status("Ready"), state, () -> state, command -> pending, feedback.get());
            ((Button) box.lookup("#weapon-load-ship-mod_ship_gun")).fire();
        });
        pending.complete(CommandOutcome.REJECTED);
        fx(() -> assertTrue(feedback.get().getText().contains("stock, funds or ship access may have changed")));
    }
}
