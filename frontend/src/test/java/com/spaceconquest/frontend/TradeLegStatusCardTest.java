package com.spaceconquest.frontend;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.logistics.TradeRoute;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class TradeLegStatusCardTest {
    @BeforeAll static void initializeJavaFx() {
        try { Platform.startup(() -> {}); } catch (IllegalStateException ignored) { /* Toolkit is already running. */ }
    }

    @Test void asynchronousStatusExplainsMissingCarrierWithoutChangingSnapshot() throws Exception {
        var state = GameState.builder().build();
        var route = new TradeRoute("route", "Route", "owner", "source", "destination", "steel", 20, 0, 100,
                List.of("missing"), 0, true);
        CompletableFuture<String> rendered = new CompletableFuture<>();
        var task = new FutureTask<Void>(() -> {
            var label = TradeLegStatusCard.create(state, route);
            assertEquals("Checking next-leg supplies...", label.getText());
            label.textProperty().addListener((observable, old, value) -> rendered.complete(value));
            return null;
        });
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
        assertEquals("Waiting: no assigned carrier is available.", rendered.get(10, TimeUnit.SECONDS));
        assertTrue(state.fleets().isEmpty());
    }

    @Test void roamingStatusUsesThePersistedTickDecision() {
        var route = new TradeRoute("route", "Route", "owner", "source", "destination", "steel", 20, 0, 100,
                List.of("ship"), 0, true).withRoaming(true).withStatus("Waiting at port: no profitable safe trade.");
        assertEquals(route.status(), TradeLegStatusCard.status(GameState.builder().build(), route));
    }
}
