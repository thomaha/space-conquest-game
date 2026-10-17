package com.spaceconquest.frontend;

import com.spaceconquest.control.ai.EmpireResearchNeeds;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.technology.*;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import java.util.*;

/** Snapshot-only receipts with optional developer inspection of other empires. */
final class ResearchHistoryCard {
    private final boolean debug;
    private String inspectedEmpire;
    ResearchHistoryCard(boolean debug) { this.debug = debug; }
    VBox create(GameState state, String playerId, List<Technology> catalog) {
        var card = new VBox(8); card.getChildren().add(new Label("Research history"));
        card.getChildren().add(history(state, playerId, catalog));
        if (debug) {
            var selector = new ComboBox<String>(); selector.setId("research-debug-empire");
            state.empires().forEach(empire -> selector.getItems().add(empire.id()));
            if (!selector.getItems().contains(inspectedEmpire)) inspectedEmpire = playerId;
            selector.setValue(inspectedEmpire); var detail = new VBox(6);
            card.getChildren().addAll(new Label("Developer research inspector (read only)"), selector, detail);
            render(detail, state, inspectedEmpire, catalog);
            selector.setOnAction(event -> { inspectedEmpire = selector.getValue(); render(detail, state, inspectedEmpire, catalog); });
        }
        return card;
    }
    private void render(VBox detail, GameState state, String empireId, List<Technology> catalog) {
        detail.getChildren().clear();
        state.researchProjects().stream().filter(project -> Objects.equals(empireId, project.empireId())).forEach(project -> {
            detail.getChildren().add(text(ResearchFeedback.name(project.targetTechOrAppId(), catalog)));
            detail.getChildren().add(reason(project)); detail.getChildren().add(context(state, project, catalog));
        });
        detail.getChildren().add(history(state, empireId, catalog));
    }
    static Text reason(ResearchProject project) {
        var decision = project.decision();
        var text = text(decision == null ? "Original choice: no recorded reason." : "Original choice (" + decision.actor() + "): " + decision.reason());
        text.setId("research-reason-" + project.id()); return text;
    }
    static Text context(GameState state, ResearchProject project, List<Technology> catalog) {
        String message = "Current context: prerequisite or general research; funding status is shown above.";
        var empire = state.empires().stream().filter(value -> project.empireId().equals(value.id())).findFirst().orElse(null);
        String goal = project.decision() == null ? null : project.decision().goalApplicationId();
        if (empire != null && goal != null) {
            var app = catalog.stream().flatMap(technology -> technology.applications().stream()).filter(value -> goal.equals(value.id())).findFirst().orElse(null);
            if (app != null) message = "Current context for " + app.name() + ": " + new EmpireResearchNeeds(state, empire).reason(app);
        }
        var text = text(message); text.setId("research-context-" + project.id()); return text;
    }
    private static VBox history(GameState state, String empireId, List<Technology> catalog) {
        var rows = new VBox(6); rows.setId("research-history-" + empireId);
        var events = state.researchHistory().stream().filter(event -> Objects.equals(empireId, event.empireId())).toList().reversed();
        if (events.isEmpty()) rows.getChildren().add(text("No recorded research events."));
        events.stream().limit(12).forEach(event -> rows.getChildren().add(text("Turn " + event.turn() + " | " + event.kind().name().toLowerCase(Locale.ROOT)
                + " | " + ResearchFeedback.name(event.targetId(), catalog) + " | " + event.decision().actor() + "\n" + event.decision().reason()
                + (event.outcome() == null ? "" : String.format("\nOutcome: %s | Output %.0f%% | Cost %.0f%% | Complexity %+d",
                event.outcome().outcomeType(), event.outcome().effectMultiplier() * 100, event.outcome().costMultiplier() * 100, event.outcome().complexityShift())))));
        return rows;
    }
    private static Text text(String message) { var text = new Text(message); text.setFill(Color.LIGHTSKYBLUE); text.setWrappingWidth(740); return text; }
}
