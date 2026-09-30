package com.spaceconquest.control;

import com.spaceconquest.control.command.CommandQueue;
import com.spaceconquest.control.command.DeclareWarCommand;
import com.spaceconquest.control.command.EndWarCommand;
import com.spaceconquest.control.command.ProposeDiplomaticPactCommand;
import com.spaceconquest.control.command.ResolveDiplomaticProposalCommand;
import com.spaceconquest.control.command.WithdrawDiplomaticProposalCommand;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.governance.DiplomacyProcessor;
import com.spaceconquest.engine.governance.DiplomaticPact;
import com.spaceconquest.engine.governance.DiplomaticProposal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class DiplomaticCommandTest {

    private CommandQueue commandQueue;
    private GameState initialState;

    @BeforeEach
    void setUp() {
        commandQueue = new CommandQueue();
        Empire empireA = new Empire(
                "terran", "Terran Confederation", "human", "Individualist",
                50000.0, 0.05, List.of("sol"),
                List.of(), Map.of(), List.of(), List.of()
        );
        Empire empireB = new Empire(
                "vulkan", "Vulkan High Command", "vulkan", "Individualist",
                60000.0, 0.05, List.of("vulkan_sys"),
                List.of(), Map.of(), List.of(), List.of()
        );

        initialState = GameState.builder()
                .turn(1)
                .status("RUNNING")
                .empires(List.of(empireA, empireB))
                .build();
    }

    @Test
    void testDiplomaticPactIsPendingUntilReceiverAccepts() {
        ProposeDiplomaticPactCommand cmd = new ProposeDiplomaticPactCommand(
                "terran", "vulkan", DiplomaticPact.MUTUAL_TRADE_AGREEMENT
        );

        assertTrue(cmd.validate(initialState));
        GameState proposedState = cmd.apply(initialState);

        assertEquals(1, proposedState.diplomaticProposals().size());
        DiplomaticProposal proposal = proposedState.diplomaticProposals().getFirst();
        assertEquals(DiplomaticProposal.STATUS_PENDING, proposal.status());
        DiplomacyProcessor processor = new DiplomacyProcessor();
        assertEquals(DiplomacyProcessor.NEUTRAL,
                processor.getDiplomaticTier("terran", "vulkan", proposedState.diplomaticRelations()));

        GameState nextState = new ResolveDiplomaticProposalCommand(
                proposal.id(), "vulkan", true).apply(proposedState);

        String tier = processor.getDiplomaticTier("terran", "vulkan", nextState.diplomaticRelations());
        assertEquals(DiplomacyProcessor.COMMERCIAL_ALLIANCE, tier);
        assertEquals(DiplomaticProposal.STATUS_ACCEPTED, nextState.diplomaticProposals().getFirst().status());
    }

    @Test
    void rejectingDiplomaticPactLeavesRelationUnchanged() {
        GameState proposed = new ProposeDiplomaticPactCommand(
                "terran", "vulkan", DiplomaticPact.MUTUAL_TRADE_AGREEMENT).apply(initialState);
        String proposalId = proposed.diplomaticProposals().getFirst().id();

        GameState rejected = new ResolveDiplomaticProposalCommand(proposalId, "vulkan", false).apply(proposed);

        assertEquals(DiplomacyProcessor.NEUTRAL,
                new DiplomacyProcessor().getDiplomaticTier("terran", "vulkan", rejected.diplomaticRelations()));
        assertEquals(DiplomaticProposal.STATUS_REJECTED, rejected.diplomaticProposals().getFirst().status());
        assertFalse(new ResolveDiplomaticProposalCommand(proposalId, "terran", true).validate(rejected));
    }

    @Test
    void declaringWarRejectsPendingProposalBetweenTheEmpires() {
        GameState proposed = new ProposeDiplomaticPactCommand(
                "terran", "vulkan", DiplomaticPact.MUTUAL_TRADE_AGREEMENT).apply(initialState);
        String proposalId = proposed.diplomaticProposals().getFirst().id();

        GameState atWar = new DeclareWarCommand("terran", "vulkan", null).apply(proposed);

        assertEquals(DiplomaticProposal.STATUS_REJECTED, atWar.diplomaticProposals().getFirst().status());
        assertFalse(new ResolveDiplomaticProposalCommand(proposalId, "vulkan", true).validate(atWar));
    }

    @Test
    void testDeclareWarCommandSetsTotalWarTier() {
        DeclareWarCommand cmd = new DeclareWarCommand("terran", "vulkan", "cb_border_1");
        assertTrue(cmd.validate(initialState));

        GameState nextState = cmd.apply(initialState);
        DiplomacyProcessor processor = new DiplomacyProcessor();
        String tier = processor.getDiplomaticTier("terran", "vulkan", nextState.diplomaticRelations());
        assertEquals(DiplomacyProcessor.TOTAL_WAR, tier);
        assertEquals(1, nextState.warDeclarations().size());
        assertTrue(nextState.warDeclarations().getFirst().justified());
        assertEquals("cb_border_1", nextState.warDeclarations().getFirst().casusBelliId());
        assertEquals(0.0, nextState.warDeclarations().getFirst().civilianHappinessPenalty());
    }

    @Test
    void testUnprovokedWarPersistsCalculatedPenalties() {
        GameState nextState = new DeclareWarCommand("terran", "vulkan", null).apply(initialState);

        assertEquals(1, nextState.warDeclarations().size());
        assertFalse(nextState.warDeclarations().getFirst().justified());
        assertEquals(-0.40, nextState.warDeclarations().getFirst().civilianHappinessPenalty());
        assertEquals(0.0, nextState.warDeclarations().getFirst().corporateTrustPenalty());
        assertEquals("Unprovoked aggression", nextState.warDeclarations().getFirst().justificationSummary());
    }

    @Test
    void declaringWarBreaksCommercialAllianceAndRecordsTrustPenalty() {
        GameState alliedState = initialState.withDiplomaticRelations(List.of(
                new com.spaceconquest.engine.DiplomaticRelation("terran", "vulkan",
                        DiplomacyProcessor.COMMERCIAL_ALLIANCE, 0.5)));

        GameState nextState = new DeclareWarCommand("terran", "vulkan", "cb_border_1").apply(alliedState);

        assertEquals(-50.0, nextState.warDeclarations().getFirst().corporateTrustPenalty());
        assertEquals(DiplomacyProcessor.TOTAL_WAR,
                new DiplomacyProcessor().getDiplomaticTier("terran", "vulkan", nextState.diplomaticRelations()));
    }

    @Test
    void endWarReturnsRelationToNeutralAndPreservesDeclarationHistory() {
        GameState atWar = new DeclareWarCommand("terran", "vulkan", null).apply(initialState);
        EndWarCommand command = new EndWarCommand("terran", "vulkan");

        assertTrue(command.validate(atWar));
        GameState peace = command.apply(atWar);

        assertEquals(DiplomacyProcessor.NEUTRAL,
                new DiplomacyProcessor().getDiplomaticTier("terran", "vulkan", peace.diplomaticRelations()));
        assertEquals(atWar.warDeclarations(), peace.warDeclarations());
        assertFalse(command.validate(peace));
    }

    @Test
    void senderCanWithdrawOnlyAnUnexpiredPendingProposal() {
        GameState proposed = new ProposeDiplomaticPactCommand(
                "terran", "vulkan", DiplomaticPact.MUTUAL_TRADE_AGREEMENT).apply(initialState);
        String proposalId = proposed.diplomaticProposals().getFirst().id();
        WithdrawDiplomaticProposalCommand command = new WithdrawDiplomaticProposalCommand(proposalId, "terran");

        assertTrue(command.validate(proposed));
        assertFalse(new WithdrawDiplomaticProposalCommand(proposalId, "vulkan").validate(proposed));
        GameState withdrawn = command.apply(proposed);

        assertEquals(DiplomaticProposal.STATUS_WITHDRAWN, withdrawn.diplomaticProposals().getFirst().status());
        assertFalse(command.validate(withdrawn));
    }

    @Test
    void pendingProposalsExpireAfterThirtyTurns() {
        GameState proposed = new ProposeDiplomaticPactCommand(
                "terran", "vulkan", DiplomaticPact.MUTUAL_TRADE_AGREEMENT).apply(initialState);
        DiplomaticProposal proposal = proposed.diplomaticProposals().getFirst();
        ResolveDiplomaticProposalCommand resolver = new ResolveDiplomaticProposalCommand(
                proposal.id(), "vulkan", true);

        assertEquals(31, proposal.expiresOnTurn());
        assertTrue(resolver.validate(proposed.withTurn(30)));
        assertFalse(resolver.validate(proposed.withTurn(31)));
        assertEquals(DiplomaticProposal.STATUS_EXPIRED,
                new DiplomacyProcessor().expirePendingProposals(proposed.diplomaticProposals(), 31)
                        .getFirst().status());
    }
}
