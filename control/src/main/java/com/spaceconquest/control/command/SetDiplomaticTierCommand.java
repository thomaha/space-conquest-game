package com.spaceconquest.control.command;

import com.spaceconquest.engine.DiplomaticRelation;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.governance.DiplomacyProcessor;
import com.spaceconquest.engine.governance.DiplomaticProposal;

import java.util.List;

/**
 * Command to set or transition the bilateral diplomatic relation tier between two empires.
 */
public record SetDiplomaticTierCommand(
        String empireAId,
        String empireBId,
        String newTier
) implements GameCommand {

    private static final DiplomacyProcessor diplomacyProcessor = new DiplomacyProcessor();

    @Override
    public boolean validate(GameState state) {
        if (state == null || empireAId == null || empireBId == null || newTier == null) return false;
        if (empireAId.equals(empireBId)) return false;

        boolean empireAExists = state.empires().stream().anyMatch(e -> e.id().equals(empireAId));
        boolean empireBExists = state.empires().stream().anyMatch(e -> e.id().equals(empireBId));
        return empireAExists && empireBExists;
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) return state;

        List<DiplomaticRelation> updatedRelations = diplomacyProcessor.setDiplomaticTier(
                empireAId,
                empireBId,
                newTier,
                state.diplomaticRelations()
        );

        GameState updated = state.toBuilder()
                .diplomaticRelations(updatedRelations)
                .build();
        if (!DiplomacyProcessor.TOTAL_WAR.equalsIgnoreCase(newTier)) return updated;

        List<DiplomaticProposal> proposals = updated.diplomaticProposals().stream()
                .map(proposal -> isPendingProposalBetween(proposal, empireAId, empireBId, updated.turn())
                        ? proposal.withStatus(DiplomaticProposal.STATUS_REJECTED)
                        : proposal)
                .toList();
        return updated.toBuilder().diplomaticProposals(proposals).build();
    }

    private boolean isPendingProposalBetween(DiplomaticProposal proposal, String empireAId, String empireBId,
                                             long currentTurn) {
        if (!proposal.isPendingAtTurn(currentTurn)) return false;
        return (empireAId.equals(proposal.senderEmpireId()) && empireBId.equals(proposal.receiverEmpireId()))
                || (empireBId.equals(proposal.senderEmpireId()) && empireAId.equals(proposal.receiverEmpireId()));
    }
}
