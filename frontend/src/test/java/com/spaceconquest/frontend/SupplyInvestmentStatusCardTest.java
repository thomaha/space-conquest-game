package com.spaceconquest.frontend;

import com.spaceconquest.control.ai.SupplyInvestmentStatus;
import com.spaceconquest.engine.GameState;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class SupplyInvestmentStatusCardTest {
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
    private SupplyInvestmentStatus status(String reason) {
        return new SupplyInvestmentStatus(List.of(new SupplyInvestmentStatus.Row("earth", "purified_water", "surface_water_treatment", "Expand supplier", reason)));
    }
    @Test void rowsExposeSiteProductActionAndBlockerWithoutMutatingControls() throws Exception {
        fx(() -> {
            var box = new VBox(); SupplyInvestmentStatusCard.render(box, status("Insufficient uncommitted generation for expansion"));
            String text = ((Label) box.lookup("#investment-row")).getText();
            assertTrue(text.contains("earth")); assertTrue(text.contains("purified water"));
            assertTrue(text.contains("Expand supplier")); assertTrue(text.contains("generation"));
            assertTrue(box.getChildren().stream().allMatch(node -> node instanceof Label));
        });
    }
    @Test void newerRequestAndNewerSnapshotDiscardStaleAsyncResults() throws Exception {
        var state = new GameState(); var current = new AtomicReference<>(state); var box = new AtomicReference<VBox>();
        var old = new CompletableFuture<SupplyInvestmentStatus>(); var newer = new CompletableFuture<SupplyInvestmentStatus>();
        fx(() -> {
            box.set(new VBox()); SupplyInvestmentStatusCard.refresh(box.get(), state, current::get, () -> old);
            SupplyInvestmentStatusCard.refresh(box.get(), state, current::get, () -> newer);
        });
        newer.complete(status("Current")); old.complete(status("Stale"));
        fx(() -> assertTrue(((Label) box.get().lookup("#investment-row")).getText().endsWith("Current")));
        var pending = new CompletableFuture<SupplyInvestmentStatus>();
        fx(() -> SupplyInvestmentStatusCard.refresh(box.get(), state, current::get, () -> pending));
        current.set(state.withTurn(1)); pending.complete(status("Wrong snapshot"));
        fx(() -> assertNotNull(box.get().lookup("#investment-loading")));
        fx(() -> SupplyInvestmentStatusCard.refresh(box.get(), current.get(), current::get,
                () -> CompletableFuture.failedFuture(new IllegalStateException("fixture"))));
        fx(() -> assertNotNull(box.get().lookup("#investment-error")));
    }
}
