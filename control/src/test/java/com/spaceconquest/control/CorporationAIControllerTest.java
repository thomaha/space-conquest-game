package com.spaceconquest.control;

import com.spaceconquest.control.ai.CorporationAIController;
import com.spaceconquest.control.command.CommandQueue;
import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.MarketOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class CorporationAIControllerTest {

    private CommandQueue commandQueue;
    private CorporationAIController corpAI;

    @BeforeEach
    public void setUp() {
        commandQueue = new CommandQueue();
        corpAI = new CorporationAIController("corp_mining", commandQueue);
    }

    @Test
    public void testCorporationAIInvestsInInfrastructureWhenShortcomingIsHigh() {
        // High deficit in iron ore (shortcoming = 0.85)
        MarketOrder order = new MarketOrder("iron_ore", 10.0, 500.0, 15.0, 0.85);
        CommercialHub hub = new CommercialHub(
                "hub_earth",
                "earth",
                0.05,
                100000.0,
                5000.0,
                10.0,
                Map.of("iron_ore", order)
        );

        Corporation corp = new Corporation(
                "corp_mining",
                "Mining Syndicate",
                "terran",
                "earth",
                "EXTRACTION",
                25000.0,
                List.of(),
                List.of(),
                List.of()
        );

        GameState state = new GameState(
                1,
                "RUNNING",
                List.of(),
                List.of(),
                List.of(corp),
                List.of(hub),
                List.of(),
                List.of(),
                List.of()
        );

        corpAI.onGameStateUpdate(state);
        assertFalse(commandQueue.isEmpty(), "Corporation AI should submit investment command");

        GameState updated = commandQueue.drainAndExecute(state);
        Corporation updatedCorp = updated.corporations().getFirst();

        assertTrue(updatedCorp.liquidCapitalReserves() < 25000.0);
        assertFalse(updatedCorp.ownedFacilityIds().isEmpty());
    }
}
