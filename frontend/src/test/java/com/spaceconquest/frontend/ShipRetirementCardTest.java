package com.spaceconquest.frontend;
import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
class ShipRetirementCardTest {
    @BeforeAll static void start() throws Exception {
        var ready = new CountDownLatch(1); try { Platform.startup(ready::countDown); } catch (IllegalStateException running) { ready.countDown(); }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
    }
    private void fx(Runnable action) throws Exception {
        var done = new CompletableFuture<Void>(); Platform.runLater(() -> { try { action.run(); done.complete(null); } catch (Throwable failure) { done.completeExceptionally(failure); } }); done.get(10, TimeUnit.SECONDS);
    }
    private ShipRetirementQuote quote(double credits) {
        return new ShipRetirementQuote("ship", "model", "yard", "owner", new ShipRetirementTechnology(.25, .6, .5, .5),
                new ShipRetirementSalvage.Sale("hub", Map.of("steel", 20.0), credits, List.of()), 10, 2, 3, List.of());
    }
    @Test void recoveryBreakdownAndStaleBackgroundGuardsUseOnlySnapshots() throws Exception {
        var state = GameState.builder().build(); var current = new AtomicReference<>(state); var box = new VBox();
        var old = new CompletableFuture<ShipRetirementQuote>(); var newer = new CompletableFuture<ShipRetirementQuote>();
        fx(() -> { ShipRetirementCard.refresh(box, state, current::get, () -> old, quote -> {}); ShipRetirementCard.refresh(box, state, current::get, () -> newer, quote -> {}); });
        newer.complete(quote(50)); old.complete(quote(999));
        fx(() -> { assertTrue(((Label) box.lookup("#retirement-cost")).getText().contains("50.00")); assertTrue(((Label) box.lookup("#retirement-materials")).getText().contains("steel=20.0")); });
        var stale = new CompletableFuture<ShipRetirementQuote>(); fx(() -> ShipRetirementCard.refresh(box, state, current::get, () -> stale, quote -> {}));
        current.set(state.withTurn(1)); stale.complete(quote(999)); fx(() -> assertNotNull(box.lookup("#retirement-loading")));
    }
    @Test void cancellationStagesTrackedCommandAndRejectsOldSnapshotActions() throws Exception {
        var order = new ShipConstructionOrder("refit", "owner", "model", "sol", "yard", 20, 100, Map.of("steel", 10.0), Map.of("steel", 2.0))
                .withRefit(new ShipRefitPlan("ship", "old", Map.of()));
        var state = GameState.builder().shipConstructionOrders(List.of(order)).build(); var current = new AtomicReference<>(state); var controller = new HumanController();
        fx(() -> {
            var card = ShipRefitCard.create(state, "owner", current::get, controller); var box = (VBox) card.getContent();
            assertTrue(((Label) box.lookup("#yard-work-spent")).getText().contains("steel=2.0"));
            var retirement = (TitledPane) box.getChildren().stream().filter(node -> node instanceof TitledPane).findFirst().orElseThrow();
            assertTrue(((Button) ((VBox) retirement.getContent()).lookup("#retirement-start")).isDisabled());
            current.set(state.withTurn(1)); ((Button) box.lookup("#yard-work-cancel")).fire(); assertEquals(0, controller.getCommandQueue().size());
            current.set(state); ((Button) box.lookup("#yard-work-cancel")).fire(); assertEquals(1, controller.getCommandQueue().size());
        });
        var result = controller.getCommandQueue().drainAndExecute(state); assertTrue(result.shipConstructionOrders().isEmpty());
        assertEquals(state.commercialHubs(), result.commercialHubs());
    }
}
