package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.CommandQueue;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.ShipBlueprintFactory;
import com.spaceconquest.engine.ship.ShipComponentCatalog;
import com.spaceconquest.engine.ship.ShipDesignSpecification;
import com.spaceconquest.engine.ship.ShipRole;
import com.spaceconquest.engine.technology.ApplicationOptimization;
import com.spaceconquest.engine.technology.ResearchVarianceResult;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ShipDesignerOutcomeTest {
    @BeforeAll
    static void initializeJavaFx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Toolkit already initialized by another view test.
        }
    }

    private GameState state(boolean research) {
        var empire = new Empire("empire", "Empire", "human", "Individualist", 100_000, 0,
                List.of(), List.of(), Map.of(), research ? List.of("rocketry", "rocket_engines") : List.of(), List.of());
        return GameState.builder().empires(List.of(empire)).build();
    }

    private ShipDesignerView view(CommandQueue queue, GameState state) {
        var view = new ShipDesignerView(null);
        view.setPlayerEmpireId("empire");
        view.setHumanController(new HumanController(queue));
        view.initializeAfterConstruction();
        view.updateData(state);
        view.show();
        return view;
    }

    private Button button(Parent parent, String text) {
        for (var node : parent.getChildrenUnmodifiable()) {
            if (node instanceof Button button && text.equals(button.getText())) return button;
            if (node instanceof Parent child) {
                Button found = button(child, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private VBox content(ShipDesignerView view) {
        return (VBox) ((ScrollPane) view.getRoot().getChildren().get(2)).getContent();
    }

    private String feedback(ShipDesignerView view) {
        return ((Label) view.getRoot().getChildren().get(1)).getText();
    }

    private void onFxThread(Runnable assertions) throws Exception {
        var task = new FutureTask<Void>(() -> { assertions.run(); return null; });
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }

    @Test
    void rejectedUnchangedEditIsNotConfirmedFromTheExistingBlueprint() throws Exception {
        onFxThread(() -> {
            var specification = new ShipDesignSpecification("blueprint", "Freighter", "empire", ShipRole.CARGO_TRANSPORT,
                    "steel", ShipComponentCatalog.workbenchModules("mod_chemical_rocket", false), "steel", 1);
            var blueprint = ShipBlueprintFactory.evaluate(state(true), specification).design();
            assertNotNull(blueprint);
            var initial = state(true).withShipDesigns(List.of(blueprint));
            var queue = new CommandQueue();
            var view = view(queue, initial);
            button(content(view), "Edit design").fire();
            button(content(view), "Save blueprint changes").fire();
            var lostResearch = state(false).withShipDesigns(List.of(blueprint));
            var rejected = queue.drainAndExecute(lostResearch);
            assertEquals(List.of(blueprint), rejected.shipDesigns());
            view.updateData(rejected);
            assertTrue(feedback(view).contains("were rejected"), feedback(view));
        });
    }

    @Test
    void pendingSubmissionWaitsForItsReceiptAndConfirmsRecomputedStats() throws Exception {
        onFxThread(() -> {
            var initial = state(true);
            var queue = new CommandQueue();
            var view = view(queue, initial);
            button(content(view), "Register blueprint design").fire();
            view.updateData(initial.toBuilder().turn(99).build());
            assertTrue(feedback(view).contains("queued"), feedback(view));
            var improvement = new ApplicationOptimization("empire", "rocket_engines", "PATH_A",
                    new ResearchVarianceResult(ResearchVarianceResult.OPTIMIZED_SUCCESS, 1.15, 1.20, 1));
            var changed = initial.toBuilder().applicationOptimizations(List.of(improvement)).build();
            var registered = queue.drainAndExecute(changed);
            assertEquals(690_000, registered.shipDesigns().getFirst().totalThrustN(), 0.001);
            view.updateData(registered);
            assertTrue(feedback(view).contains("is registered"), feedback(view));
        });
    }

    @Test
    void clearedSubmissionShowsCancellation() throws Exception {
        onFxThread(() -> {
            var initial = state(true);
            var queue = new CommandQueue();
            var view = view(queue, initial);
            button(content(view), "Register blueprint design").fire();
            queue.clear();
            view.updateData(initial);
            assertTrue(feedback(view).contains("was cancelled"), feedback(view));
            assertTrue(initial.shipDesigns().isEmpty());
        });
    }
}
