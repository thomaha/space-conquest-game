package com.spaceconquest.control;

import com.spaceconquest.control.ai.CorporationAIController;
import com.spaceconquest.control.ai.EmpireAIController;
import com.spaceconquest.control.ai.ShadowSyndicateAIController;
import com.spaceconquest.control.command.CommandQueue;
import com.spaceconquest.control.command.SetTariffRateCommand;
import com.spaceconquest.engine.GameStartScenario;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.SpaceConquestEngine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MultiAgentSimulationTest {

    @Test
    public void testEndToEndMultiAgentTurnSimulation() {
        SpaceConquestEngine engine = new SpaceConquestEngine(GameStartScenario.BASIC_WARP);
        engine.start();

        CommandQueue commandQueue = new CommandQueue();
        HumanController human = new HumanController(commandQueue);

        GameState state = engine.getGameState();
        assertFalse(state.empires().isEmpty(), "Empires must exist");
        assertFalse(state.corporations().isEmpty(), "Corporations must exist");

        String playerEmpireId = state.empires().getFirst().id();
        EmpireAIController empireAI = new EmpireAIController(playerEmpireId, commandQueue);
        CorporationAIController corpAI = new CorporationAIController(state.corporations().getFirst().id(), commandQueue);
        ShadowSyndicateAIController syndicateAI = new ShadowSyndicateAIController("syndicate_1", commandQueue);

        List<Controller> agents = List.of(human, empireAI, corpAI, syndicateAI);

        // Run multi-turn loop
        for (int turn = 0; turn < 10; turn++) {
            GameState currentState = engine.getGameState();

            // Notify all agents
            for (Controller agent : agents) {
                agent.onGameStateUpdate(currentState);
            }

            // Human player dispatches a tariff adjustment command on turn 2
            if (turn == 2 && !currentState.commercialHubs().isEmpty()) {
                String hubId = currentState.commercialHubs().getFirst().id();
                human.dispatchCommand(new SetTariffRateCommand(playerEmpireId, hubId, 0.08));
            }

            // Drain and apply all staged agent/human commands
            GameState postCommandState = commandQueue.drainAndExecute(currentState);
            engine.reset(postCommandState);

            // Engine simulates next turn tick
            engine.update();
        }

        GameState finalState = engine.getGameState();
        assertEquals(10, finalState.turn());
        assertNotNull(human.getLastObservedState());
        assertFalse(finalState.empires().isEmpty());
    }
}
