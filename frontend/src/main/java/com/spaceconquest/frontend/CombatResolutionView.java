package com.spaceconquest.frontend;

import com.spaceconquest.engine.combat.TacticalCombatProcessor;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Interactive UI panel displaying tactical space battle resolution reports,
 * orbital bombardment logs and ground siege outcomes.
 */
public class CombatResolutionView {
    private VBox root;
    private VBox content;
    private ScrollPane scrollPane;
    private final Menubar menubar;
    private final List<TacticalCombatProcessor.CombatEngagementResult> combatReports = new ArrayList<>();

    public CombatResolutionView(Menubar menubar) {
        this.menubar = menubar;
        build();
    }

    private void build() {
        root = new VBox(15);
        content = new VBox(12);
        scrollPane = new ScrollPane(content);

        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: rgba(25, 10, 20, 0.96); " +
                "-fx-border-color: #ff4757; -fx-border-width: 2; " +
                "-fx-border-radius: 10; -fx-background-radius: 10;");
        root.setPrefSize(900, 680);

        Text title = new Text("Tactical naval combat and planetary siege operations");
        title.setFill(Color.WHITE);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 22));

        Button closeButton = new Button("Close");
        closeButton.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-weight: bold;");
        closeButton.setOnAction(e -> hide());

        HBox header = new HBox(title);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(spacer, closeButton);

        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setPadding(new Insets(10));

        root.getChildren().addAll(header, scrollPane);
        root.setVisible(false);
    }

    public VBox getRoot() {
        return root;
    }

    public void show() {
        renderContent();
        root.setVisible(true);
        root.toFront();
    }

    public void hide() {
        root.setVisible(false);
        if (menubar != null) {
            menubar.closePage();
        }
    }

    public void addEngagementReport(TacticalCombatProcessor.CombatEngagementResult result) {
        if (result != null) {
            combatReports.add(result);
        }
        if (root.isVisible()) {
            renderContent();
        }
    }

    private void renderContent() {
        content.getChildren().clear();

        Text header = new Text("Tactical space combat after-action reports (" + combatReports.size() + ")");
        header.setFill(Color.CRIMSON);
        header.setFont(Font.font("Verdana", FontWeight.BOLD, 15));
        content.getChildren().add(header);

        if (combatReports.isEmpty()) {
            Text empty = new Text("No tactical fleet engagements or orbital sieges recorded during this cycle.");
            empty.setFill(Color.LIGHTGRAY);
            content.getChildren().add(empty);
        } else {
            for (TacticalCombatProcessor.CombatEngagementResult res : combatReports) {
                VBox card = new VBox(6);
                card.setPadding(new Insets(8));
                card.setStyle("-fx-background-color: rgba(40, 15, 25, 0.7); -fx-background-radius: 6; -fx-border-color: #ff6b81; -fx-border-width: 1; -fx-border-radius: 6;");

                Text victorText = new Text("Engagement victor: " + res.winnerOwnerEntityId().toUpperCase() +
                        " | Surviving attacker ships: " + res.survivingAttackerFleet().ships().size() +
                        " | Surviving defender ships: " + res.survivingDefenderFleet().ships().size());
                victorText.setFill(Color.GOLD);
                victorText.setFont(Font.font("Verdana", FontWeight.BOLD, 13));

                VBox roundLogs = new VBox(2);
                roundLogs.setPadding(new Insets(4, 0, 0, 10));
                for (TacticalCombatProcessor.CombatRoundReport r : res.roundReports()) {
                    Text log = new Text(String.format("• Round %d: Attacker dealt %.0f dmg (Losses: %d) | Defender dealt %.0f dmg (Losses: %d)",
                            r.roundNumber(), r.attackerDamageDealt(), r.attackerShipsDestroyed(),
                            r.defenderDamageDealt(), r.defenderShipsDestroyed()));
                    log.setFill(Color.GAINSBORO);
                    log.setFont(Font.font("Verdana", 11));
                    roundLogs.getChildren().add(log);
                }

                card.getChildren().addAll(victorText, roundLogs);
                content.getChildren().add(card);
            }
        }
    }
}
