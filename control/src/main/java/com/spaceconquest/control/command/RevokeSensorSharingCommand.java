package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.galaxy.SensorSharing;
import com.spaceconquest.engine.governance.*;

/** Either signatory can revoke a ratified sensor grant on the next simulation tick. */
public record RevokeSensorSharingCommand(String agreementId, String partyId) implements GameCommand {
    public boolean validate(GameState state) {
        return state != null && agreementId != null && SensorSharing.partyExists(state, partyId)
                && state.diplomaticProposals().stream().anyMatch(proposal -> agreementId.equals(proposal.id())
                && (partyId.equals(proposal.senderEmpireId()) || partyId.equals(proposal.receiverEmpireId()))
                && DiplomaticPact.SENSOR_DATA_SHARING.equalsIgnoreCase(proposal.proposalType())
                && DiplomaticProposal.STATUS_ACCEPTED.equals(proposal.status()));
    }
    public GameState apply(GameState state) {
        if (!validate(state)) return state;
        return state.toBuilder().diplomaticProposals(state.diplomaticProposals().stream().map(proposal ->
                agreementId.equals(proposal.id()) ? proposal.withStatus(DiplomaticProposal.STATUS_REVOKED) : proposal).toList()).build();
    }
}
