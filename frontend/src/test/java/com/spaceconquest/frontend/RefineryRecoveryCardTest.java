package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class RefineryRecoveryCardTest {
    @BeforeAll static void start() throws Exception {
        var ready = new CountDownLatch(1); try { Platform.startup(ready::countDown); } catch (IllegalStateException running) { ready.countDown(); }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
    }
    private void fx(Runnable action) throws Exception {
        var done = new CompletableFuture<Void>(); Platform.runLater(() -> { try { action.run(); done.complete(null); } catch (Throwable failure) { done.completeExceptionally(failure); } });
        done.get(10, TimeUnit.SECONDS);
    }
    @Test void renderingReadsOwnedSnapshotResidueAndSelectionQueuesWithoutChangingTheLot() throws Exception {
        var state = world(); var current = new AtomicReference<>(state); var controller = new HumanController();
        fx(() -> {
            var card = new RefineryRecoveryCard(state, state.industrialFacilities().getFirst(), "owner", controller, current::get);
            assertTrue(((Label) card.lookup("#refinery-storage")).getText().contains("100.000"));
            var sources = (ComboBox<?>) card.lookup("#refinery-source"); sources.getSelectionModel().select(1);
            var button = (Button) card.lookup("#refinery-select");
            current.set(state.withTurn(1)); button.fire(); assertEquals(0, controller.getCommandQueue().size());
            current.set(state); button.fire(); assertEquals(1, controller.getCommandQueue().size());
            assertTrue(button.isDisabled()); assertNull(RefineryProcess.inventory(state, "refinery").selectedTailingsId());
        });
        var result = controller.getCommandQueue().drainAndExecute(state);
        assertEquals("tailings", RefineryProcess.inventory(result, "refinery").selectedTailingsId());
        assertEquals(RefineryProcess.inventory(state, "refinery").tailings(), RefineryProcess.inventory(result, "refinery").tailings());
        fx(() -> assertTrue(new RefineryRecoveryCard(state, state.industrialFacilities().getFirst(), "foreign", controller, current::get).getChildren().isEmpty()));
    }
    private GameState world() {
        var owner = new Empire("owner", "Owner", "human", "Individualist", 100000, 0, List.of(), List.of(), Map.of(),
                List.of("industrial_production", "rare_earth_refining"), List.of());
        var inventory = new RefineryState(null, null, List.of(new RefineryLot("tailings", "original", Map.of("Ag", 1.0, "F", 99.0))), null);
        return GameState.builder().empires(List.of(owner)).industrialFacilities(List.of(new IndustrialFacility("refinery", "earth", "rare_earth_refining", "owner",
                        IndustrialFacility.PUBLIC_STATE, 1, 10, "industrial_worker", false, 0)))
                .industryAccounts(List.of(IndustryAccount.empty("refinery").withRefineryState(inventory))).build();
    }
}
