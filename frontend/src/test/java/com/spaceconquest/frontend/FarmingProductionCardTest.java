package com.spaceconquest.frontend;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;
import javafx.application.Platform;
import javafx.scene.control.Label;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class FarmingProductionCardTest {
    @BeforeAll static void start() throws Exception {
        var ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); } catch (IllegalStateException running) { ready.countDown(); }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
    }
    @Test void rendersSavedGrowingProgressAndSiteStatusWithoutChangingTheSnapshot() throws Exception {
        var farm = new IndustrialFacility("farm", "earth", "industrial_soil_cultivation", "owner", IndustrialFacility.PUBLIC_STATE, 1, 1, "farmer", false, 0);
        var growth = new FarmingState(farm.applicationId(), 10, 39, .25);
        var state = GameState.builder().turn(39).solarSystems(DataModelLoader.loadSolarSystems()).industrialFacilities(List.of(farm))
                .industryAccounts(List.of(IndustryAccount.empty("farm").withFarmingState(growth))).build();
        var done = new CompletableFuture<Void>();
        Platform.runLater(() -> {
            try {
                var card = new FarmingProductionCard(state, farm);
                var text = card.getChildren().stream().map(n -> ((Label) n).getText()).reduce("", (a, b) -> a + b);
                assertTrue(text.contains("6.0 ha")); assertTrue(text.contains("30/120 days")); assertTrue(text.contains("0.250 paid"));
                assertTrue(text.contains("sold directly"));
                var missing = new IndustrialFacility("missing", "unknown", farm.applicationId(), "owner", IndustrialFacility.PUBLIC_STATE, 1, 1, "farmer", false, 0);
                assertTrue(((Label) new FarmingProductionCard(state, missing).getChildren().getLast()).getText().contains("paused"));
                assertEquals(growth, state.industryAccounts().getFirst().farmingState());
                var factory = new IndustrialFacility("food", "earth", "food_processing", "owner", IndustrialFacility.PUBLIC_STATE, 1, 10, "industrial_worker", false, 0);
                assertTrue(new FarmingProductionCard(state, factory).getChildren().isEmpty());
                done.complete(null);
            } catch (Throwable failure) { done.completeExceptionally(failure); }
        });
        done.get(10, TimeUnit.SECONDS);
    }
    @Test void rendersEnclosureUtilitiesAndReservedStationSpaceFromApplicationData() throws Exception {
        var farm = new IndustrialFacility("farm", "station", "hydroponic_growth_arrays", "owner", "PUBLIC_STATE", 1, 4, "farmer", false, 0);
        var station = new com.spaceconquest.engine.macrostructure.OrbitalStation("station", "Station", "sol", "earth", "owner", "PUBLIC_STATE", 12,
                List.of(), Map.of(), 0, 0, 0, 0, 100, 100, "steel", 1, true);
        var state = GameState.builder().industrialFacilities(List.of(farm)).orbitalStations(List.of(station)).build();
        var done = new CompletableFuture<Void>();
        Platform.runLater(() -> {
            try {
                var card = new FarmingProductionCard(state, farm);
                var text = card.getChildren().stream().map(n -> ((Label) n).getText()).reduce("", (a, b) -> a + b);
                assertTrue(text.contains("2000 m³")); assertTrue(text.contains("250 m² footprint"));
                assertTrue(text.contains("98.0%")); assertTrue(text.contains("lighting 44800 kWh"));
                assertTrue(text.contains("Reserved station slots: 4")); assertTrue(text.contains("890.9 kg purified water"));
                assertEquals(0, state.orbitalStations().getFirst().getAllocatedSlots());
                done.complete(null);
            } catch (Throwable failure) { done.completeExceptionally(failure); }
        });
        done.get(10, TimeUnit.SECONDS);
    }
}
