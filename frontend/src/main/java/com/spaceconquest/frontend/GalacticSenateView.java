package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.ProposeResolutionCommand;
import com.spaceconquest.control.command.VoteResolutionCommand;
import com.spaceconquest.engine.community.GalacticCommunity;
import com.spaceconquest.engine.community.GalacticResolution;
import com.spaceconquest.engine.community.GalacticSanction;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.Map;

/**
 * Interactive UI panel displaying Galactic Senate legislative assemblies, voting results, resolutions, sanctions and voting controls.
 */
public class GalacticSenateView {

    private VBox root;
    private VBox content;
    private Label feedbackLabel;
    private final Menubar menubar;
    private HumanController humanController;
    private String playerEmpireId = "terran_confederation";
    private GalacticCommunity community;

    public GalacticSenateView(Menubar menubar) {
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
        root.setStyle("-fx-background-color: rgba(18, 22, 48, 0.96); " +
                "-fx-border-color: #34495e; -fx-border-width: 2; " +
                "-fx-border-radius: 10; -fx-background-radius: 10;");
        root.setPrefSize(920, 700);

        Text title = new Text("Galactic Senate and interstellar legislation");
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

        feedbackLabel = new Label("Ready | Introduce resolutions or cast your senate vote.");
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

    public void updateData(GalacticCommunity newCommunity) {
        this.community = newCommunity;
        if (root.isVisible()) {
            renderContent();
        }
    }

    private void renderContent() {
        content.getChildren().clear();

        if (community == null) {
            Text empty = new Text("Galactic Senate assembly is not currently in session.");
            empty.setFill(Color.LIGHTGRAY);
            content.getChildren().add(empty);
            return;
        }

        // 1. Propose Resolution Workbench
        content.getChildren().add(createResolutionProposalWorkbench());

        // 2. Assembly Overview
        VBox overview = new VBox(6);
        overview.setPadding(new Insets(8));
        overview.setStyle("-fx-background-color: rgba(30, 40, 75, 0.6); -fx-background-radius: 6;");
        Text oText = new Text(String.format("Assembly: %s | Member states: %d | Next session: Turn %d (interval: %d turns)",
                community.name(), community.memberEmpireIds().size(), community.nextSenateSessionTurn(), community.senateSessionInterval()));
        oText.setFill(Color.LIGHTCYAN);
        oText.setFont(Font.font("Verdana", FontWeight.BOLD, 13));
        overview.getChildren().add(oText);
        content.getChildren().add(overview);

        // 3. Active Proposed Resolutions
        VBox activeBox = new VBox(8);
        activeBox.setPadding(new Insets(10));
        activeBox.setStyle("-fx-background-color: rgba(30, 40, 75, 0.7); -fx-background-radius: 8; -fx-border-color: #3498db; -fx-border-width: 1; -fx-border-radius: 8;");
        Text activeHeader = new Text("Actively debated resolutions (in session)");
        activeHeader.setFill(Color.LIGHTBLUE);
        activeHeader.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        activeBox.getChildren().add(activeHeader);

        if (community.activeResolutions().isEmpty()) {
            Text noAct = new Text("No active resolutions currently on the legislative floor.");
            noAct.setFill(Color.LIGHTGRAY);
            activeBox.getChildren().add(noAct);
        } else {
            for (GalacticResolution res : community.activeResolutions()) {
                VBox rBox = new VBox(6);
                rBox.setPadding(new Insets(8));
                rBox.setStyle("-fx-background-color: rgba(20, 30, 60, 0.6); -fx-background-radius: 6;");

                HBox topRow = new HBox(10);
                topRow.setAlignment(Pos.CENTER_LEFT);

                Text rTitle = new Text(String.format("• %s [%s] (Proposer: %s, Turns remaining: %d)",
                        res.title(), res.type(), res.proposerEmpireId(), res.sessionTurnsLeft()));
                rTitle.setFill(Color.YELLOW);
                rTitle.setFont(Font.font("Verdana", FontWeight.BOLD, 12));
                HBox.setHgrow(rTitle, Priority.ALWAYS);

                Button ayeBtn = new Button("AYE");
                ayeBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 10px;");
                ayeBtn.setOnAction(e -> {
                    if (humanController != null) {
                        humanController.stageCommand(new VoteResolutionCommand(
                                res.id(), playerEmpireId, GalacticResolution.VOTE_AYE
                        ));
                        feedbackLabel.setText("Cast vote AYE for resolution: " + res.title());
                        feedbackLabel.setTextFill(Color.LIGHTGREEN);
                    }
                });

                Button nayBtn = new Button("NAY");
                nayBtn.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 10px;");
                nayBtn.setOnAction(e -> {
                    if (humanController != null) {
                        humanController.stageCommand(new VoteResolutionCommand(
                                res.id(), playerEmpireId, GalacticResolution.VOTE_NAY
                        ));
                        feedbackLabel.setText("Cast vote NAY for resolution: " + res.title());
                        feedbackLabel.setTextFill(Color.SALMON);
                    }
                });

                Button abstainBtn = new Button("ABSTAIN");
                abstainBtn.setStyle("-fx-background-color: #7f8c8d; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 10px;");
                abstainBtn.setOnAction(e -> {
                    if (humanController != null) {
                        humanController.stageCommand(new VoteResolutionCommand(
                                res.id(), playerEmpireId, GalacticResolution.VOTE_ABSTAIN
                        ));
                        feedbackLabel.setText("Cast ABSTAIN for resolution: " + res.title());
                        feedbackLabel.setTextFill(Color.LIGHTGRAY);
                    }
                });

                topRow.getChildren().addAll(rTitle, ayeBtn, nayBtn, abstainBtn);

                StringBuilder votes = new StringBuilder("Registered votes: ");
                for (Map.Entry<String, String> v : res.votes().entrySet()) {
                    votes.append(String.format("%s: %s  ", v.getKey(), v.getValue()));
                }
                Text vText = new Text(votes.toString());
                vText.setFill(Color.LIGHTGRAY);
                vText.setFont(Font.font("Verdana", 10));

                rBox.getChildren().addAll(topRow, vText);
                activeBox.getChildren().add(rBox);
            }
        }
        content.getChildren().add(activeBox);

        // 4. Enacted Interstellar Charters & Laws
        VBox lawsBox = new VBox(8);
        lawsBox.setPadding(new Insets(10));
        lawsBox.setStyle("-fx-background-color: rgba(30, 40, 75, 0.7); -fx-background-radius: 8; -fx-border-color: #2ecc71; -fx-border-width: 1; -fx-border-radius: 8;");
        Text lawsHeader = new Text("Enacted interstellar treaties and galactic law");
        lawsHeader.setFill(Color.LIGHTGREEN);
        lawsHeader.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        lawsBox.getChildren().add(lawsHeader);

        if (community.passedResolutions().isEmpty()) {
            Text noLaws = new Text("No permanent charters enacted yet.");
            noLaws.setFill(Color.LIGHTGRAY);
            lawsBox.getChildren().add(noLaws);
        } else {
            for (GalacticResolution law : community.passedResolutions()) {
                Text lText = new Text(String.format("✔ %s [%s] - Status: ENFORCED", law.title(), law.type()));
                lText.setFill(Color.LIGHTGREEN);
                lText.setFont(Font.font("Verdana", 11));
                lawsBox.getChildren().add(lText);
            }
        }
        content.getChildren().add(lawsBox);

        // 5. Active Sanctions
        VBox sanctionsBox = new VBox(8);
        sanctionsBox.setPadding(new Insets(10));
        sanctionsBox.setStyle("-fx-background-color: rgba(30, 40, 75, 0.7); -fx-background-radius: 8; -fx-border-color: #e74c3c; -fx-border-width: 1; -fx-border-radius: 8;");
        Text sancHeader = new Text("Enforced pan-galactic economic and military sanctions");
        sancHeader.setFill(Color.SALMON);
        sancHeader.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        sanctionsBox.getChildren().add(sancHeader);

        if (community.activeSanctions().isEmpty()) {
            Text noSanc = new Text("No active sanctions against sovereign entities.");
            noSanc.setFill(Color.LIGHTGRAY);
            sanctionsBox.getChildren().add(noSanc);
        } else {
            for (GalacticSanction s : community.activeSanctions()) {
                Text sText = new Text(String.format("⛔ [%s] Target: %s | Tariff penalty: +%.0f%% | Asset freeze: %s | Military intervention: %s (Duration: %d turns)",
                        s.sanctionType(), s.targetEmpireId().toUpperCase(), s.tradeTariffPenaltyRate() * 100.0,
                        s.isAssetFreezeActive() ? "YES" : "NO", s.isMilitaryInterventionAuthorized() ? "AUTHORIZED" : "NO",
                        s.turnsRemaining()));
                sText.setFill(Color.SALMON);
                sText.setFont(Font.font("Verdana", 11));
                sanctionsBox.getChildren().add(sText);
            }
        }
        content.getChildren().add(sanctionsBox);
    }

    private VBox createResolutionProposalWorkbench() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(12));
        section.setStyle("-fx-background-color: rgba(20, 40, 70, 0.75); -fx-background-radius: 8; -fx-border-color: #9b59b6; -fx-border-width: 1; -fx-border-radius: 8;");

        Text title = new Text("Introduce legislative resolution to Senate");
        title.setFill(Color.MEDIUMPURPLE);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 15));

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);

        Label titleLbl = new Label("Resolution title:");
        titleLbl.setTextFill(Color.LIGHTCYAN);
        TextField titleField = new TextField("Pan-Galactic Mutual Defense Treaty");

        Label typeLbl = new Label("Resolution type:");
        typeLbl.setTextFill(Color.LIGHTCYAN);
        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll(
                GalacticResolution.TYPE_MUTUAL_DEFENSE,
                GalacticResolution.TYPE_ANTI_PIRACY,
                GalacticResolution.TYPE_ENVIRONMENTAL_ACCORD,
                GalacticResolution.TYPE_FREE_TRADE,
                GalacticResolution.TYPE_SANCTION_EMBARGO,
                GalacticResolution.TYPE_SANCTION_FREEZE,
                GalacticResolution.TYPE_MILITARY_INTERVENTION
        );
        typeCombo.setValue(GalacticResolution.TYPE_MUTUAL_DEFENSE);

        Label targetLbl = new Label("Target empire (sanctions):");
        targetLbl.setTextFill(Color.LIGHTCYAN);
        ComboBox<String> targetCombo = new ComboBox<>();
        targetCombo.getItems().addAll("none", "vulkan_forge", "silicon_collective", "zephyr_freehold", "shadow_syndicate_sol");
        targetCombo.setValue("none");

        Button proposeBtn = new Button("Propose resolution");
        proposeBtn.setStyle("-fx-background-color: #8e44ad; -fx-text-fill: white; -fx-font-weight: bold;");
        proposeBtn.setOnAction(e -> {
            String target = "none".equalsIgnoreCase(targetCombo.getValue()) ? null : targetCombo.getValue();
            if (humanController != null) {
                humanController.stageCommand(new ProposeResolutionCommand(
                        playerEmpireId, titleField.getText(), typeCombo.getValue(), target
                ));
                feedbackLabel.setText("Introduced resolution: " + titleField.getText() + " to the Senate floor.");
                feedbackLabel.setTextFill(Color.LIGHTGREEN);
            }
        });

        grid.add(titleLbl, 0, 0);
        grid.add(titleField, 1, 0);
        grid.add(typeLbl, 2, 0);
        grid.add(typeCombo, 3, 0);

        grid.add(targetLbl, 0, 1);
        grid.add(targetCombo, 1, 1);

        HBox actions = new HBox(12, proposeBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        section.getChildren().addAll(title, grid, actions);
        return section;
    }
}
