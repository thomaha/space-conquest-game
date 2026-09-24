package com.spaceconquest.control;

import com.spaceconquest.control.command.CommandQueue;
import com.spaceconquest.control.command.DeclareWarCommand;
import com.spaceconquest.control.command.ProposeDiplomaticPactCommand;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.governance.DiplomacyProcessor;
import com.spaceconquest.engine.governance.DiplomaticPact;
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
    void testProposeDiplomaticPactElevatesTier() {
        ProposeDiplomaticPactCommand cmd = new ProposeDiplomaticPactCommand(
                "terran", "vulkan", DiplomaticPact.MUTUAL_TRADE_AGREEMENT
        );

        assertTrue(cmd.validate(initialState));
        GameState nextState = cmd.apply(initialState);

        DiplomacyProcessor processor = new DiplomacyProcessor();
        String tier = processor.getDiplomaticTier("terran", "vulkan", nextState.diplomaticRelations());
        assertEquals(DiplomacyProcessor.COMMERCIAL_ALLIANCE, tier);
    }

    @Test
    void testDeclareWarCommandSetsTotalWarTier() {
        DeclareWarCommand cmd = new DeclareWarCommand("terran", "vulkan", "cb_border_1");
        assertTrue(cmd.validate(initialState));

        GameState nextState = cmd.apply(initialState);
        DiplomacyProcessor processor = new DiplomacyProcessor();
        String tier = processor.getDiplomaticTier("terran", "vulkan", nextState.diplomaticRelations());
        assertEquals(DiplomacyProcessor.TOTAL_WAR, tier);
    }
}
