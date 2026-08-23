package com.spaceconquest.engine.espionage;

import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.Empire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class EspionageProcessorTest {

    private EspionageProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new EspionageProcessor(new Random(42));
    }

    @Test
    void testEspionageMissionProgressionAndCompletion() {
        SleeperAgent agent = new SleeperAgent(
                "agent_1", "terran_confederation", "earth", "bureaucrat", 5, false
        );
        EspionageOperation op = new EspionageOperation(
                "op_sabotage_1", EspionageOperation.OP_POWER_GRID_SABOTAGE,
                "terran_confederation", "centauri_dominion", "grid_sol", "agent_1",
                0.90, 80.0, false, false
        );

        Empire empire = new Empire(
                "terran_confederation", "Terran Confederation", "human", "Individualist",
                50000.0, 0.10, List.of("sol"), List.of(), Map.of(), List.of(), List.of()
        );

        EspionageProcessor.EspionageTurnResult result = processor.processEspionageTurn(
                List.of(op), List.of(agent), List.of(), List.of(empire)
        );

        assertNotNull(result);
        assertEquals(1, result.updatedOperations().size());
        assertTrue(result.updatedOperations().get(0).isCompleted());
    }

    @Test
    void testSupplyLineExtortionRoutesCreditsToPirateBase() {
        PirateBase base = new PirateBase(
                "base_ceres_1", "syndicate_shadow", "sol", "ceres", 5000.0, 4, true
        );
        Corporation corp = new Corporation(
                "corp_transport", "Interplanetary Freight", "terran_confederation",
                "earth", "TRANSPORT", 40000.0, List.of(), List.of(), List.of()
        );

        EspionageProcessor.ExtortionResult result = processor.processSupplyLineExtortion(base, corp, 5000.0);

        assertTrue(result.isSuccessful());
        assertEquals(5000.0, result.creditsExtorted(), 0.001);
        assertEquals("corp_transport", result.targetCorpId());
        assertEquals("base_ceres_1", result.targetBaseId());
    }
}
