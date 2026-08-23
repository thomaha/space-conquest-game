package com.spaceconquest.control;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.StationModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Phase1To5CommandsTest {

    private GameState initialState;

    @BeforeEach
    void setUp() {
        initialState = new GameState(1, "RUNNING", List.of(
                new SolarSystem("sol", "Sol System", "", 0, 0, 0, 1.0, 1.0, "Yellow", List.of(), List.of())
        ));
    }

    @Test
    void testBuildOrbitalStationCommand() {
        BuildOrbitalStationCommand cmd = new BuildOrbitalStationCommand(
                "Gateway Station", "sol", "earth", "terran_confederation",
                OrbitalStation.OWNERSHIP_PUBLIC_STATE, 50, "steel", 5.0
        );

        assertTrue(cmd.validate(initialState));
        GameState updated = cmd.apply(initialState);
        assertEquals(1, updated.orbitalStations().size());
        assertEquals("Gateway Station", updated.orbitalStations().get(0).name());
        assertTrue(updated.orbitalStations().get(0).isOperational());
    }

    @Test
    void testBuildSpaceElevatorCommand() {
        BuildSpaceElevatorCommand cmd = new BuildSpaceElevatorCommand("earth", "terran_confederation", 100000.0);
        assertTrue(cmd.validate(initialState));

        GameState updated = cmd.apply(initialState);
        assertEquals(1, updated.spaceElevators().size());
        assertEquals("earth", updated.spaceElevators().get(0).planetId());
        assertEquals(0.95, updated.spaceElevators().get(0).surfaceToOrbitCostDiscount(), 0.001);

        // Duplicate validation fails
        assertFalse(cmd.validate(updated));
    }

    @Test
    void testAddStationModuleCommand() {
        BuildOrbitalStationCommand buildCmd = new BuildOrbitalStationCommand(
                "Gateway Station", "sol", "earth", "terran_confederation",
                OrbitalStation.OWNERSHIP_PUBLIC_STATE, 50, "steel", 5.0
        );
        GameState stateWithStation = buildCmd.apply(initialState);
        String stationId = stateWithStation.orbitalStations().get(0).id();

        AddStationModuleCommand modCmd = new AddStationModuleCommand(
                stationId, "Hydroponics Ring", StationModule.TYPE_HYDROPONIC_FOOD,
                8, 11000.0, 20.0, 0.0, "farmer", 4
        );

        assertTrue(modCmd.validate(stateWithStation));
        GameState updated = modCmd.apply(stateWithStation);
        assertEquals(3, updated.orbitalStations().get(0).modules().size());
    }

    @Test
    void testDeployConstructionShipCommand() {
        DeployConstructionShipCommand cmd = new DeployConstructionShipCommand(
                "const_ship_1", "sol", "mars", "ORBITAL_STATION", 3.0
        );
        assertTrue(cmd.validate(initialState));

        GameState updated = cmd.apply(initialState);
        assertEquals(1, updated.constructionProjects().size());
        assertEquals("mars", updated.constructionProjects().get(0).targetCelestialId());
    }

    @Test
    void testInfiltrateAgentAndCovertOperationCommands() {
        InfiltrateAgentCommand infCmd = new InfiltrateAgentCommand(
                "terran_confederation", "earth", "technician", 4
        );
        assertTrue(infCmd.validate(initialState));
        GameState stateWithAgent = infCmd.apply(initialState);
        assertEquals(1, stateWithAgent.sleeperAgents().size());
        String agentId = stateWithAgent.sleeperAgents().get(0).id();

        LaunchCovertOperationCommand opCmd = new LaunchCovertOperationCommand(
                "POWER_GRID_SABOTAGE", "terran_confederation", "centauri_dominion", "grid_sol", agentId
        );
        assertTrue(opCmd.validate(stateWithAgent));
        GameState stateWithOp = opCmd.apply(stateWithAgent);
        assertEquals(1, stateWithOp.espionageOperations().size());
    }

    @Test
    void testSetFacilityRecipeCommand() {
        BuildFacilityCommand facCmd = new BuildFacilityCommand(
                "earth", "pyro_iron_smelting", "terran_confederation", "PUBLIC_STATE", 50, "industrial_worker"
        );
        GameState stateWithFac = facCmd.apply(initialState);
        String facId = stateWithFac.industrialFacilities().get(0).id();

        SetFacilityRecipeCommand recipeCmd = new SetFacilityRecipeCommand(facId, "alloy_steel");
        assertTrue(recipeCmd.validate(stateWithFac));

        GameState updated = recipeCmd.apply(stateWithFac);
        assertEquals("alloy_steel", updated.industrialFacilities().get(0).applicationId());
    }
}
