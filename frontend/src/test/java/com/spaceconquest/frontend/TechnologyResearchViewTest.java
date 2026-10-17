package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.CommandQueue;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.*;
import com.spaceconquest.engine.technology.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.text.Text;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class TechnologyResearchViewTest {
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
    private void layout(TechnologyView view) {
        new javafx.scene.Scene(view.getRoot());
        view.getRoot().applyCss(); view.getRoot().layout();
    }
    private GameState world() {
        var empire = new Empire("owner", "Owner", "human", "Individualist", 100000, 0,
                List.of("sol"), List.of(), Map.of(), List.of("electricity", "solar_power"), List.of());
        var planet = new Planet("earth", "Earth", "", 1, 9.81, 1, 0, 12742, "terrestrial", "breathable", true, 1,
                List.of(), List.of(), List.of(new Population("human", Map.of(25, 20000L))));
        var economy = new SystemEconomy("sol", "owner", .2, .2, .2, .2, .2, 10000, 0, 1, 1, 1, 1, 1,
                10, 6, 15, 10, 20, 30, 25, 100, .1);
        var state = GameState.builder().empires(List.of(empire)).systemEconomies(List.of(economy))
                .solarSystems(List.of(new SolarSystem("sol", "Sol", "", 0, 0, 0, 1, 1, "Yellow", List.of(planet), List.of()))).build();
        state = state.toBuilder().householdAccounts(new HouseholdEconomyProcessor().process(state, List.of()).householdAccounts()).build();
        assertEquals(6, ResearchWorkforce.capacity(state, empire));
        return state.withResearchProjects(List.of(new ResearchProject("active", "owner", "nuclear_fission", false, 17, 3000, 6, 1)));
    }
    @Test void fullyAssignedScientistsCanBeReducedPausedAndCancelledThroughQueuedControls() throws Exception {
        var state = world(); var queue = new CommandQueue();
        fx(() -> {
            var view = new TechnologyView(null); view.setPlayerEmpireId("owner"); view.setHumanController(new HumanController(queue));
            view.updateData(state); view.show(); layout(view);
            assertTrue(((Text) view.getRoot().lookup("#research-scientists")).getText().contains("Unassigned: 0"));
            assertTrue(((Text) view.getRoot().lookup("#research-estimate-active")).getText().contains("498 days remaining"));
            @SuppressWarnings("unchecked") var spinner = (Spinner<Integer>) view.getRoot().lookup("#research-assignment-active");
            spinner.getValueFactory().setValue(3); ((Button) view.getRoot().lookup("#research-update-active")).fire();
            ((Button) view.getRoot().lookup("#research-pause-active")).fire();
            assertEquals(6, state.researchProjects().getFirst().assignedScientists(), "UI must only stage commands");
        });
        var paused = queue.drainAndExecute(state);
        assertEquals(0, paused.researchProjects().getFirst().assignedScientists()); assertEquals(17, paused.researchProjects().getFirst().accumulatedPoints());
        fx(() -> {
            var view = new TechnologyView(null); view.setPlayerEmpireId("owner"); view.setHumanController(new HumanController(queue));
            view.updateData(paused); view.show(); layout(view); ((Button) view.getRoot().lookup("#research-cancel-active")).fire();
            assertTrue(((Text) view.getRoot().lookup("#research-estimate-active")).getText().contains("Paused"));
        });
        assertTrue(queue.drainAndExecute(paused).researchProjects().isEmpty());
    }
    @Test void unsupportedSalvageIsDisabledAndTheSummaryUsesPaidStaffing() throws Exception {
        var state = world().toBuilder().householdAccounts(List.of()).build(); var queue = new CommandQueue();
        fx(() -> {
            var view = new TechnologyView(null); view.setPlayerEmpireId("owner"); view.setHumanController(new HumanController(queue));
            view.updateData(state); view.show(); layout(view);
            var salvage = (Button) view.getRoot().lookup("#research-salvage"); assertTrue(salvage.isDisabled()); salvage.fire();
            assertTrue(queue.isEmpty());
            assertTrue(((Text) view.getRoot().lookup("#research-scientists")).getText().contains("Funded scientists: 0"));
            assertTrue(((Text) view.getRoot().lookup("#application-support-batteries")).getText().contains("Future work"));
            assertTrue(((Text) view.getRoot().lookup("#application-support-solar_power")).getText().startsWith("Partial:"));
            assertTrue(((Text) view.getRoot().lookup("#research-estimate-active")).getText().contains("No completion estimate"));
            assertTrue(((Text) view.getRoot().lookup("#research-status-warp")).getText().contains("Requires research"));
            assertTrue(((Button) view.getRoot().lookup("#research-start-warp")).isDisabled());
            assertTrue(((Text) view.getRoot().lookup("#application-benefits-solar_power")).getText().contains("power plant"));
            assertTrue(((Text) view.getRoot().lookup("#application-benefits-laser_weapons")).getText().contains("Ship equipment"));
            assertTrue(((Text) view.getRoot().lookup("#research-status-laser_weapons")).getText().contains("Requires research"));
            assertTrue(((Button) view.getRoot().lookup("#research-start-laser_weapons")).isDisabled());
            assertTrue(((Text) view.getRoot().lookup("#application-benefits-batteries")).getText().contains("none yet"));
        });
    }
    @Test void pendingOutcomesAndMissingPrerequisitesExplainDisabledActionsWithoutQueuingCommands() throws Exception {
        var base = world().withResearchProjects(List.of());
        var completed = new ResearchProject("complete", "owner", "solar_power", true, 500, 500, 5, 1);
        var state = base.withApplicationOptimizations(ApplicationRefinement.completed(base.applicationOptimizations(), completed,
                new ResearchProcessor().rollBreakthrough(.1)));
        var queue = new CommandQueue();
        fx(() -> {
            var view = new TechnologyView(null); view.setPlayerEmpireId("owner"); view.setHumanController(new HumanController(queue));
            view.updateData(state); view.show(); layout(view);
            assertTrue(((Text) view.getRoot().lookup("#research-status-solar_power")).getText().contains("Resolve"));
            var refine = (Button) view.getRoot().lookup("#research-start-solar_power"); assertTrue(refine.isDisabled()); refine.fire();
            var locked = (Button) view.getRoot().lookup("#research-start-laser_weapons"); assertTrue(locked.isDisabled()); locked.fire();
            assertTrue(queue.isEmpty());
            assertTrue(((Text) view.getRoot().lookup("#application-benefits-industrial_soil_cultivation")).getText().contains("Produces:"));
            assertFalse(((Button) view.getRoot().lookup("#research-start-nuclear_fission")).isDisabled());
        });
    }
    @Test void storedChoicesStayHistoricalAndDeveloperInspectionIsReadOnlyAndOwnerFiltered() throws Exception {
        var original = new ResearchDecision(ResearchDecision.Actor.AI, "Power deficit when chosen.", "solar_power");
        var project = new ResearchProject("ai", "owner", "solar_power", true, 10, 500, 5, 1, original);
        var state = world().withResearchProjects(List.of(project));
        var other = new Empire("other", "Other", "human", "Individualist", 0, 0, List.of(), List.of(), Map.of(), List.of(), List.of());
        state = state.withEmpires(List.of(state.empires().getFirst(), other));
        state = ResearchHistory.record(state, new ResearchEvent(2, "owner", "ai", "solar_power", ResearchEvent.Kind.STARTED, original, null));
        state = ResearchHistory.record(state, new ResearchEvent(3, "other", null, "warp", ResearchEvent.Kind.COMPLETED,
                new ResearchDecision(ResearchDecision.Actor.SIMULATION, "Other empire secret.", null), null));
        final var snapshot = state; var queue = new CommandQueue();
        fx(() -> {
            var view = new TechnologyView(null); view.setPlayerEmpireId("owner"); view.setHumanController(new HumanController(queue));
            view.updateData(snapshot); view.show(); layout(view);
            assertTrue(((Text) view.getRoot().lookup("#research-reason-ai")).getText().contains("Power deficit when chosen"));
            assertTrue(((Text) view.getRoot().lookup("#research-context-ai")).getText().contains("no urgent shortage"));
            var history = (javafx.scene.layout.VBox) view.getRoot().lookup("#research-history-owner");
            assertTrue(history.getChildren().stream().filter(node -> node instanceof Text).map(node -> ((Text) node).getText()).noneMatch(text -> text.contains("secret")));
            assertNull(view.getRoot().lookup("#research-debug-empire"));
            var debug = new ResearchHistoryCard(true).create(snapshot, "owner", DataModelLoaderUnchecked.catalog());
            new javafx.scene.Scene(debug); debug.applyCss();
            @SuppressWarnings("unchecked") var selector = (ComboBox<String>) debug.lookup("#research-debug-empire"); selector.setValue("other");
            assertNotNull(debug.lookup("#research-history-other"));
            assertTrue(debug.lookupAll(".button").isEmpty()); assertTrue(queue.isEmpty());
        });
    }
    private static class DataModelLoaderUnchecked {
        static List<Technology> catalog() { try { return DataModelLoader.loadTechnologies(); } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); } }
    }
}
