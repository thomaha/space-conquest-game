package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.galaxy.SensorSharing;
import com.spaceconquest.engine.governance.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.LinkedHashMap;

/** Snapshot-only sensor agreement controls. Every change is queued for the simulation tick. */
final class SensorSharingCard {
    private SensorSharingCard() {}
    static VBox create(GameState state, String empireId, HumanController controller) {
        var box = new VBox(6); box.setId("sensor-sharing-card");
        box.getChildren().add(label("Sensor data sharing"));
        if (state == null || empireId == null) return box;
        var names = new LinkedHashMap<String, String>();
        state.empires().forEach(empire -> names.put(empire.id(), empire.name()));
        state.corporations().forEach(corporation -> names.put(corporation.id(), corporation.name() + " (corporation)"));
        box.getChildren().add(label("Affiliated corporate sensors join the empire view. Foreign sharing requires an accepted agreement."));
        for (var proposal : state.diplomaticProposals()) {
            if (!DiplomaticPact.SENSOR_DATA_SHARING.equalsIgnoreCase(proposal.proposalType())
                    || !empireId.equals(proposal.senderEmpireId()) && !empireId.equals(proposal.receiverEmpireId())) continue;
            String other = empireId.equals(proposal.senderEmpireId()) ? proposal.receiverEmpireId() : proposal.senderEmpireId();
            var row = new HBox(6, label(names.getOrDefault(other, other) + ": " + proposal.status()));
            if (DiplomaticProposal.STATUS_ACCEPTED.equals(proposal.status()))
                row.getChildren().add(action("Revoke sharing", "revoke-sensors-" + proposal.id(), controller,
                        new RevokeSensorSharingCommand(proposal.id(), empireId)));
            else if (proposal.isPendingAtTurn(state.turn())) {
                if (empireId.equals(proposal.receiverEmpireId())) row.getChildren().addAll(
                        action("Accept", "accept-sensors-" + proposal.id(), controller, new ResolveDiplomaticProposalCommand(proposal.id(), empireId, true)),
                        action("Reject", "reject-sensors-" + proposal.id(), controller, new ResolveDiplomaticProposalCommand(proposal.id(), empireId, false)));
                else row.getChildren().add(action("Withdraw", "withdraw-sensors-" + proposal.id(), controller,
                        new WithdrawDiplomaticProposalCommand(proposal.id(), empireId)));
            }
            box.getChildren().add(row);
        }
        for (var party : names.entrySet()) {
            if (empireId.equals(party.getKey()) || empireId.equals(SensorSharing.homeEmpire(state, party.getKey()))) continue;
            var command = new ProposeDiplomaticPactCommand(empireId, party.getKey(), DiplomaticPact.SENSOR_DATA_SHARING);
            var button = action("Propose sensor sharing", "propose-sensors-" + party.getKey(), controller, command);
            button.setDisable(controller == null || !command.validate(state));
            box.getChildren().add(new HBox(6, label(party.getValue()), button));
        }
        return box;
    }
    private static Button action(String label, String id, HumanController controller, GameCommand command) {
        var button = new Button(label); button.setId(id); button.setDisable(controller == null);
        button.setOnAction(event -> { if (controller != null) controller.stageCommand(command); });
        return button;
    }
    private static Label label(String text) {
        var label = new Label(text); label.setStyle("-fx-text-fill: #dce8ff;"); label.setWrapText(true); return label;
    }
}
