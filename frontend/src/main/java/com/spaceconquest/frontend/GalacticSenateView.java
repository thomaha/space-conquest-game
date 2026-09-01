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

        content.getChildren().add(createResolutionProposalWorkbench());
        content.getChildren().add(buildAssemblyOverview());
        content.getChildren().add(buildActiveResolutionsSection());
        content.getChildren().add(buildPassedResolutionsSection());
        content.getChildren().add(buildSanctionsSection());
    }

    private VBox buildAssemblyOverview() {
        VBox overview = new VBox(6);
        overview.setPadding(new Insets(8));
        overview.setStyle("-fx-background-color: rgba(30, 40, 75, 0.6); -fx-background-radius: 6;");
        Text oText = new Text(String.format("Assembly: %s | Member states: %d | Next session: Turn %d (interval: %d turns)",
                community.name(), community.memberEmpireIds().size(), community.nextSenateSessionTurn(), community.senateSessionInterval()));
        oText.setFill(Color.LIGHTCYAN);
        oText.setFont(Font.font("Verdana", FontWeight.BOLD, 13));
        overview.getChildren().add(oText);
        return overview;
    }

    private VBox buildActiveResolutionsSection() {
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
                activeBox.getChildren().add(buildActiveResolutionCard(res));
            }
        }
        return activeBox;
    }

    private VBox buildActiveResolutionCard(GalacticResolution res) {
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

        Button ayeBtn = createVoteButton("AYE", "#27ae60", res, GalacticResolution.VOTE_AYE, Color.LIGHTGREEN);
        Button nayBtn = createVoteButton("NAY", "#c0392b", res, GalacticResolution.VOTE_NAY, Color.SALMON);
        Button abstainBtn = createVoteButton("ABSTAIN", "#7f8c8d", res, GalacticResolution.VOTE_ABSTAIN, Color.LIGHTGRAY);

        topRow.getChildren().addAll(rTitle, ayeBtn, nayBtn, abstainBtn);

        StringBuilder votes = new StringBuilder("Registered votes: ");
        for (Map.Entry<String, String> v : res.votes().entrySet()) {
            votes.append(String.format("%s: %s  ", v.getKey(), v.getValue()));
        }
        Text vText = new Text(votes.toString());
        vText.setFill(Color.LIGHTGRAY);
        vText.setFont(Font.font("Verdana", 10));

        rBox.getChildren().addAll(topRow, vText);
        return rBox;
    }

    private Button createVoteButton(String label, String bgColor, GalacticResolution res, String voteType, Color feedbackColor) {
        Button btn = new Button(label);
        btn.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 10px;", bgColor));
        btn.setOnAction(e -> {
            if (humanController != null) {
                humanController.stageCommand(new VoteResolutionCommand(res.id(), playerEmpireId, voteType));
                feedbackLabel.setText("Cast vote " + label + " for resolution: " + res.title());
                feedbackLabel.setTextFill(feedbackColor);
            }
        });
        return btn;
    }

    private VBox buildPassedResolutionsSection() {
        VBox passedBox = new VBox(8);
        passedBox.setPadding(new Insets(10));
        passedBox.setStyle("-fx-background-color: rgba(30, 40, 75, 0.7); -fx-background-radius: 8; -fx-border-color: #2ecc71; -fx-border-width: 1; -fx-border-radius: 8;");
        Text passedHeader = new Text("Enacted galactic resolutions (active laws)");
        passedHeader.setFill(Color.LIGHTGREEN);
        passedHeader.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        passedBox.getChildren().add(passedHeader);

        if (community.passedResolutions().isEmpty()) {
            Text noPass = new Text("No galactic treaties or resolutions currently enacted into interstellar law.");
            noPass.setFill(Color.LIGHTGRAY);
            passedBox.getChildren().add(noPass);
        } else {
            for (GalacticResolution pass : community.passedResolutions()) {
                Text pText = new Text(String.format("✓ %s [%s] — Proposed by %s", pass.title(), pass.type(), pass.proposerEmpireId()));
                pText.setFill(Color.WHITE);
                pText.setFont(Font.font("Verdana", 11));
                passedBox.getChildren().add(pText);
            }
        }
        return passedBox;
    }

    private VBox buildSanctionsSection() {
        VBox sanctionBox = new VBox(8);
        sanctionBox.setPadding(new Insets(10));
        sanctionBox.setStyle("-fx-background-color: rgba(30, 40, 75, 0.7); -fx-background-radius: 8; -fx-border-color: #e74c3c; -fx-border-width: 1; -fx-border-radius: 8;");
        Text sHeader = new Text("Active multilateral senate sanctions");
        sHeader.setFill(Color.LIGHTPINK);
        sHeader.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        sanctionBox.getChildren().add(sHeader);

        if (community.activeSanctions().isEmpty()) {
            Text noSanc = new Text("No interstellar diplomatic or commercial sanctions currently in effect.");
            noSanc.setFill(Color.LIGHTGRAY);
            sanctionBox.getChildren().add(noSanc);
        } else {
            for (GalacticSanction s : community.activeSanctions()) {
                Text sText = new Text(String.format("⚠ [%s] Target: %s | Tariff Penalty: %.0f%% (Duration: %d turns)",
                        s.sanctionType(), s.targetEmpireId(), s.tradeTariffPenaltyRate() * 100.0, s.turnsRemaining()));
                sText.setFill(Color.SALMON);
                sText.setFont(Font.font("Verdana", 11));
                sanctionBox.getChildren().add(sText);
            }
        }
        return sanctionBox;
    }

    private VBox createResolutionProposalWorkbench() {
        VBox workbench = new VBox(8);
        workbench.setPadding(new Insets(10));
        workbench.setStyle("-fx-background-color: rgba(35, 45, 80, 0.7); -fx-background-radius: 8; -fx-border-color: #9b59b6; -fx-border-width: 1; -fx-border-radius: 8;");

        Text title = new Text("Introduce new senate resolution");
        title.setFill(Color.LIGHTBLUE);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 14));

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);

        TextField titleField = new TextField("Interstellar Commerce Standardization Act");
        titleField.setPrefWidth(260);

        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll(
                GalacticResolution.TYPE_FREE_TRADE,
                GalacticResolution.TYPE_MUTUAL_DEFENSE,
                GalacticResolution.TYPE_ANTI_PIRACY,
                GalacticResolution.TYPE_ENVIRONMENTAL_ACCORD,
                GalacticResolution.TYPE_SANCTION_EMBARGO,
                GalacticResolution.TYPE_MILITARY_INTERVENTION
        );
        typeCombo.setValue(GalacticResolution.TYPE_FREE_TRADE);

        TextField targetField = new TextField("");
        targetField.setPromptText("Target Empire ID (for sanctions)");
        targetField.setPrefWidth(200);

        Button proposeBtn = new Button("Introduce resolution");
        proposeBtn.setStyle("-fx-background-color: #8e44ad; -fx-text-fill: white; -fx-font-weight: bold;");
        proposeBtn.setOnAction(e -> {
            if (humanController != null) {
                humanController.stageCommand(new ProposeResolutionCommand(
                        playerEmpireId,
                        titleField.getText().trim(),
                        typeCombo.getValue(),
                        targetField.getText().trim().isEmpty() ? null : targetField.getText().trim()
                ));
                feedbackLabel.setText("Resolution introduced to Galactic Senate: " + titleField.getText().trim());
                feedbackLabel.setTextFill(Color.LIGHTGREEN);
            }
        });

        form.add(new Label("Title:"), 0, 0);
        form.add(titleField, 1, 0);
        form.add(new Label("Type:"), 2, 0);
        form.add(typeCombo, 3, 0);
        form.add(new Label("Target ID:"), 0, 1);
        form.add(targetField, 1, 1);
        form.add(proposeBtn, 3, 1);

        workbench.getChildren().addAll(title, form);
        return workbench;
    }
}
