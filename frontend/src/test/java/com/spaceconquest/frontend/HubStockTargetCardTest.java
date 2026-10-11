package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.market.HubStockTarget;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class HubStockTargetCardTest {
    private GameState fuelWorld() {
        var state = world();
        var source = new CommercialHub("source", "elsewhere", 0, 1000, 1000, 10, Map.of(
                "liquid_hydrogen", new MarketOrder("liquid_hydrogen", 170, 0, .01, 0),
                "liquid_oxygen", new MarketOrder("liquid_oxygen", 830, 0, .01, 0)));
        return state.withCommercialHubs(List.of(state.commercialHubs().getFirst(), source));
    }

    private void planningInputs(javafx.scene.layout.VBox card, String departure) {
        ((TextField) card.lookup("#hub-fuel-plan-departure")).setText(departure);
        ((TextField) card.lookup("#hub-fuel-plan-daily")).setText("10");
        ((TextField) card.lookup("#hub-fuel-plan-cycle")).setText("3");
        ((TextField) card.lookup("#hub-fuel-plan-safety")).setText("2");
    }

    @Test void reviewsAndQueuesBothMixtureIngredientsAsOneCommand() throws Exception {
        fx(() -> {
            var state = fuelWorld();
            var controller = new HumanController();
            var card = HubStockTargetCard.create(state, state.commercialHubs().getFirst(), "owner", controller, new Label());
            planningInputs(card, "100");
            var apply = (Button) card.lookup("#hub-fuel-plan-apply");
            assertTrue(apply.isDisabled());
            ((Button) card.lookup("#hub-fuel-plan-preview")).fire();
            assertFalse(apply.isDisabled());
            assertEquals(0, controller.getCommandQueue().size());
            assertTrue(((Label) card.lookup("#hub-fuel-plan-result")).getText().contains("liquid_oxygen"));
            apply.fire();
            assertEquals(1, controller.getCommandQueue().size());
            assertTrue(state.commercialHubs().getFirst().stockTargets().isEmpty());
            var targets = controller.getCommandQueue().drainAndExecute(state).commercialHubs().getFirst().stockTargets();
            assertEquals(25.5, targets.get("liquid_hydrogen").reorderThresholdKg(), 1e-6);
            assertEquals(42.5, targets.get("liquid_hydrogen").targetStockKg(), 1e-6);
            assertEquals(207.5, targets.get("liquid_oxygen").targetStockKg(), 1e-6);
            return null;
        });
    }

    @Test void editingInputsInvalidatesReviewAndOversizedPlansRemainUnqueued() throws Exception {
        fx(() -> {
            var state = fuelWorld();
            var controller = new HumanController();
            var card = HubStockTargetCard.create(state, state.commercialHubs().getFirst(), "owner", controller, new Label());
            planningInputs(card, "100");
            var preview = (Button) card.lookup("#hub-fuel-plan-preview");
            var apply = (Button) card.lookup("#hub-fuel-plan-apply");
            preview.fire();
            assertFalse(apply.isDisabled());
            ((TextField) card.lookup("#hub-fuel-plan-cycle")).setText("30");
            assertTrue(apply.isDisabled());
            planningInputs(card, "1000");
            preview.fire();
            assertTrue(apply.isDisabled());
            assertTrue(((Label) card.lookup("#hub-fuel-plan-result")).getText().contains("storage"));
            planningInputs(card, "NaN");
            preview.fire();
            assertTrue(apply.isDisabled());
            assertEquals(0, controller.getCommandQueue().size());
            return null;
        });
    }

    @Test void asynchronousEstimatesExplainMissingSupplierDataWithoutMutatingState() throws Exception {
        var completed = new java.util.concurrent.CountDownLatch(1);
        var state = fuelWorld();
        var card = fx(() -> {
            var box = HubStockTargetCard.create(state, state.commercialHubs().getFirst(), "owner", new HumanController(), new Label());
            var estimate = (Button) box.lookup("#hub-fuel-plan-estimate");
            estimate.disabledProperty().addListener((observable, oldValue, disabled) -> { if (!disabled) completed.countDown(); });
            estimate.fire();
            return box;
        });
        assertTrue(completed.await(10, TimeUnit.SECONDS));
        fx(() -> {
            assertEquals("0.0", ((TextField) card.lookup("#hub-fuel-plan-cycle")).getText());
            assertTrue(((Button) card.lookup("#hub-fuel-plan-apply")).isDisabled());
            assertTrue(state.commercialHubs().getFirst().stockTargets().isEmpty());
            return null;
        });
    }

    @Test void editsMadeWhileEstimatingAreNotOverwrittenByTheBackgroundResponse() throws Exception {
        var completed = new java.util.concurrent.CountDownLatch(1);
        var state = fuelWorld();
        var card = fx(() -> {
            var box = HubStockTargetCard.create(state, state.commercialHubs().getFirst(), "owner", new HumanController(), new Label());
            var estimate = (Button) box.lookup("#hub-fuel-plan-estimate");
            estimate.disabledProperty().addListener((observable, oldValue, disabled) -> { if (!disabled) completed.countDown(); });
            estimate.fire();
            ((TextField) box.lookup("#hub-fuel-plan-departure")).setText("777");
            ((TextField) box.lookup("#hub-fuel-plan-cycle")).setText("9");
            return box;
        });
        assertTrue(completed.await(10, TimeUnit.SECONDS));
        fx(() -> {
            assertEquals("777", ((TextField) card.lookup("#hub-fuel-plan-departure")).getText());
            assertEquals("9", ((TextField) card.lookup("#hub-fuel-plan-cycle")).getText());
            assertTrue(((Button) card.lookup("#hub-fuel-plan-apply")).isDisabled());
            return null;
        });
    }

    @BeforeAll static void initializeJavaFx() {
        try { Platform.startup(() -> {}); }
        catch (IllegalStateException ignored) { /* Toolkit already initialized. */ }
    }

    private <T> T fx(Callable<T> work) throws Exception {
        var task = new FutureTask<>(work);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    private GameState world() {
        var hub = new CommercialHub("hub", "station", 0, 1000, 0, 10,
                Map.of("steel", new MarketOrder("steel", 0, 0, 2, 0)));
        var station = new OrbitalStation("station", "Station", "sol", "", "owner", "PUBLIC_STATE", 10,
                List.of(), Map.of(), 0, 0, 0, 0, 100, 100, "steel", 0, true);
        var owner = new Empire("owner", "Owner", "human", "Individualist", 1000, 0,
                List.of("sol"), List.of(), Map.of(), List.of(), List.of());
        return GameState.builder().empires(List.of(owner)).orbitalStations(List.of(station))
                .commercialHubs(List.of(hub)).build();
    }

    @Test void validatesAndQueuesSettingsWithoutMutatingSnapshot() throws Exception {
        fx(() -> {
            var initial = world();
            var controller = new HumanController();
            var feedback = new Label();
            var card = HubStockTargetCard.create(initial, initial.commercialHubs().getFirst(), "owner", controller, feedback);
            var threshold = (TextField) card.lookup("#hub-stock-threshold");
            var target = (TextField) card.lookup("#hub-stock-target");
            var save = (Button) card.lookup("#hub-stock-save");
            assertEquals("0", threshold.getText());
            threshold.setText("100");
            target.setText("NaN");
            save.fire();
            assertEquals(0, controller.getCommandQueue().size());
            target.setText("300");
            save.fire();
            assertEquals(1, controller.getCommandQueue().size());
            assertTrue(initial.commercialHubs().getFirst().stockTargets().isEmpty());
            assertTrue(feedback.getText().contains("queued"));
            var next = controller.getCommandQueue().drainAndExecute(initial);
            assertEquals(new HubStockTarget(100, 300), next.commercialHubs().getFirst().stockTargets().get("steel"));
            return null;
        });
    }

    @Test void foreignHubHasNoConfigurationControls() throws Exception {
        fx(() -> {
            var state = world();
            assertTrue(HubStockTargetCard.create(state, state.commercialHubs().getFirst(), "foreign",
                    new HumanController(), new Label()).getChildren().isEmpty());
            var unlocated = new CommercialHub("unknown", "missing-body", 0, 1000, 0, 10, Map.of());
            assertTrue(HubStockTargetCard.create(state, unlocated, "owner",
                    new HumanController(), new Label()).getChildren().isEmpty());
            return null;
        });
    }

    @Test void emptyDepotCanSelectGoodsAvailableAtAnotherHub() throws Exception {
        fx(() -> {
            var state = world();
            var source = state.commercialHubs().getFirst();
            var empty = new CommercialHub(source.id(), source.entityId(), 0, 1000, 0, 10, Map.of());
            var supplier = new CommercialHub("supplier", "elsewhere", 0, 1000, 100, 10,
                    Map.of("liquid_hydrogen", new MarketOrder("liquid_hydrogen", 100, 0, .01, 0)));
            state = state.withCommercialHubs(List.of(empty, supplier));
            var controller = new HumanController();
            var card = HubStockTargetCard.create(state, empty, "owner", controller, new Label());
            ((TextField) card.lookup("#hub-stock-threshold")).setText("100");
            ((TextField) card.lookup("#hub-stock-target")).setText("300");
            ((Button) card.lookup("#hub-stock-save")).fire();
            var next = controller.getCommandQueue().drainAndExecute(state);
            assertEquals(new HubStockTarget(100, 300), next.commercialHubs().getFirst().stockTargets().get("liquid_hydrogen"));
            return null;
        });
    }
}
