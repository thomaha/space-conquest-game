package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.BuildMegastructureCommand;
import com.spaceconquest.engine.megastructure.Megastructure;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Interactive UI panel displaying stellar megastructures, construction phases, power generation, gateways and commissioning commands.
 */
public class MegastructureView {

    private VBox root;
    private VBox content;
    private Label feedbackLabel;
    private final Menubar menubar;
    private HumanController humanController;
    private String playerEmpireId = "terran_confederation";
    private final List<Megastructure> megastructures = new ArrayList<>();

    public MegastructureView(Menubar menubar) {
        this.menubar = menubar;
        build();
    }

    public void setHumanController(HumanController controller) {
        this.humanController = controller;
    }

    public void setPlayerEmpireId(String empireId) {
        if (empireId != null && !empireId.isEmpty()) {
            this.playerEmpireId = empireId;
        }
    }

    private void build() {
        root = new VBox(15);
        content = new VBox(12);
        ScrollPane scrollPane = new ScrollPane(content);

        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: rgba(22, 16, 40, 0.96); " +
                "-fx-border-color: #9b59b6; -fx-border-width: 2; " +
                "-fx-border-radius: 10; -fx-background-radius: 10;");
        root.setPrefSize(920, 700);

        Text title = new Text("Stellar engineering and deep-space megastructures");
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

        feedbackLabel = new Label("Ready | Commission stellar engineering macro-projects.");
        feedbackLabel.setTextFill(Color.LIGHTCYAN);
        feedbackLabel.setFont(Font.font("Verdana", 11));

        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setPadding(new Insets(10));

        root.getChildren().addAll(header, feedbackLabel, scrollPane);
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

    public void updateData(List<Megastructure> newMegastructures) {
        megastructures.clear();
        if (newMegastructures != null) megastructures.addAll(newMegastructures);

        if (root.isVisible()) {
            renderContent();
        }
    }

    private void renderContent() {
        content.getChildren().clear();

        // 1. Commission Megastructure Workbench
        content.getChildren().add(createMegastructureWorkbench());

        // 2. Active Megastructures List
        VBox section = new VBox(10);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: rgba(35, 25, 55, 0.7); -fx-background-radius: 8; -fx-border-color: #8e44ad; -fx-border-width: 1; -fx-border-radius: 8;");

        Text header = new Text("Active stellar megastructures, dysons and gateways");
        header.setFill(Color.LIGHTPINK);
        header.setFont(Font.font("Verdana", FontWeight.BOLD, 15));
        section.getChildren().add(header);

        if (megastructures.isEmpty()) {
            Text empty = new Text("No megastructures currently under construction or operation. Construct Dyson swarms, star lifters or hyperlane gateways.");
            empty.setFill(Color.LIGHTGRAY);
            section.getChildren().add(empty);
        } else {
            for (Megastructure mega : megastructures) {
                VBox card = new VBox(6);
                card.setPadding(new Insets(8));
                card.setStyle("-fx-background-color: rgba(25, 15, 45, 0.6); -fx-background-radius: 6;");

                Text mTitle = new Text(String.format("Megastructure: %s [%s] (Owner: %s, System: %s)",
                        mega.name(), mega.type(), mega.ownerEmpireId(), mega.systemId().toUpperCase()));
                mTitle.setFill(Color.LIGHTGREEN);
                mTitle.setFont(Font.font("Verdana", FontWeight.BOLD, 13));

                Text stageInfo = new Text(String.format("Stage: %d/%d (Progress: %.0f/%.0f turns) | Operational: %s | Energy output: %,.0f kW | Habitation: %,d pop",
                        mega.currentStage(), mega.totalStages(), mega.currentStageProgress(), mega.requiredStageProgress(),
                        mega.isOperational() ? "YES" : "NO", mega.energyYieldKw(), mega.habitableCapacity()));
                stageInfo.setFill(Color.WHITE);
                stageInfo.setFont(Font.font("Verdana", 11));

                card.getChildren().addAll(mTitle, stageInfo);

                if (!mega.materialHarvestYieldKgPerTurn().isEmpty()) {
                    StringBuilder harvest = new StringBuilder("Stellar siphon yields: ");
                    for (Map.Entry<String, Double> e : mega.materialHarvestYieldKgPerTurn().entrySet()) {
                        harvest.append(String.format("%s: %,.0f kg/turn  ", e.getKey(), e.getValue()));
                    }
                    Text hText = new Text(harvest.toString());
                    hText.setFill(Color.GOLD);
                    hText.setFont(Font.font("Verdana", 10));
                    card.getChildren().add(hText);
                }

                section.getChildren().add(card);
            }
        }

        content.getChildren().add(section);
    }

    private VBox createMegastructureWorkbench() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(12));
        section.setStyle("-fx-background-color: rgba(35, 25, 55, 0.75); -fx-background-radius: 8; -fx-border-color: #9b59b6; -fx-border-width: 1; -fx-border-radius: 8;");

        Text title = new Text("Commission stellar megastructure project");
        title.setFill(Color.LIGHTPINK);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 15));

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);

        Label sysLbl = new Label("Target system:");
        sysLbl.setTextFill(Color.LIGHTCYAN);
        ComboBox<String> sysCombo = new ComboBox<>();
        sysCombo.getItems().addAll("sol", "alpha_centauri", "sirius", "vega", "proxima");
        sysCombo.setValue("sol");

        Label typeLbl = new Label("Megastructure type:");
        typeLbl.setTextFill(Color.LIGHTCYAN);
        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll(
                Megastructure.TYPE_DYSON_SWARM,
                Megastructure.TYPE_DYSON_SPHERE,
                Megastructure.TYPE_STAR_LIFTER,
                Megastructure.TYPE_RINGWORLD,
                Megastructure.TYPE_HYPERLANE_GATEWAY,
                Megastructure.TYPE_ORBITAL_HABITAT
        );
        typeCombo.setValue(Megastructure.TYPE_DYSON_SWARM);

        Button buildBtn = new Button("Authorize megastructure assembly");
        buildBtn.setStyle("-fx-background-color: #8e44ad; -fx-text-fill: white; -fx-font-weight: bold;");
        buildBtn.setOnAction(e -> {
            if (humanController != null) {
                humanController.stageCommand(new BuildMegastructureCommand(
                        playerEmpireId, typeCombo.getValue(), sysCombo.getValue(), "star_" + sysCombo.getValue(), "Megastructure " + typeCombo.getValue()
                ));
                feedbackLabel.setText("Authorized construction of " + typeCombo.getValue() + " in " + sysCombo.getValue().toUpperCase());
                feedbackLabel.setTextFill(Color.LIGHTGREEN);
            }
        });

        grid.add(sysLbl, 0, 0);
        grid.add(sysCombo, 1, 0);
        grid.add(typeLbl, 2, 0);
        grid.add(typeCombo, 3, 0);

        HBox actions = new HBox(12, buildBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        section.getChildren().addAll(title, grid, actions);
        return section;
    }
}
