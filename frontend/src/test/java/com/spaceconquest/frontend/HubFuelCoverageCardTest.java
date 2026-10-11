package com.spaceconquest.frontend;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.logistics.HubFuelStockSuggestions;
import com.spaceconquest.engine.market.HubStockTarget;
import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class HubFuelCoverageCardTest {
    @BeforeAll static void initializeJavaFx() {
        try { Platform.startup(() -> {}); } catch (IllegalStateException ignored) { /* Toolkit already running. */ }
    }

    private <T> T fx(Callable<T> work) throws Exception {
        var task = new FutureTask<>(work);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    private GameState world() {
        var hub = new CommercialHub("hub", "station", 0, 1000, 183, 10, Map.of(
                "liquid_hydrogen", new MarketOrder("liquid_hydrogen", 17, 0, 2, 0),
                "liquid_oxygen", new MarketOrder("liquid_oxygen", 166, 0, 2, 0)), Map.of(
                "liquid_hydrogen", new HubStockTarget(34, 51, true),
                "liquid_oxygen", new HubStockTarget(166, 249, true)));
        return GameState.builder().commercialHubs(List.of(hub)).build();
    }

    @Test void asynchronouslyRendersCoverageAndRequestsWithoutChangingTheSnapshot() throws Exception {
        var state = world();
        var pending = new ArrayList<Runnable>();
        var selected = new AtomicReference<String>();
        var card = fx(() -> {
            var box = HubFuelCoverageCard.create(state, state.commercialHubs().getFirst(), null, selected::set, pending::add);
            assertEquals("Checking fuel coverage...", ((Label) box.lookup("#hub-fuel-coverage-details")).getText());
            return box;
        });
        assertEquals("mod_hydrolox_rocket", selected.get());
        pending.getFirst().run();
        var preview = fx(() -> {
            var text = ((Label) card.lookup("#hub-fuel-coverage-details")).getText();
            assertTrue(text.contains("Usable on hand: 100.000 kg | With paid inbound: 100.000 kg"));
            assertTrue(text.contains("Limiting ingredients on hand: liquid hydrogen"));
            assertTrue(text.contains("Reorder 34.000 kg | Target 51.000 kg"));
            assertTrue(text.contains("Outstanding request 34.000 kg"));
            assertTrue(text.contains("No completed demand history"));
            var warnings = ((Label) card.lookup("#hub-fuel-coverage-warnings")).getText();
            assertTrue(warnings.contains("No stock at an accessible source hub: liquid hydrogen"));
            assertTrue(warnings.contains("No active supplier route"));
            assertEquals(17, state.commercialHubs().getFirst().activeOrders().get("liquid_hydrogen").supplyKg());
            assertTrue(state.commercialHubs().getFirst().stockTargets().get("liquid_hydrogen").refillRequested());
            var surface = new VBox(card);
            surface.setStyle("-fx-background-color: #283c64; -fx-padding: 12;");
            new Scene(surface, 760, 620);
            surface.applyCss();
            surface.layout();
            var image = surface.snapshot(null, null);
            int width = (int) image.getWidth(), height = (int) image.getHeight();
            var buffered = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
                buffered.setRGB(x, y, image.getPixelReader().getArgb(x, y));
            return buffered;
        });
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("target"));
        javax.imageio.ImageIO.write(preview, "png", java.nio.file.Path.of("target/hub-fuel-coverage-preview.png").toFile());
    }

    @Test void switchingFuelDiscardsAnOlderBackgroundResponse() throws Exception {
        var state = world();
        var pending = new ArrayList<Runnable>();
        var card = fx(() -> {
            var box = HubFuelCoverageCard.create(state, state.commercialHubs().getFirst(), "mod_hydrolox_rocket", ignored -> {}, pending::add);
            @SuppressWarnings("unchecked")
            var fuel = (ComboBox<HubFuelStockSuggestions.Fuel>) box.lookup("#hub-fuel-coverage-mixture");
            fuel.setValue(fuel.getItems().stream().filter(item -> item.id().equals("mod_chemical_rocket")).findFirst().orElseThrow());
            return box;
        });
        assertEquals(2, pending.size());
        pending.getLast().run();
        var latest = fx(() -> ((Label) card.lookup("#hub-fuel-coverage-details")).getText());
        assertTrue(latest.contains("RP-1 kerosene"));
        assertTrue(latest.contains("Usable on hand: 0.000 kg"));
        pending.getFirst().run();
        assertEquals(latest, fx(() -> ((Label) card.lookup("#hub-fuel-coverage-details")).getText()));
    }

    @Test void displaysGrossRollingRatesAndStockoutWarningsFromCompletedDays() throws Exception {
        var original = world();
        var hub = original.commercialHubs().getFirst().observeDay(1);
        var orders = new HashMap<>(hub.activeOrders());
        orders.put("liquid_hydrogen", new MarketOrder("liquid_hydrogen", 0, 0, 2, 0));
        hub = hub.withInventory(1, 166, orders).observeDay(2);
        orders.put("liquid_hydrogen", new MarketOrder("liquid_hydrogen", 17, 0, 2, 0));
        hub = hub.withInventory(2, 183, orders);
        var state = original.withCommercialHubs(List.of(hub)).withTurn(3);
        var pending = new ArrayList<Runnable>();
        var card = fx(() -> HubFuelCoverageCard.create(state, state.commercialHubs().getFirst(), "mod_hydrolox_rocket", ignored -> {}, pending::add));
        pending.getFirst().run();
        fx(() -> {
            var details = ((Label) card.lookup("#hub-fuel-coverage-details")).getText();
            assertTrue(details.contains("Rolling withdrawals 8.500 kg/day | Deliveries 8.500 kg/day | 2 observed days | 2 stockout days"));
            assertTrue(details.contains("last 30 completed days"));
            assertTrue(((Label) card.lookup("#hub-fuel-coverage-warnings")).getText().contains("may understate demand"));
            return null;
        });
    }
}
