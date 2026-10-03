package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.CommandQueue;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.ShipBlueprintFactory;
import com.spaceconquest.engine.ship.ShipComponentCatalog;
import com.spaceconquest.engine.ship.ShipDesignSpecification;
import com.spaceconquest.engine.ship.ShipRole;
import com.spaceconquest.engine.ship.ShipModule;
import com.spaceconquest.engine.technology.ApplicationOptimization;
import com.spaceconquest.engine.technology.ResearchVarianceResult;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.GridPane;
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

    private ComboBox<?> powerCombo(ShipDesignerView view) {
        var section = (VBox) content(view).getChildren().getFirst();
        var grid = section.getChildren().stream().filter(GridPane.class::isInstance)
                .map(GridPane.class::cast).findFirst().orElseThrow();
        return grid.getChildren().stream().filter(ComboBox.class::isInstance).map(node -> (ComboBox<?>) node)
                .filter(combo -> combo.getValue() instanceof ShipModule module
                        && ShipComponentCatalog.POWER_MODULE_IDS.contains(module.id())).findFirst().orElseThrow();
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

    @Test
    void designerOffersEarlyPowerAndPreservesSelectedResearchedFissionPowerWhenEditing() throws Exception {
        onFxThread(() -> {
            var initial = state(true);
            var queue = new CommandQueue();
            var view = view(queue, initial);
            assertEquals(1, powerCombo(view).getItems().size());
            assertEquals(ShipComponentCatalog.CHEMICAL_GENERATOR_ID,
                    ((ShipModule) powerCombo(view).getValue()).id());
            var owner = initial.empires().getFirst();
            var researched = new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(),
                    owner.treasuryCredits(), owner.corporateTaxRate(), owner.controlledSystemIds(), owner.ministries(),
                    owner.systemGovernorAssignments(), List.of("rocketry", "nuclear_fission"), owner.activeShipDesignIds());
            var nuclear = initial.toBuilder().empires(List.of(researched)).build();
            view.updateData(nuclear);
            assertEquals(2, powerCombo(view).getItems().size());
            powerCombo(view).getSelectionModel().select(1);
            button(content(view), "Register blueprint design").fire();
            var registered = queue.drainAndExecute(nuclear);
            assertTrue(registered.shipDesigns().getFirst().equippedModuleIds()
                    .contains(ShipComponentCatalog.FISSION_REACTOR_ID));
            view.updateData(registered);
            button(content(view), "Edit design").fire();
            assertEquals(ShipComponentCatalog.FISSION_REACTOR_ID, ((ShipModule) powerCombo(view).getValue()).id());
        });
    }
}
