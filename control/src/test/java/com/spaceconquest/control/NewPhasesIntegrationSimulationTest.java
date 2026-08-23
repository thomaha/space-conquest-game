package com.spaceconquest.control;

import com.spaceconquest.control.ai.EmpireAIController;
import com.spaceconquest.control.command.BuildMegastructureCommand;
import com.spaceconquest.control.command.CommandQueue;
import com.spaceconquest.control.command.ProposeResolutionCommand;
import com.spaceconquest.control.command.StartTerraformingProjectCommand;
import com.spaceconquest.control.command.VoteResolutionCommand;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.SpaceConquestEngine;
import com.spaceconquest.engine.community.GalacticResolution;
import com.spaceconquest.engine.megastructure.Megastructure;
import com.spaceconquest.engine.terraforming.GeoengineeringProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class NewPhasesIntegrationSimulationTest {

    private SpaceConquestEngine engine;
    private CommandQueue commandQueue;

    @BeforeEach
    public void setup() {
        engine = new SpaceConquestEngine();
        commandQueue = new CommandQueue();
    }

    @Test
    public void testTerraformingAndMegastructureCommandsAndTurnProgression() {
        // 1. Give terran confederation sufficient credits for megastructure and terraforming
        List<Empire> updatedEmpires = engine.getGameState().empires().stream().map(e -> {
            if ("terran_confederation".equalsIgnoreCase(e.id())) {
                return new Empire(
                        e.id(), e.name(), e.raceId(), e.societyStructure(),
                        200000.0, e.corporateTaxRate(), e.controlledSystemIds(),
                        e.ministries(), e.systemGovernorAssignments(),
                        e.unlockedTechIds(), e.activeShipDesignIds()
                );
            }
            return e;
        }).toList();

        engine.applyGameState(new com.spaceconquest.engine.GameState(
                engine.getGameState().turn(),
                engine.getGameState().status(),
                engine.getGameState().solarSystems(),
                updatedEmpires,
                engine.getGameState().corporations(),
                engine.getGameState().commercialHubs(),
                engine.getGameState().shadowSyndicates(),
                engine.getGameState().diplomaticRelations(),
                engine.getGameState().systemGovernors()
        ));

        // 2. Submit Terraforming and Megastructure commands
        commandQueue.submit(new StartTerraformingProjectCommand(
                "terran_confederation", "mars", GeoengineeringProject.TYPE_GREENHOUSE_FACTORY,
                1.0, 288.0, Map.of("oxygen_gas", 0.20, "nitrogen_gas", 0.75)
        ));

        commandQueue.submit(new BuildMegastructureCommand(
                "terran_confederation", Megastructure.TYPE_DYSON_SWARM, "sol", "sol_star", "Terran Sol Swarm"
        ));

        // Propose Senate Resolution
        commandQueue.submit(new ProposeResolutionCommand(
                "terran_confederation", "Pan-Galactic Free Trade Accord",
                GalacticResolution.TYPE_FREE_TRADE, ""
        ));

        // Process staged commands
        int applied = commandQueue.processCommands(engine);
        assertEquals(3, applied);

        assertEquals(1, engine.getTerraformingProjects().size());
        assertEquals(1, engine.getMegastructures().size());
        assertNotNull(engine.getGalacticCommunity());
        assertEquals(1, engine.getGalacticCommunity().activeResolutions().size());

        // 3. Simulate multi-turn progression
        EmpireAIController ai = new EmpireAIController("terran_confederation", commandQueue);

        for (int t = 0; t < 10; t++) {
            ai.onGameStateUpdate(engine.getGameState());
            commandQueue.processCommands(engine);
            engine.stepTurn();
        }

        assertTrue(engine.getGameState().turn() >= 10);
        assertNotNull(engine.getMegastructures().get(0));
        assertTrue(engine.getMegastructures().get(0).currentStageProgress() > 0 || engine.getMegastructures().get(0).isOperational());
    }
}
