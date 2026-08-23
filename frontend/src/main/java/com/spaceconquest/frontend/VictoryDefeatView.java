package com.spaceconquest.frontend;

import com.spaceconquest.engine.scenario.VictoryConditionChecker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/**
 * Interactive UI panel presenting campaign victory or defeat outcomes, winning empire details and summary statistics.
 */
public class VictoryDefeatView {

    private VBox root;
    private final Menubar menubar;
    private Text outcomeTitle;
    private Text winnerText;
    private Text conditionText;
    private Text summaryText;
    private boolean sandboxModeActive = false;

    public VictoryDefeatView(Menubar menubar) {
        this.menubar = menubar;
        build();
    }

    private void build() {
        root = new VBox(20);
        root.setPadding(new Insets(30));
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: rgba(10, 15, 30, 0.98); " +
                "-fx-border-color: #f1c40f; -fx-border-width: 3; " +
                "-fx-border-radius: 12; -fx-background-radius: 12;");
        root.setPrefSize(750, 520);

        outcomeTitle = new Text("CAMPAIGN VICTORY ACHIEVED");
        outcomeTitle.setFill(Color.GOLD);
        outcomeTitle.setFont(Font.font("Verdana", FontWeight.BOLD, 26));

        VBox card = new VBox(15);
        card.setPadding(new Insets(20));
        card.setStyle("-fx-background-color: rgba(25, 35, 60, 0.7); -fx-background-radius: 8;");

        winnerText = new Text("Victorious sovereign: Terran Confederation");
        winnerText.setFill(Color.LIGHTGREEN);
        winnerText.setFont(Font.font("Verdana", FontWeight.BOLD, 18));

        conditionText = new Text("Objective fulfilled: Planetary domination");
        conditionText.setFill(Color.LIGHTCYAN);
        conditionText.setFont(Font.font("Verdana", FontWeight.BOLD, 14));

        summaryText = new Text("All strategic prerequisites have been accomplished across the galactic sector.");
        summaryText.setFill(Color.WHITE);
        summaryText.setFont(Font.font("Verdana", 13));
        summaryText.setWrappingWidth(650);

        card.getChildren().addAll(winnerText, conditionText, summaryText);

        HBox actions = new HBox(15);
        actions.setAlignment(Pos.CENTER);

        Button continueBtn = new Button("Continue in sandbox mode");
        continueBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px;");
        continueBtn.setOnAction(e -> {
            sandboxModeActive = true;
            hide();
        });

        Button closeBtn = new Button("Acknowledge and close");
        closeBtn.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px;");
        closeBtn.setOnAction(e -> hide());

        actions.getChildren().addAll(continueBtn, closeBtn);

        root.getChildren().addAll(outcomeTitle, card, actions);
        root.setVisible(false);
    }

    public VBox getRoot() {
        return root;
    }

    public void showVictory(VictoryConditionChecker.VictoryCheckResult result, String playerEmpireId) {
        if (sandboxModeActive || result == null) return;

        boolean isPlayerWin = playerEmpireId != null && playerEmpireId.equalsIgnoreCase(result.winningEmpireId());
        outcomeTitle.setText(isPlayerWin ? "★ GLORIOUS CAMPAIGN VICTORY ★" : "⚠ SECTOR CONQUEST CONCLUDED ⚠");
        outcomeTitle.setFill(isPlayerWin ? Color.GOLD : Color.SALMON);

        winnerText.setText("Dominant civilization: " + result.winningEmpireId().toUpperCase());
        conditionText.setText("Victory condition met: " + result.victoryConditionType());
        summaryText.setText(result.summary());

        root.setVisible(true);
        root.toFront();
    }

    public void hide() {
        root.setVisible(false);
        if (menubar != null) {
            menubar.closePage();
        }
    }

    public boolean isSandboxModeActive() {
        return sandboxModeActive;
    }
}
