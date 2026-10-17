package com.spaceconquest.frontend;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.Fleet;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;
import java.util.*;

/** Bounded formatting of saved decisions; no forecasting or simulation on the UI thread. */
final class CombatTargetingHistoryCard {
    private CombatTargetingHistoryCard() {}
    static TitledPane create(GameState state, Fleet fleet, String owner, Set<String> expanded) {
        String key = "targeting-history-" + fleet.id();
        var box = new VBox(5); var pane = new TitledPane("Combat targeting history", box);
        pane.setId(key); pane.setExpanded(false);
        pane.expandedProperty().addListener((observable, previous, open) -> {
            if (!open) { expanded.remove(key); return; }
            expanded.add(key); box.getChildren().clear();
            for (String text : rows(state, fleet, owner)) {
                var label = new Label(text); label.setWrapText(true); box.getChildren().add(label);
            }
        });
        pane.setExpanded(expanded.contains(key)); return pane;
    }
    static List<String> rows(GameState state, Fleet fleet, String owner) {
        if (!owner.equals(fleet.ownerEntityId())) return List.of("Combat targeting history unavailable.");
        var battles = state.fleetEngagements().reversed().stream().filter(event ->
                fleet.id().equals(event.attackerFleetId()) && owner.equals(event.attackerEmpireId())
                || fleet.id().equals(event.defenderFleetId()) && owner.equals(event.defenderEmpireId())).limit(3).toList();
        if (battles.isEmpty()) return List.of("No recorded combat for this fleet.");
        var rows = new ArrayList<String>();
        rows.add("Recent battles: up to three encounters and the last twelve targeting decisions per encounter. Forecasts describe conditions at the time of combat.");
        for (var battle : battles) {
            rows.add("Turn " + battle.turn() + ": " + battle.rounds() + " rounds; winner " + battle.winnerEmpireId());
            var decisions = battle.targetingDecisions();
            if (decisions.isEmpty()) rows.add("Targeting explanations were not recorded for this encounter.");
            else for (var decision : decisions.subList(Math.max(0, decisions.size() - 12), decisions.size()))
                rows.add("Round " + decision.round() + ": " + decision.explanation());
        }
        return List.copyOf(rows);
    }
}
