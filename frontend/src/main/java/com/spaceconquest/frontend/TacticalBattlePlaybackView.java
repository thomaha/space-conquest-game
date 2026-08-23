package com.spaceconquest.frontend;

import com.spaceconquest.engine.combat.TacticalCombatProcessor;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
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
 * Interactive UI panel displaying round-by-round tactical battle playback,
 * weapon trajectories, shield absorption bars and strike wing deployments.
 */
public class TacticalBattlePlaybackView {

    private VBox root;
    private VBox content;
    private final Menubar menubar;
    private TacticalCombatProcessor.CombatEngagementResult lastEngagement;

    public TacticalBattlePlaybackView(Menubar menubar) {
        this.menubar = menubar;
        build();
    }

    private void build() {
        root = new VBox(15);
        content = new VBox(12);
        ScrollPane scrollPane = new ScrollPane(content);

        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: rgba(14, 22, 45, 0.96); " +
                "-fx-border-color: #e74c3c; -fx-border-width: 2; " +
                "-fx-border-radius: 10; -fx-background-radius: 10;");
        root.setPrefSize(920, 700);

        Text title = new Text("Tactical space combat and round-by-round battle playback");
        title.setFill(Color.WHITE);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 20));

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

    public void setEngagementResult(TacticalCombatProcessor.CombatEngagementResult result) {
        this.lastEngagement = result;
        if (root.isVisible()) {
            renderContent();
        }
    }

    private void renderContent() {
        content.getChildren().clear();

        if (lastEngagement == null) {
            Text empty = new Text("No tactical combat engagements recorded in the active battle buffer.");
            empty.setFill(Color.LIGHTGRAY);
            empty.setFont(Font.font("Verdana", 13));
            content.getChildren().add(empty);
            return;
        }

        // Summary Banner
        VBox banner = new VBox(6);
        banner.setPadding(new Insets(10));
        banner.setStyle("-fx-background-color: rgba(44, 62, 80, 0.8); -fx-background-radius: 8;");

        Text winnerText = new Text("Engagement outcome: " + (lastEngagement.winnerOwnerEntityId().equals("DRAW") ? "DRAW / STALEMATE" : "VICTORY FOR " + lastEngagement.winnerOwnerEntityId().toUpperCase()));
        winnerText.setFill(Color.GOLD);
        winnerText.setFont(Font.font("Verdana", FontWeight.BOLD, 16));

        Text fleetStatus = new Text(String.format("Attacker surviving ships: %d | Defender surviving ships: %d",
                lastEngagement.survivingAttackerFleet() != null ? lastEngagement.survivingAttackerFleet().ships().size() : 0,
                lastEngagement.survivingDefenderFleet() != null ? lastEngagement.survivingDefenderFleet().ships().size() : 0));
        fleetStatus.setFill(Color.LIGHTCYAN);
        fleetStatus.setFont(Font.font("Verdana", 12));

        banner.getChildren().addAll(winnerText, fleetStatus);
        content.getChildren().add(banner);

        // Round by Round Log
        for (TacticalCombatProcessor.CombatRoundReport r : lastEngagement.roundReports()) {
            VBox roundBox = new VBox(6);
            roundBox.setPadding(new Insets(8));
            roundBox.setStyle("-fx-background-color: rgba(25, 40, 70, 0.7); -fx-background-radius: 6; -fx-border-color: #3498db; -fx-border-width: 1; -fx-border-radius: 6;");

            Text rTitle = new Text(String.format("Round %d | Attacker firepower: %.1f (Lost: %d ships, %d fighters) | Defender firepower: %.1f (Lost: %d ships, %d fighters)",
                    r.roundNumber(), r.attackerDamageDealt(), r.attackerShipsDestroyed(), r.attackerFightersLost(),
                    r.defenderDamageDealt(), r.defenderShipsDestroyed(), r.defenderFightersLost()));
            rTitle.setFill(Color.WHITE);
            rTitle.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

            roundBox.getChildren().add(rTitle);

            if (r.detailedActions() != null) {
                for (TacticalCombatProcessor.CombatActionReport act : r.detailedActions()) {
                    Text actText = new Text(String.format("  • [%s] %s | Damage: %.1f -> %s",
                            act.phase(), act.description(), act.damageAmount(), act.targetEntityId()));
                    actText.setFill(Color.LIGHTGREEN);
                    actText.setFont(Font.font("Verdana", 10));
                    roundBox.getChildren().add(actText);
                }
            }

            content.getChildren().add(roundBox);
        }
    }
}
