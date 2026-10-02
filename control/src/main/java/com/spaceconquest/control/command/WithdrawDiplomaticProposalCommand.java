package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.governance.DiplomaticProposal;

import java.util.List;

/** Withdraws a still-pending treaty proposal by its sending empire. */
public record WithdrawDiplomaticProposalCommand(String proposalId, String senderEmpireId) implements GameCommand {
    @Override
    public boolean validate(GameState state) {
        if (state == null || proposalId == null || senderEmpireId == null) return false;
        boolean senderExists = state.empires().stream().anyMatch(empire -> senderEmpireId.equals(empire.id()));
        if (!senderExists) return false;
        return state.diplomaticProposals().stream().anyMatch(proposal ->
                proposalId.equals(proposal.id())
                        && senderEmpireId.equals(proposal.senderEmpireId())
                        && proposal.isPendingAtTurn(state.turn()));
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) return state;
        List<DiplomaticProposal> proposals = state.diplomaticProposals().stream()
                .map(proposal -> proposalId.equals(proposal.id())
                        ? proposal.withStatus(DiplomaticProposal.STATUS_WITHDRAWN)
                        : proposal)
                .toList();
        return state.toBuilder().diplomaticProposals(proposals).build();
    }
}
