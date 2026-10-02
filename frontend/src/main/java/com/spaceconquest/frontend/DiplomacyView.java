package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.DeclareWarCommand;
import com.spaceconquest.control.command.EndWarCommand;
import com.spaceconquest.control.command.GameCommand;
import com.spaceconquest.control.command.ProposeDiplomaticPactCommand;
import com.spaceconquest.control.command.ResolveDiplomaticProposalCommand;
import com.spaceconquest.control.command.WithdrawDiplomaticProposalCommand;
import com.spaceconquest.engine.DiplomaticRelation;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.governance.DiplomaticPact;
import com.spaceconquest.engine.governance.DiplomaticProposal;
import com.spaceconquest.engine.governance.DiplomacyProcessor;
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
import java.util.List;

/**
 * UI panel displaying galactic diplomatic relations, treaties, influence values, and bilateral stances.
 */
public class DiplomacyView {
    private VBox root;
    private VBox content;
    private List<Empire> empires = List.of();
    private List<DiplomaticRelation> relations = List.of();
    private List<DiplomaticProposal> proposals = List.of();
    private long currentTurn;
    private final Menubar menubar;
    private final DiplomacyProcessor diplomacyProcessor = new DiplomacyProcessor();
    private HumanController humanController;

    public DiplomacyView(Menubar menubar) {
        this.menubar = menubar;
    }

    public void initializeAfterConstruction() {
        build();
    }

    private void build() {
        root = new VBox(20);
        content = new VBox(15);
        ScrollPane scrollPane = new ScrollPane(content);

        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: rgba(12, 20, 42, 0.95); " +
                "-fx-border-color: #78aaff; -fx-border-width: 2; " +
                "-fx-border-radius: 10; -fx-background-radius: 10;");
        root.setPrefSize(750, 550);

        Text title = new Text("Galactic diplomacy matrix");
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

    public void setHumanController(HumanController humanController) {
        this.humanController = humanController;
    }

    public void show() {
        loadData();
        root.setVisible(true);
        root.toFront();
    }

    public void updateData(GameState state) {
        empires = state == null ? List.of() : state.empires();
        relations = state == null ? List.of() : state.diplomaticRelations();
        proposals = state == null ? List.of() : state.diplomaticProposals();
        currentTurn = state == null ? 0 : state.turn();
        if (root.isVisible()) loadData();
    }

    public void hide() {
        root.setVisible(false);
        if (menubar != null) {
            menubar.closePage();
        }
    }

    private void loadData() {
        content.getChildren().clear();
        for (Empire empire : empires) {
            content.getChildren().add(createDiplomacyBox(empire, empires, relations, proposals));
        }
    }

    private VBox createDiplomacyBox(Empire empire, List<Empire> allEmpires,
                                    List<DiplomaticRelation> diplomaticRelations,
                                    List<DiplomaticProposal> diplomaticProposals) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: rgba(40, 60, 100, 0.6); -fx-background-radius: 6;");

        Text nameText = new Text(empire.name() + " (" + empire.raceId() + ")");
        nameText.setFill(Color.LIGHTBLUE);
        nameText.setFont(Font.font("Verdana", FontWeight.BOLD, 16));

        Text postureText = new Text("Ideology: " + empire.societyStructure() + " | Foreign relations:");
        postureText.setFill(Color.GAINSBORO);
        postureText.setFont(Font.font("Verdana", 12));

        box.getChildren().addAll(nameText, postureText);

        if (empire.id().equals(menubar.getPlayerEmpireId())) {
            List<DiplomaticProposal> relevantProposals = diplomaticProposals.stream()
                    .filter(proposal -> empire.id().equals(proposal.senderEmpireId())
                            || empire.id().equals(proposal.receiverEmpireId()))
                    .toList();
            if (!relevantProposals.isEmpty()) {
                Text proposalHeading = new Text("Treaty proposals");
                proposalHeading.setFill(Color.WHITE);
                proposalHeading.setFont(Font.font("Verdana", FontWeight.BOLD, 13));
                box.getChildren().add(proposalHeading);
            }
            for (DiplomaticProposal proposal : diplomaticProposals) {
                boolean isSender = empire.id().equals(proposal.senderEmpireId());
                boolean isReceiver = empire.id().equals(proposal.receiverEmpireId());
                if (!isSender && !isReceiver) continue;
                String otherEmpireId = isSender ? proposal.receiverEmpireId() : proposal.senderEmpireId();
                Empire otherEmpire = allEmpires.stream()
                        .filter(candidate -> candidate.id().equals(otherEmpireId))
                        .findFirst().orElse(null);
                if (otherEmpire == null) continue;
                HBox proposalRow = new HBox(8);
                proposalRow.setAlignment(Pos.CENTER_LEFT);
                String direction = isSender ? "To " : "From ";
                String status = DiplomaticProposal.STATUS_PENDING.equals(proposal.status())
                        && currentTurn >= proposal.expiresOnTurn()
                        ? DiplomaticProposal.STATUS_EXPIRED : proposal.status();
                Text proposalText = new Text(direction + otherEmpire.name() + ": "
                        + proposal.proposalType() + " — " + status);
                proposalText.setFill(Color.LIGHTGOLDENRODYELLOW);
                proposalRow.getChildren().add(proposalText);
                if (proposal.isPendingAtTurn(currentTurn)) {
                    String tier = diplomacyProcessor.getDiplomaticTier(empire.id(), otherEmpire.id(), relations);
                    if (DiplomacyProcessor.TOTAL_WAR.equalsIgnoreCase(tier)) {
                        proposalText.setText(proposalText.getText() + " (unavailable during war)");
                    } else if (isReceiver) {
                        Button acceptButton = new Button("Accept");
                        acceptButton.setOnAction(e -> stageCommand(new ResolveDiplomaticProposalCommand(
                                proposal.id(), empire.id(), true)));
                        Button rejectButton = new Button("Reject");
                        rejectButton.setOnAction(e -> stageCommand(new ResolveDiplomaticProposalCommand(
                                proposal.id(), empire.id(), false)));
                        proposalRow.getChildren().addAll(acceptButton, rejectButton);
                    } else {
                        Button withdrawButton = new Button("Withdraw");
                        withdrawButton.setOnAction(e -> stageCommand(new WithdrawDiplomaticProposalCommand(
                                proposal.id(), empire.id())));
                        proposalRow.getChildren().add(withdrawButton);
                    }
                }
                box.getChildren().add(proposalRow);
            }
        }

        for (Empire other : allEmpires) {
            if (!other.id().equals(empire.id())) {
                String tier = diplomacyProcessor.getDiplomaticTier(empire.id(), other.id(), diplomaticRelations);
                double discount = getTariffDiscount(empire.id(), other.id(), tier, diplomaticRelations);
                
                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);

                Text rel = new Text(String.format("• vs %s: %s (Tariff discount: %.0f%%)",
                        other.name(), tier, discount * 100.0));
                rel.setFill(Color.LIGHTSKYBLUE);
                rel.setFont(Font.font("Verdana", 11));

                Button proposeTradeBtn = new Button("Propose trade");
                proposeTradeBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-size: 10px;");
                proposeTradeBtn.setOnAction(e -> stageCommand(new ProposeDiplomaticPactCommand(
                        menubar.getPlayerEmpireId(), other.id(), DiplomaticPact.MUTUAL_TRADE_AGREEMENT)));

                Button declareWarBtn = new Button("Declare war");
                declareWarBtn.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-size: 10px;");
                declareWarBtn.setOnAction(e -> stageCommand(new DeclareWarCommand(
                        menubar.getPlayerEmpireId(), other.id(), null)));

                Button endWarBtn = new Button("End war");
                endWarBtn.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; -fx-font-size: 10px;");
                endWarBtn.setOnAction(e -> stageCommand(new EndWarCommand(
                        menubar.getPlayerEmpireId(), other.id())));

                row.getChildren().add(rel);
                if (empire.id().equals(menubar.getPlayerEmpireId())) {
                    if (DiplomacyProcessor.TOTAL_WAR.equalsIgnoreCase(tier)) {
                        row.getChildren().add(endWarBtn);
                    } else if (hasPendingProposal(empire.id(), other.id(), diplomaticProposals, currentTurn)) {
                        Text pending = new Text("Proposal pending");
                        pending.setFill(Color.LIGHTGOLDENRODYELLOW);
                        row.getChildren().add(pending);
                    } else {
                        row.getChildren().addAll(proposeTradeBtn, declareWarBtn);
                    }
                }
                box.getChildren().add(row);
            }
        }

        return box;
    }

    private boolean hasPendingProposal(String empireAId, String empireBId,
                                      List<DiplomaticProposal> diplomaticProposals, long turn) {
        return diplomaticProposals.stream().anyMatch(proposal ->
                proposal.isPendingAtTurn(turn)
                        && ((empireAId.equals(proposal.senderEmpireId())
                        && empireBId.equals(proposal.receiverEmpireId()))
                        || (empireBId.equals(proposal.senderEmpireId())
                        && empireAId.equals(proposal.receiverEmpireId()))));
    }

    private void stageCommand(GameCommand command) {
        if (humanController != null) {
            humanController.stageCommand(command);
        }
    }

    private double getTariffDiscount(String empireAId, String empireBId, String tier,
                                     List<DiplomaticRelation> diplomaticRelations) {
        return diplomaticRelations.stream()
                .filter(relation -> (relation.empireAId().equals(empireAId)
                        && relation.empireBId().equals(empireBId))
                        || (relation.empireAId().equals(empireBId)
                        && relation.empireBId().equals(empireAId)))
                .mapToDouble(DiplomaticRelation::mutualTariffDiscount)
                .findFirst()
                .orElseGet(() -> diplomacyProcessor.getDefaultTariffDiscount(tier));
    }
}
