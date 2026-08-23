package com.spaceconquest.control.command;

import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.governance.DiplomacyProcessor;
import com.spaceconquest.engine.governance.DiplomaticPact;
import com.spaceconquest.engine.governance.DiplomaticProposal;

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
        return senderExists && receiverExists;
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        Empire sender = state.empires().stream().filter(e -> e.id().equals(senderEmpireId)).findFirst().orElse(null);
        Empire receiver = state.empires().stream().filter(e -> e.id().equals(receiverEmpireId)).findFirst().orElse(null);

        DiplomacyProcessor processor = new DiplomacyProcessor();
        DiplomaticProposal proposal = new DiplomaticProposal(
                "prop_" + senderEmpireId + "_" + receiverEmpireId,
                senderEmpireId,
                receiverEmpireId,
                pactType,
                DiplomaticProposal.STATUS_PENDING
        );

        boolean accepted = processor.evaluateProposalAcceptance(
                proposal, sender, receiver, state.diplomaticRelations(), 1.0
        );

        if (accepted) {
            // If accepted, elevate relation tier if appropriate (e.g. mutual trade elevates to Commercial Alliance)
            if (DiplomaticPact.MUTUAL_TRADE_AGREEMENT.equalsIgnoreCase(pactType)) {
                return new SetDiplomaticTierCommand(senderEmpireId, receiverEmpireId, DiplomacyProcessor.COMMERCIAL_ALLIANCE).apply(state);
            } else if (DiplomaticPact.DEFENSIVE_PACT.equalsIgnoreCase(pactType) || DiplomacyProcessor.INTEGRATED_FEDERATION.equalsIgnoreCase(pactType)) {
                return new SetDiplomaticTierCommand(senderEmpireId, receiverEmpireId, DiplomacyProcessor.INTEGRATED_FEDERATION).apply(state);
            }
        }

        return state;
    }
}
