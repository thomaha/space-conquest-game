package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.CommandQueue;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.*;
import com.spaceconquest.engine.industry.IndustrialFacility;
import javafx.application.Platform;
import javafx.scene.control.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class BusinessFinanceCardTest {
    @BeforeAll static void startFx() { try { Platform.startup(() -> {}); } catch (IllegalStateException alreadyStarted) { } }
    private void fx(Runnable action) throws Exception {
        var done = new CompletableFuture<Void>();
        Platform.runLater(() -> { try { action.run(); done.complete(null); } catch (Throwable failure) { done.completeExceptionally(failure); } });
        done.get(10, TimeUnit.SECONDS);
    }
    @Test void reserveReceiptsRenderFromTheSnapshotAndReleaseOnlyThroughTheCommandQueue() throws Exception {
        var owner = new Empire("owner", "Owner", "human", "Individualist", 1000, 0, List.of("sol"), List.of(), Map.of(), List.of(), List.of());
        var facility = new IndustrialFacility("factory", "earth", "food_processing", "owner", IndustrialFacility.PUBLIC_STATE, 1, 10, "industrial_worker", false, 0);
        var planet = new Planet("earth", "Earth", "", 1, 1, 1, 0, 100, "terrestrial", "nitrogen_oxygen", true, .3, List.of(), List.of(), List.of());
        var hub = new CommercialHub("hub", "earth", 0, 10000, 0, 10, Map.of("food_matrix", new MarketOrder("food_matrix", 0, 100, 4, 0)));
        var ledger = BusinessLedger.empty().withAccount("owner", BusinessAccount.empty().withReserve(100)).withDecisions(Map.of("factory",
                new BusinessOperatingPlan.Decision(0, false, "Inputs unavailable", 0, 0,
                        List.of(new ProductionConstraints.Constraint("Material", "harvested_crops", 100, 0)))));
        var state = GameState.builder().empires(List.of(owner)).industrialFacilities(List.of(facility)).businessLedger(ledger).commercialHubs(List.of(hub))
                .solarSystems(List.of(new SolarSystem("sol", "Sol", "", 0, 0, 0, 1, 1, "Yellow", List.of(planet), List.of()))).build();
        var queue = new CommandQueue();
        fx(() -> {
            var card = new BusinessFinanceCard(state, facility, "owner", new HumanController(queue), () -> state);
            new javafx.scene.Scene(card); card.applyCss(); card.layout();
            assertTrue(((Label) card.lookup("#business-reserve")).getText().contains("100.00"));
            assertNotNull(card.lookup("#supply-contract-create"));
            assertTrue(card.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                    .anyMatch(label -> label.getText().contains("harvested crops; available 0.00 / required 100.00")));
            ((Button) card.lookup("#business-release-reserve")).fire();
            assertEquals(100, state.businessLedger().account("owner").replacementReserveCredits());
            assertEquals(1000, state.empires().getFirst().treasuryCredits());
        });
        var end = queue.drainAndExecute(state);
        assertEquals(0, end.businessLedger().account("owner").replacementReserveCredits());
        assertEquals(1100, end.empires().getFirst().treasuryCredits());
    }
}
