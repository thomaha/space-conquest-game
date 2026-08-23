package com.spaceconquest.control;

import com.spaceconquest.control.ai.CorporationAIController;
import com.spaceconquest.control.ai.EmpireAIController;
import com.spaceconquest.control.command.BombardPlanetCommand;
import com.spaceconquest.control.command.BuildFacilityCommand;
import com.spaceconquest.control.command.CommandQueue;
import com.spaceconquest.control.command.DesignShipCommand;
import com.spaceconquest.control.command.ExpandFacilityCommand;
import com.spaceconquest.control.command.LaunchMassDriverPayloadCommand;
import com.spaceconquest.control.command.LoadPassengersCommand;
import com.spaceconquest.control.command.MoveFleetCommand;
import com.spaceconquest.control.command.ProposeDiplomaticPactCommand;
import com.spaceconquest.control.command.QueueShipBuildCommand;
import com.spaceconquest.control.command.ScanAnomalyCommand;
import com.spaceconquest.control.command.SetPassengerTransitModeCommand;
import com.spaceconquest.control.command.StartProspectingMissionCommand;
import com.spaceconquest.control.command.StartResearchCommand;
import com.spaceconquest.engine.GameStartScenario;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.SpaceConquestEngine;
import com.spaceconquest.engine.galaxy.Anomaly;
import com.spaceconquest.engine.governance.DiplomaticPact;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FuturePhasesMultiAgentTest {

    @Test
    public void testEndToEndFuturePhasesMultiAgentSimulation() {
        SpaceConquestEngine engine = new SpaceConquestEngine(GameStartScenario.BASIC_WARP);
        engine.start();

        CommandQueue commandQueue = new CommandQueue();
        HumanController human = new HumanController(commandQueue);

        GameState state = engine.getGameState();
        assertFalse(state.empires().isEmpty(), "Empires must exist");
        assertFalse(state.corporations().isEmpty(), "Corporations must exist");
        assertFalse(state.solarSystems().isEmpty(), "Solar systems must exist");

        String playerEmpireId = state.empires().getFirst().id();
        String homeSystemId = state.solarSystems().getFirst().id();
        Planet homePlanet = state.solarSystems().getFirst().planets().getFirst();

        EmpireAIController empireAI = new EmpireAIController(playerEmpireId, commandQueue);
        CorporationAIController corpAI = new CorporationAIController(state.corporations().getFirst().id(), commandQueue);

        List<Controller> controllers = List.of(human, empireAI, corpAI);

        // Turn 0: Human issues foundational research and modular ship design
        human.dispatchCommand(new StartResearchCommand(playerEmpireId, "nuclear_fission", false, 10));

        ShipDesign frigateDesign = new ShipDesign(
                "design_terran_frigate", "Terran Patrol Frigate", playerEmpireId,
                ShipRole.COMBAT_SHIP, "steel", List.of(), "steel", 1.5,
                12000.0, 1000.0, 150.0, 1.4, 80000.0, 350000.0, true, false
        );
        human.dispatchCommand(new DesignShipCommand(frigateDesign));

        // Human orders facility build and prospecting survey
        human.dispatchCommand(new BuildFacilityCommand(
                homePlanet.id(), "refining_blast_furnace", playerEmpireId,
                IndustrialFacility.PUBLIC_STATE, 20, "industrial_worker"
        ));
        human.dispatchCommand(new StartProspectingMissionCommand(homePlanet.id(), playerEmpireId));

        // Simulate 5 turns
        for (int turn = 0; turn < 5; turn++) {
            GameState currentState = engine.getGameState();

            for (Controller c : controllers) {
                c.onGameStateUpdate(currentState);
            }

            if (turn == 1) {
                // Commission a frigate into a fleet at home system
                human.dispatchCommand(new QueueShipBuildCommand(playerEmpireId, "design_terran_frigate", homeSystemId));

                // Execute mass driver payload launch
                human.dispatchCommand(new LaunchMassDriverPayloadCommand("mass_driver_1", playerEmpireId, "iron_ore", 500.0, 300.0));
            }

            if (turn == 2) {
                // Find commissioned fleet and order FTL warp movement if secondary system exists
                if (!currentState.fleets().isEmpty() && currentState.solarSystems().size() > 1) {
                    String fleetId = currentState.fleets().getFirst().id();
                    String destinationSys = currentState.solarSystems().get(1).id();
                    human.dispatchCommand(new MoveFleetCommand(fleetId, destinationSys, 0, 0));

                    // Load troops in cryogenic stasis
                    human.dispatchCommand(new LoadPassengersCommand(fleetId, currentState.fleets().getFirst().ships().getFirst().id(), "human", 100, ShipInstance.MODE_CRYOGENIC_STASIS));
                }

                // Expand facility to Tier 2
                if (!currentState.industrialFacilities().isEmpty()) {
                    String facId = currentState.industrialFacilities().getFirst().id();
                    human.dispatchCommand(new ExpandFacilityCommand(facId, 2, 100.0, 2000.0));
                }

                // Propose bilateral trade pact if another empire exists
                if (currentState.empires().size() > 1) {
                    String foreignEmp = currentState.empires().get(1).id();
                    human.dispatchCommand(new ProposeDiplomaticPactCommand(playerEmpireId, foreignEmp, DiplomaticPact.MUTUAL_TRADE_AGREEMENT));
                }
            }

            if (turn == 3) {
                // Scan deep-space anomaly
                if (!currentState.fleets().isEmpty()) {
                    String fleetId = currentState.fleets().getFirst().id();
                    Anomaly anomaly = new Anomaly("anom_1", homeSystemId, Anomaly.TYPE_DERELICT_STARSHIP, "Derelict Cruiser", "Hull", 20.0, false, Anomaly.REWARD_CREDITS, 5000.0, "");
                    human.dispatchCommand(new ScanAnomalyCommand(playerEmpireId, fleetId, anomaly));
                }
            }

            if (turn == 4) {
                // Issue kinetic dart bombardment
                human.dispatchCommand(new BombardPlanetCommand(playerEmpireId, homeSystemId, homePlanet.id(), "KINETIC_DARTS"));
            }

            // Drain and apply queued commands
            GameState postCmdState = commandQueue.drainAndExecute(currentState);
            engine.reset(postCmdState);

            // Engine simulates turn tick
            engine.update();
        }

        GameState finalState = engine.getGameState();
        assertEquals(5, finalState.turn());
        assertFalse(finalState.shipDesigns().isEmpty(), "Ship blueprints must be preserved in state");
        assertFalse(finalState.industrialFacilities().isEmpty(), "Industrial facilities must exist");
        assertFalse(finalState.geologicalDeposits().isEmpty(), "Geological deposits must be tracked");
    }
}
