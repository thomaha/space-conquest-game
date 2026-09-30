package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.governance.DiplomacyProcessor;
import com.spaceconquest.engine.governance.DiplomaticPact;
import com.spaceconquest.engine.governance.DiplomaticProposal;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Command to dispatch an official diplomatic proposal seeking to ratify a bilateral treaty.
 */
public record ProposeDiplomaticPactCommand(
        String senderEmpireId,
        String receiverEmpireId,
        String pactType
) implements GameCommand {

    @Override
    public boolean validate(GameState state) {
        if (state == null || senderEmpireId == null || receiverEmpireId == null || pactType == null) {
            return false;
        }
        if (senderEmpireId.equals(receiverEmpireId)) return false;

        boolean senderExists = state.empires().stream().anyMatch(e -> e.id().equals(senderEmpireId));
        boolean receiverExists = state.empires().stream().anyMatch(e -> e.id().equals(receiverEmpireId));
        boolean supportedPact = DiplomaticPact.MUTUAL_TRADE_AGREEMENT.equalsIgnoreCase(pactType)
                || DiplomaticPact.DEFENSIVE_PACT.equalsIgnoreCase(pactType)
                || DiplomacyProcessor.INTEGRATED_FEDERATION.equalsIgnoreCase(pactType);
        boolean atWar = DiplomacyProcessor.TOTAL_WAR.equalsIgnoreCase(
                new DiplomacyProcessor().getDiplomaticTier(senderEmpireId, receiverEmpireId,
                        state.diplomaticRelations()));
        boolean alreadyPending = state.diplomaticProposals().stream().anyMatch(proposal ->
                proposal.isPendingAtTurn(state.turn())
                        && ((proposal.senderEmpireId().equals(senderEmpireId)
                        && proposal.receiverEmpireId().equals(receiverEmpireId))
                        || (proposal.senderEmpireId().equals(receiverEmpireId)
                        && proposal.receiverEmpireId().equals(senderEmpireId))));
        return senderExists && receiverExists && supportedPact && !atWar && !alreadyPending;
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        DiplomaticProposal proposal = new DiplomaticProposal(
                "prop_" + UUID.randomUUID(),
                senderEmpireId,
                receiverEmpireId,
                pactType,
                state.turn() + DiplomaticProposal.DEFAULT_DURATION_TURNS,
                DiplomaticProposal.STATUS_PENDING
        );
        ArrayList<DiplomaticProposal> proposals = new ArrayList<>(state.diplomaticProposals());
        proposals.add(proposal);
        return state.toBuilder().diplomaticProposals(proposals).build();
    }
}
