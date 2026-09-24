package com.spaceconquest.control;

import com.spaceconquest.control.ai.EmpireAIController;
import com.spaceconquest.control.command.CommandQueue;
import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
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
}
