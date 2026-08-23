package com.spaceconquest.control;

import com.spaceconquest.control.ai.CorporationAIController;
import com.spaceconquest.control.ai.EmpireAIController;
import com.spaceconquest.control.ai.ShadowSyndicateAIController;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.SpaceConquestEngine;
import com.spaceconquest.engine.community.GalacticResolution;
import com.spaceconquest.engine.megastructure.Megastructure;
import com.spaceconquest.engine.scenario.CampaignSetup;
import com.spaceconquest.engine.scenario.VictoryConditionChecker;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.terraforming.GeoengineeringProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class InteractiveGameplayPipelineTest {

    private SpaceConquestEngine engine;
    private CommandQueue commandQueue;
    private HumanController humanController;

    @BeforeEach
    public void setUp() {
        engine = new SpaceConquestEngine();
        commandQueue = new CommandQueue();
        humanController = new HumanController(commandQueue);
    }

    @Test
    public void testFullInteractiveCommandPipelineAndSimulationLoop() {
        String playerEmpire = "terran_confederation";

        // 1. Initial State Checks
        GameState state = engine.getGameState();
        assertNotNull(state);
        assertTrue(state.empires().stream().anyMatch(e -> e.id().equals(playerEmpire)));

        // 2. Step 1: Technology Research, Optimization & Reverse Engineering
        humanController.stageCommand(new StartResearchCommand(
                playerEmpire, "fusion_reactors", false, 10
        ));
        humanController.stageCommand(new SelectOptimizationPathCommand(
                playerEmpire, "fusion_reactor_mk1", SelectOptimizationPathCommand.PATH_A_PERFORMANCE
        ));
        humanController.stageCommand(new ReverseEngineerSalvageCommand(
                playerEmpire, "salvage_hull_01", 0.8, "energy_shielding"
        ));

        // 3. Step 2: Planetary Prospecting, Facility Construction & Expansion
        humanController.stageCommand(new StartProspectingMissionCommand(
                playerEmpire, "mars", 5
        ));
        humanController.stageCommand(new BuildFacilityCommand(
                playerEmpire, "earth", "smelter", "smelter_operator", 1
        ));
        humanController.stageCommand(new ExpandFacilityCommand(
                "foundry_luna", 2, 5000.0
        ));
        humanController.stageCommand(new SetFacilityRecipeCommand(
                "foundry_luna", "recipe_advanced_composite"
        ));

        // 4. Step 3: Starframe Design, Ship Construction & Fleet Maneuvers
        ShipDesign customDesign = new ShipDesign(
                "design_dreadnought_01",
                "Terran Heavy Dreadnought",
                playerEmpire,
                "COMBAT_SHIP",
                "steel",
                List.of("mod_fission_reactor", "mod_ion_drive", "mod_cargo_vault"),
                "inconel_alloy",
                2.5,
                25000.0,
                50000.0,
                250.0,
                0.85,
                500000.0,
                1500000.0,
                true,
                false
        );
        humanController.stageCommand(new DesignShipCommand(customDesign));
        humanController.stageCommand(new QueueShipBuildCommand(
                playerEmpire, "design_dreadnought_01", "sol"
        ));
        humanController.stageCommand(new MoveFleetCommand(
                "terran_confederation_fleet_alpha", "alpha_centauri"
        ));
        humanController.stageCommand(new SetFleetStanceCommand(
                "terran_confederation_fleet_alpha", "AGGRESSIVE"
        ));

        // 5. Senate & Megastructures
        humanController.stageCommand(new ProposeResolutionCommand(
                playerEmpire, "Galactic Anti-Piracy Mandate",
                GalacticResolution.TYPE_ANTI_PIRACY, ""
        ));
        humanController.stageCommand(new BuildMegastructureCommand(
                playerEmpire, Megastructure.TYPE_DYSON_SWARM, "sol", "star_sol", "Sol Dyson Swarm"
        ));
        humanController.stageCommand(new ColonizePlanetCommand(
                playerEmpire, "mars", "colony_ship_01", 1000, "human"
        ));

        // Drain and execute all staged commands
        int executedCount = commandQueue.processCommands(engine);
        assertTrue(executedCount >= 10, "Should execute all staged commands");

        // 6. Verify Mutations in GameState
        GameState mutatedState = engine.getGameState();
        assertTrue(mutatedState.researchProjects().stream().anyMatch(r -> r.targetTechOrAppId().equals("fusion_reactors")));
        assertTrue(mutatedState.shipDesigns().stream().anyMatch(d -> d.id().equals("design_dreadnought_01")));
        assertTrue(mutatedState.industrialFacilities().stream().anyMatch(f -> f.applicationId().equals("smelter")));
        assertEquals(1, engine.getMegastructures().size());

        // 7. Multi-Agent Turn Advancement Loop
        EmpireAIController empireAI = new EmpireAIController("vulkan_forge", commandQueue);
        CorporationAIController corpAI = new CorporationAIController("corp_sol_extraction", commandQueue);
        ShadowSyndicateAIController syndicateAI = new ShadowSyndicateAIController("shadow_syndicate_sol", commandQueue);

        for (int turn = 1; turn <= 5; turn++) {
            empireAI.onGameStateUpdate(engine.getGameState());
            corpAI.onGameStateUpdate(engine.getGameState());
            syndicateAI.onGameStateUpdate(engine.getGameState());

            commandQueue.processCommands(engine);
            engine.stepTurn();
            humanController.onGameStateUpdate(engine.getGameState());
        }

        assertEquals(5, engine.getGameState().turn());

        // 8. Victory Condition Check
        CampaignSetup setup = CampaignSetup.createDefault();
        VictoryConditionChecker checker = engine.getVictoryConditionChecker();
        assertNotNull(checker);
        VictoryConditionChecker.VictoryCheckResult vRes = checker.evaluateVictory(
                engine.getGameState(), setup, engine.getGalacticCommunity(), engine.getMegastructures()
        );
        assertNotNull(vRes);
        assertFalse(vRes.isVictoryAchieved()); // 5 turns is not enough to achieve total domination yet
    }
}
