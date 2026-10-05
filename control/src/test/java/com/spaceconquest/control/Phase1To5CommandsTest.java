package com.spaceconquest.control;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.MarketOrder;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.macrostructure.MacroStructureProcessor;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.StationModule;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetLocation;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Phase1To5CommandsTest {

    private GameState initialState;

    @BeforeEach
    void setUp() {
        initialState = GameState.builder()
                .turn(1)
                .status("RUNNING")
                .solarSystems(List.of(
                new SolarSystem("sol", "Sol System", "", 0, 0, 0, 1.98847e30, 1.3927e6, "Yellow",
                        List.of(new Planet("earth", "Earth", "", 5.972e24, 9.81, 149597870, 0, 12742,
                                "terrestrial", "breathable", true, 1, List.of(), List.of(), List.of()),
                                new Planet("mars", "Mars", "", 6.417e23, 3.72, 227939200, 1.85, 6779,
                                        "terrestrial", "none", false, 1, List.of(), List.of(), List.of())), List.of())
        ))
                .empires(List.of(new Empire("terran_confederation", "Terran", "human",
                        "Individualist", 1_000_000.0, 0.15, List.of("sol"), List.of(),
                        Map.of(), List.of("industrial_production", "rocketry", "space_stations"), List.of())))
                .commercialHubs(List.of(new CommercialHub("hub_earth", "earth", 0.0,
                        1_000_000.0, 500_000.0, 10.0,
                        Map.of("refined_iron", new MarketOrder("refined_iron", 200_000.0, 0, 1, 0),
                                "refined_aluminum", new MarketOrder("refined_aluminum", 100_000.0, 0, 1, 0),
                                "refined_copper", new MarketOrder("refined_copper", 100_000.0, 0, 1, 0),
                                "steel", new MarketOrder("steel", 50_000.0, 0, 1, 0),
                                "silicon", new MarketOrder("silicon", 50_000.0, 0, 1, 0)))))
                .fleets(List.of(new Fleet("fleet_const", "Builders", "terran_confederation",
                        "sol", "", 0, 0, 0, false, "PASSIVE", List.of(
                        new ShipInstance("const_ship_1", "transport_design", "terran_confederation",
                                1_000.0, 100.0, 100.0, Map.of("refined_iron", 100_000.0,
                                "refined_aluminum", 30_000.0, "refined_copper", 30_000.0,
                                "steel", 30_000.0, "silicon", 10_000.0))),
                        FleetLocation.at(FleetLocation.Site.orbit("earth")))))
                .shipDesigns(List.of(new ShipDesign("transport_design", "Orbital cargo",
                        "terran_confederation", ShipRole.CARGO_TRANSPORT, "steel", List.of(),
                        "steel", 0.0, 10_000.0, 250_000.0, 0.0, 1.0, 0.0, 0.0,
                        true, false)))
                .build();
    }

    @Test
    void testBuildOrbitalStationCommand() {
        BuildOrbitalStationCommand cmd = new BuildOrbitalStationCommand(
                "Gateway Station", "sol", "earth", "terran_confederation",
                OrbitalStation.OWNERSHIP_PUBLIC_STATE, 50, "steel", 5.0
        );

        assertTrue(cmd.validate(initialState));
        GameState updated = cmd.apply(initialState);
        assertTrue(updated.orbitalStations().isEmpty());
        assertEquals(1, updated.constructionProjects().size());
        for (int day = 0; day < 5; day++) updated = new MacroStructureProcessor()
                .advanceConstructionProjects(updated);
        assertEquals("Gateway Station", updated.orbitalStations().getFirst().name());
        assertTrue(updated.orbitalStations().getFirst().isOperational());
    }

    @Test
    void orbitalStationFrameRequiresRocketryAndSpaceStationsResearch() {
        Empire owner = initialState.empires().getFirst();
        Empire withoutStationResearch = new Empire(owner.id(), owner.name(), owner.raceId(),
                owner.societyStructure(), owner.treasuryCredits(), owner.corporateTaxRate(),
                owner.controlledSystemIds(), owner.ministries(), owner.systemGovernorAssignments(),
                List.of("industrial_production"), owner.activeShipDesignIds());
        GameState locked = initialState.withEmpires(List.of(withoutStationResearch));
        BuildOrbitalStationCommand command = new BuildOrbitalStationCommand(
                "Gateway Station", "sol", "earth", owner.id(),
                OrbitalStation.OWNERSHIP_PUBLIC_STATE, 50, "steel", 5.0);
        assertFalse(command.validate(locked));
    }

    @Test
    void testBuildSpaceElevatorCommand() {
        BuildSpaceElevatorCommand cmd = new BuildSpaceElevatorCommand("earth", "terran_confederation", 100000.0);
        assertTrue(cmd.validate(initialState));

        GameState updated = cmd.apply(initialState);
        assertTrue(updated.spaceElevators().isEmpty());
        assertEquals(1, updated.constructionProjects().size());
        for (int day = 0; day < 10; day++) updated = new MacroStructureProcessor()
                .advanceConstructionProjects(updated);
        assertEquals("earth", updated.spaceElevators().getFirst().planetId());
        assertEquals(0.95, updated.spaceElevators().getFirst().surfaceToOrbitCostDiscount(), 0.001);

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
        for (int day = 0; day < 5; day++) stateWithStation = new MacroStructureProcessor()
                .advanceConstructionProjects(stateWithStation);
        String stationId = stateWithStation.orbitalStations().getFirst().id();
        stateWithStation = stateWithStation.withFleets(List.of(stateWithStation.fleets()
                .getFirst().withLocation(FleetLocation.at(FleetLocation.Site.docked(stationId)))));

        AddStationModuleCommand modCmd = new AddStationModuleCommand(
                stationId, "Hydroponics Ring", StationModule.TYPE_HYDROPONIC_FOOD,
                8, 11000.0, 20.0, 0.0, "farmer", 4
        );

        assertTrue(modCmd.validate(stateWithStation));
        GameState updated = modCmd.apply(stateWithStation);
        assertEquals(2, updated.orbitalStations().getFirst().modules().size());
        assertEquals(1, updated.constructionProjects().size());
        for (int day = 0; day < 2; day++) updated = new MacroStructureProcessor()
                .advanceConstructionProjects(updated);
        assertEquals(3, updated.orbitalStations().getFirst().modules().size());
    }

    @Test
    void testDeployConstructionShipCommand() {
        DeployConstructionShipCommand cmd = new DeployConstructionShipCommand(
                "const_ship_1", "sol", "mars", "ORBITAL_STATION", 3.0
        );
        GameState atMars = initialState.withFleets(List.of(initialState.fleets().getFirst()
                .withLocation(FleetLocation.at(FleetLocation.Site.orbit("mars")))));
        assertTrue(cmd.validate(atMars));

        GameState updated = cmd.apply(atMars);
        assertEquals(1, updated.constructionProjects().size());
        assertEquals("mars", updated.constructionProjects().getFirst().targetCelestialId());
    }

    @Test
    void testInfiltrateAgentAndCovertOperationCommands() {
        InfiltrateAgentCommand infCmd = new InfiltrateAgentCommand(
                "terran_confederation", "earth", "technician", 4
        );
        assertTrue(infCmd.validate(initialState));
        GameState stateWithAgent = infCmd.apply(initialState);
        assertEquals(1, stateWithAgent.sleeperAgents().size());
        String agentId = stateWithAgent.sleeperAgents().getFirst().id();

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
        String facId = stateWithFac.industrialFacilities().getFirst().id();

        SetFacilityRecipeCommand recipeCmd = new SetFacilityRecipeCommand(facId, "alloy_steel");
        assertTrue(recipeCmd.validate(stateWithFac));

        GameState updated = recipeCmd.apply(stateWithFac);
        assertEquals("alloy_steel", updated.industrialFacilities().getFirst().applicationId());
    }
}
