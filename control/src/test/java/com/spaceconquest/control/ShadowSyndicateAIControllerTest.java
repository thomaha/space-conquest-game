package com.spaceconquest.control;

import com.spaceconquest.control.ai.ShadowSyndicateAIController;
import com.spaceconquest.control.command.CommandQueue;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ShadowSyndicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ShadowSyndicateAIControllerTest {

    private CommandQueue commandQueue;
    private ShadowSyndicateAIController syndicateAI;

    @BeforeEach
    public void setUp() {
        commandQueue = new CommandQueue();
        syndicateAI = new ShadowSyndicateAIController("syndicate_shadow_1", commandQueue);
    }

    @Test
    public void testShadowSyndicateAIControllerObservesState() {
        ShadowSyndicate syndicate = new ShadowSyndicate(
                "syndicate_shadow_1",
                "Orion Corsairs",
                "terran",
                "sol",
                7500.0,
                List.of("pirate_raider_1")
        );

        GameState state = new GameState(
                1,
                "RUNNING",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(syndicate),
                List.of(),
                List.of()
        );

        assertEquals("syndicate_shadow_1", syndicateAI.getSyndicateId());
        assertDoesNotThrow(() -> syndicateAI.onGameStateUpdate(state));
        assertDoesNotThrow(() -> syndicateAI.onGameStateUpdate(null));
    }
}
