package com.spaceconquest.control;

import com.spaceconquest.control.ai.EmpireAIController;
import com.spaceconquest.control.command.CommandQueue;
import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.governance.DiplomaticProposal;
import com.spaceconquest.engine.governance.DiplomacyProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class EmpireAIControllerTest {

    private CommandQueue commandQueue;
    private EmpireAIController empireAI;

    @BeforeEach
    public void setUp() {
        commandQueue = new CommandQueue();
        empireAI = new EmpireAIController("terran", commandQueue);
    }

    @Test
    public void testEmpireAIAppointsCabinetAndGovernors() {
        Empire terran = new Empire(
                "terran",
                "Terran Confederation",
                "human",
                "Individualist",
                50000.0,
                0.15,
                List.of("sol"),
                List.of(),
                Map.of(),
                List.of(),
                List.of()
        );

        GameState state = GameState.builder()
                .turn(1)
                .status("RUNNING")
                .empires(List.of(terran))
                .build();

        empireAI.onGameStateUpdate(state);
        assertFalse(commandQueue.isEmpty(), "Empire AI should stage commands for empty cabinet and governor");

        GameState updated = commandQueue.drainAndExecute(state);
        Empire updatedEmpire = updated.empires().getFirst();

        assertFalse(updatedEmpire.ministries().isEmpty(), "Cabinet ministries should be appointed");
        assertFalse(updated.systemGovernors().isEmpty(), "Governor should be assigned");
    }

    @Test
    public void testEmpireAISubsidizesStrugglingCorporation() {
        Empire terran = new Empire(
                "terran",
                "Terran Confederation",
                "human",
                "Individualist",
                50000.0,
                0.15,
                List.of("sol"),
                List.of(),
                Map.of("sol", "gov_1"),
                List.of(),
                List.of()
        );

        Corporation brokeCorp = new Corporation(
                "corp_broke",
                "Struggling Transport",
                "terran",
                "earth",
                "TRANSPORT",
                1000.0, // under 5000 threshold
                List.of(),
                List.of(),
                List.of()
        );

        GameState state = GameState.builder()
                .turn(1)
                .status("RUNNING")
                .empires(List.of(terran))
                .corporations(List.of(brokeCorp))
                .build();

        empireAI.onGameStateUpdate(state);
        GameState updated = commandQueue.drainAndExecute(state);

        Corporation subsidizedCorp = updated.corporations().getFirst();
        assertEquals(6000.0, subsidizedCorp.liquidCapitalReserves(), 0.001);
    }

    @Test
    public void eachAiEmpireResolvesItsOwnIncomingDiplomaticProposal() {
        Empire player = empire("player", "Individualist");
        Empire aiOne = empire("ai_one", "Collectivist");
        Empire aiTwo = empire("ai_two", "Collectivist");
        GameState state = GameState.builder().empires(List.of(player, aiOne, aiTwo))
                .diplomaticProposals(List.of(
                        new DiplomaticProposal("proposal_one", "player", "ai_one",
                                "MUTUAL_TRADE_AGREEMENT", DiplomaticProposal.STATUS_PENDING),
                        new DiplomaticProposal("proposal_two", "player", "ai_two",
                                "MUTUAL_TRADE_AGREEMENT", DiplomaticProposal.STATUS_PENDING)))
                .build();
        CommandQueue sharedQueue = new CommandQueue();

        new EmpireAIController("ai_one", sharedQueue).onGameStateUpdate(state);
        new EmpireAIController("ai_two", sharedQueue).onGameStateUpdate(state);
        GameState resolved = sharedQueue.drainAndExecute(state);

        assertTrue(resolved.diplomaticProposals().stream()
                .allMatch(proposal -> DiplomaticProposal.STATUS_ACCEPTED.equals(proposal.status())));
        DiplomacyProcessor diplomacy = new DiplomacyProcessor();
        assertEquals(DiplomacyProcessor.COMMERCIAL_ALLIANCE,
                diplomacy.getDiplomaticTier("player", "ai_one", resolved.diplomaticRelations()));
        assertEquals(DiplomacyProcessor.COMMERCIAL_ALLIANCE,
                diplomacy.getDiplomaticTier("player", "ai_two", resolved.diplomaticRelations()));
    }

    private Empire empire(String id, String society) {
        return new Empire(id, id, "human", society, 1000.0, 0.1,
                List.of(), List.of(), Map.of(), List.of(), List.of());
    }
}
