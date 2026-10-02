package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.governance.DiplomaticPact;
import com.spaceconquest.engine.governance.DiplomaticProposal;
import com.spaceconquest.engine.governance.DiplomacyProcessor;

import java.util.ArrayList;
import java.util.List;

/** Resolves a pending diplomatic proposal on behalf of its receiving empire. */
public record ResolveDiplomaticProposalCommand(
        String proposalId,
        String receiverEmpireId,
        boolean accepted
) implements GameCommand {
    @Override
    public boolean validate(GameState state) {
        if (state == null || proposalId == null || receiverEmpireId == null) return false;
        return state.diplomaticProposals().stream().anyMatch(proposal ->
                proposalId.equals(proposal.id())
                        && receiverEmpireId.equals(proposal.receiverEmpireId())
                        && proposal.isPendingAtTurn(state.turn())
                        && !DiplomacyProcessor.TOTAL_WAR.equalsIgnoreCase(
                        new DiplomacyProcessor().getDiplomaticTier(proposal.senderEmpireId(),
                                proposal.receiverEmpireId(), state.diplomaticRelations())));
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) return state;
        DiplomaticProposal selected = state.diplomaticProposals().stream()
                .filter(proposal -> proposalId.equals(proposal.id())).findFirst().orElseThrow();
        String status = accepted ? DiplomaticProposal.STATUS_ACCEPTED : DiplomaticProposal.STATUS_REJECTED;
        List<DiplomaticProposal> updated = new ArrayList<>(state.diplomaticProposals().stream()
                .map(proposal -> proposalId.equals(proposal.id())
                        ? proposal.withStatus(status)
                        : proposal)
                .toList());
        GameState resolved = state.toBuilder().diplomaticProposals(updated).build();
        if (!accepted) return resolved;

        String tier = DiplomaticPact.MUTUAL_TRADE_AGREEMENT.equalsIgnoreCase(selected.proposalType())
                ? DiplomacyProcessor.COMMERCIAL_ALLIANCE
                : DiplomacyProcessor.INTEGRATED_FEDERATION;
        return new SetDiplomaticTierCommand(selected.senderEmpireId(), selected.receiverEmpireId(), tier)
                .apply(resolved);
    }
}
