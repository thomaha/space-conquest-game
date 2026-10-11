package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
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

class FleetShieldPolicyCardTest {
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
    private GameState world() {
        var fleet = new Fleet("fleet", "Fleet", "owner", "sol", "", 0, 0, 0, false, "PASSIVE",
                List.of(new ShipInstance("ship", "unknown", "owner", 1000, 7, 100, Map.of())));
        return GameState.builder().fleets(List.of(fleet)).build();
    }
    @Test void selectionQueuesPolicyAndDisplaysAuthoritativeEligibilityAfterRefresh() throws Exception {
        var state = world(); var fleet = state.fleets().getFirst(); var controller = new HumanController();
        var box = new AtomicReference<VBox>();
        fx(() -> {
            box.set(FleetShieldPolicyCard.create(state, fleet, "owner", controller, new Label(), () -> state));
            @SuppressWarnings("unchecked") var modes = (ComboBox<FleetShieldPolicy.Mode>) box.get().lookup("#shield-policy-mode");
            modes.setValue(FleetShieldPolicy.Mode.CONSERVE_ENERGY);
            ((Button) box.get().lookup("#shield-policy-apply")).fire();
            assertEquals(FleetShieldPolicy.Mode.AUTOMATIC, fleet.shieldPolicy());
        });
        var changed = controller.getCommandQueue().drainAndExecute(state);
        assertEquals(FleetShieldPolicy.Mode.CONSERVE_ENERGY, changed.fleets().getFirst().shieldPolicy());
        fx(() -> box.set(FleetShieldPolicyCard.create(changed, changed.fleets().getFirst(), "owner", controller, new Label(), () -> changed)));
        fx(() -> assertTrue(((Label) box.get().lookup("#shield-policy-reason")).getText().contains("conserve energy")));
        fx(() -> {
            var foreign = FleetShieldPolicyCard.create(state, fleet, "foreign", controller, new Label(), () -> state);
            assertTrue(((Button) foreign.lookup("#shield-policy-apply")).isDisabled());
        });
    }
    @Test void policyReasonsAreExplicitAndStaleResponsesCannotReplaceNewerStatus() throws Exception {
        var state = world(); var fleet = state.fleets().getFirst();
        assertFalse(FleetShieldPolicy.decision(state, fleet, true).recharge());
        assertTrue(FleetShieldPolicy.decision(state, fleet, true).reason().contains("no live or recent"));
        assertTrue(FleetShieldPolicy.decision(state, fleet.withShieldPolicy(FleetShieldPolicy.Mode.ALWAYS_CHARGE), true).reason().contains("always charge"));
        var current = new AtomicReference<>(state); var label = new Label("Initial");
        var old = new CompletableFuture<String>(); var newer = new CompletableFuture<String>();
        fx(() -> { FleetShieldPolicyCard.refresh(label, state, current::get, () -> old); FleetShieldPolicyCard.refresh(label, state, current::get, () -> newer); });
        newer.complete("Current"); old.complete("Stale");
        fx(() -> assertEquals("Current", label.getText()));
        var pending = new CompletableFuture<String>();
        fx(() -> FleetShieldPolicyCard.refresh(label, state, current::get, () -> pending));
        current.set(state.withTurn(1)); pending.complete("Wrong snapshot");
        fx(() -> assertEquals("Current", label.getText()));
    }
}
