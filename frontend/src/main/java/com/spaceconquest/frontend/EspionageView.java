package com.spaceconquest.frontend;

import com.spaceconquest.engine.espionage.EspionageOperation;
import com.spaceconquest.engine.espionage.PirateBase;
import com.spaceconquest.engine.espionage.SleeperAgent;
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
 * Interactive UI panel displaying imperial sleeper agents, active covert operations
 * and syndicate rogue pirate strongholds.
 */
public class EspionageView {

    private VBox root;
    private VBox content;
    private final Menubar menubar;
    private final List<SleeperAgent> agents = new ArrayList<>();
    private final List<EspionageOperation> operations = new ArrayList<>();
    private final List<PirateBase> pirateBases = new ArrayList<>();

    public EspionageView(Menubar menubar) {
        this.menubar = menubar;
        build();
    }

    private void build() {
        root = new VBox(15);
        content = new VBox(12);
        ScrollPane scrollPane = new ScrollPane(content);

        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: rgba(14, 22, 45, 0.96); " +
                "-fx-border-color: #8e44ad; -fx-border-width: 2; " +
                "-fx-border-radius: 10; -fx-background-radius: 10;");
        root.setPrefSize(920, 700);

        Text title = new Text("Imperial intelligence, sleeper agents and syndicate bases");
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

    public void updateData(List<SleeperAgent> newAgents, List<EspionageOperation> newOps, List<PirateBase> newBases) {
        agents.clear();
        if (newAgents != null) agents.addAll(newAgents);

        operations.clear();
        if (newOps != null) operations.addAll(newOps);

        pirateBases.clear();
        if (newBases != null) pirateBases.addAll(newBases);

        if (root.isVisible()) {
            renderContent();
        }
    }

    private void renderContent() {
        content.getChildren().clear();

        // 1. Sleeper Agents Section
        content.getChildren().add(createAgentsSection());

        // 2. Covert Operations Section
        content.getChildren().add(createOperationsSection());

        // 3. Pirate Bases Section
        content.getChildren().add(createPirateBasesSection());
    }

    private VBox createAgentsSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: rgba(25, 40, 70, 0.7); -fx-background-radius: 8; -fx-border-color: #9b59b6; -fx-border-width: 1; -fx-border-radius: 8;");

        Text header = new Text("Active infiltrated sleeper operatives");
        header.setFill(Color.MEDIUMPURPLE);
        header.setFont(Font.font("Verdana", FontWeight.BOLD, 15));
        section.getChildren().add(header);

        if (agents.isEmpty()) {
            Text empty = new Text("No active sleeper agents currently deployed. Infiltrate operatives to uncover technical blueprints and execute sabotage.");
            empty.setFill(Color.LIGHTGRAY);
            section.getChildren().add(empty);
        } else {
            for (SleeperAgent agent : agents) {
                VBox aBox = new VBox(4);
                aBox.setPadding(new Insets(6));
                aBox.setStyle("-fx-background-color: rgba(15, 30, 55, 0.6); -fx-background-radius: 6;");

                Text aTitle = new Text(String.format("Operative [%s] | Target: %s | Cover: %s | Clearance tier: %d | Status: %s",
                        agent.id(), agent.targetEntityId().toUpperCase(), agent.coverProfessionId(),
                        agent.infiltrationLevel(), agent.isCompromised() ? "COMPROMISED" : "ACTIVE / EMBEDDED"));
                aTitle.setFill(agent.isCompromised() ? Color.SALMON : Color.LIGHTGREEN);
                aTitle.setFont(Font.font("Verdana", 11));

                aBox.getChildren().add(aTitle);
                section.getChildren().add(aBox);
            }
        }

        return section;
    }

    private VBox createOperationsSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: rgba(25, 40, 70, 0.7); -fx-background-radius: 8; -fx-border-color: #e74c3c; -fx-border-width: 1; -fx-border-radius: 8;");

        Text header = new Text("Covert espionage operations and sabotage missions");
        header.setFill(Color.TOMATO);
        header.setFont(Font.font("Verdana", FontWeight.BOLD, 15));
        section.getChildren().add(header);

        if (operations.isEmpty()) {
            Text empty = new Text("No covert operations currently executing.");
            empty.setFill(Color.LIGHTGRAY);
            section.getChildren().add(empty);
        } else {
            for (EspionageOperation op : operations) {
                VBox oBox = new VBox(4);
                oBox.setPadding(new Insets(6));
                oBox.setStyle("-fx-background-color: rgba(15, 30, 55, 0.6); -fx-background-radius: 6;");

                Text oTitle = new Text(String.format("Operation [%s]: %s | Target: %s (%s) | Progress: %.0f%% | Success prob: %.0f%% | Status: %s",
                        op.id(), op.operationType(), op.targetEntityId().toUpperCase(), op.targetEmpireId(),
                        op.progressPercent(), op.successProbability() * 100.0, op.isCompleted() ? (op.wasDetected() ? "COMPLETED (DETECTED)" : "COMPLETED (COVERT)") : "IN PROGRESS"));
                oTitle.setFill(Color.WHITE);
                oTitle.setFont(Font.font("Verdana", 11));

                oBox.getChildren().add(oTitle);
                section.getChildren().add(oBox);
            }
        }

        return section;
    }

    private VBox createPirateBasesSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: rgba(25, 40, 70, 0.7); -fx-background-radius: 8; -fx-border-color: #e67e22; -fx-border-width: 1; -fx-border-radius: 8;");

        Text header = new Text("Shadow syndicate pirate bases and illicit capital vaults");
        header.setFill(Color.ORANGE);
        header.setFont(Font.font("Verdana", FontWeight.BOLD, 15));
        section.getChildren().add(header);

        if (pirateBases.isEmpty()) {
            Text empty = new Text("No pirate strongholds detected. High security and active police patrols prevent rogue syndicate base emergence.");
            empty.setFill(Color.LIGHTGRAY);
            section.getChildren().add(empty);
        } else {
            for (PirateBase base : pirateBases) {
                VBox bBox = new VBox(4);
                bBox.setPadding(new Insets(6));
                bBox.setStyle("-fx-background-color: rgba(15, 30, 55, 0.6); -fx-background-radius: 6;");

                Text bText = new Text(String.format("Pirate base [%s] | Syndicate: %s | Location: %s (%s) | Stored capital: %,.0f credits | Rogue ships: %d | Status: %s",
                        base.id(), base.syndicateId(), base.celestialLocationId().toUpperCase(), base.systemId(),
                        base.illicitCapitalStored(), base.pirateShipCount(), base.isHidden() ? "CLOAKED" : "REVEALED"));
                bText.setFill(Color.GOLD);
                bText.setFont(Font.font("Verdana", 11));

                bBox.getChildren().add(bText);
                section.getChildren().add(bBox);
            }
        }

        return section;
    }
}
