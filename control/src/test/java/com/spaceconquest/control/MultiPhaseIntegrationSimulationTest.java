package com.spaceconquest.control;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.GameClock;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.SpaceConquestEngine;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.StationModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MultiPhaseIntegrationSimulationTest {

    private SpaceConquestEngine engine;

    @BeforeEach
    void setUp() {
        engine = new SpaceConquestEngine();
    }

    @Test
    void testFullMultiPhaseMultiTurnSimulationLoop() {
        GameState initialState = engine.getGameState();
        assertNotNull(initialState);

        // 1. Dispatch macro-structure deployment commands
        BuildOrbitalStationCommand stationCmd = new BuildOrbitalStationCommand(
                "Sol Gateway Starbase", "sol", "earth", "terran_confederation",
                OrbitalStation.OWNERSHIP_PUBLIC_STATE, 50, "steel", 5.0
        );
        BuildSpaceElevatorCommand elevatorCmd = new BuildSpaceElevatorCommand(
                "earth", "terran_confederation", 100000.0
        );
        InfiltrateAgentCommand agentCmd = new InfiltrateAgentCommand(
                "terran_confederation", "earth", "technician", 3
        );

        GameState s1 = stationCmd.apply(initialState);
        GameState s2 = elevatorCmd.apply(s1);
        GameState s3 = agentCmd.apply(s2);
        engine.applyGameState(s3);

        String stationId = s3.orbitalStations().get(0).id();
        AddStationModuleCommand hangarCmd = new AddStationModuleCommand(
                stationId, "Civilian Trading Berths", StationModule.TYPE_CIVILIAN_HANGAR,
                12, 28000.0, 30.0, 0.0, "technician", 5
        );
        AddStationModuleCommand commCmd = new AddStationModuleCommand(
                stationId, "B2B Commerce Terminal", StationModule.TYPE_COMMERCE,
                8, 14000.0, 20.0, 0.0, "bureaucrat", 3
        );

        GameState s4 = hangarCmd.apply(s3);
        GameState s5 = commCmd.apply(s4);
        engine.applyGameState(s5);

        // Advance 5 turns in the engine
        for (int i = 0; i < 5; i++) {
            engine.stepTurn();
        }

        GameState advancedState = engine.getGameState();
        assertEquals(5, advancedState.turn());
        assertEquals(1, advancedState.orbitalStations().size());
        assertEquals(1, advancedState.spaceElevators().size());
        assertEquals(1, advancedState.sleeperAgents().size());
        assertTrue(advancedState.orbitalStations().get(0).isOperational());

        // Verify clock
        GameClock clock = engine.getGameClock();
        assertEquals(5, clock.getCurrentTurn());
        assertNotNull(clock.getFormattedGameTime());
    }
}
