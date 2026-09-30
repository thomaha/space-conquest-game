package com.spaceconquest.control;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.GameClock;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.MarketOrder;
import com.spaceconquest.engine.SpaceConquestEngine;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.StationModule;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetLocation;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MultiPhaseIntegrationSimulationTest {

    private SpaceConquestEngine engine;

    @BeforeEach
    void setUp() {
        engine = SpaceConquestEngine.fromSolScenario();
    }

    @Test
    void testFullMultiPhaseMultiTurnSimulationLoop() {
        GameState opening = engine.getGameState();
        List<CommercialHub> stocked = opening.commercialHubs().stream().map(hub -> {
            if (!"earth".equals(hub.entityId())) return hub;
            Map<String, MarketOrder> orders = new HashMap<>(hub.activeOrders());
            for (String material : List.of("refined_iron", "refined_aluminum",
                    "refined_copper", "steel", "silicon")) {
                orders.put(material, new MarketOrder(material, 500_000.0, 0, 1.0, 0));
            }
            return new CommercialHub(hub.id(), hub.entityId(), hub.transactionTariffRate(),
                    5_000_000.0, 2_500_000.0, hub.logisticsRangeUnits(), Map.copyOf(orders));
        }).toList();
        List<Empire> funded = opening.empires().stream().map(empire -> new Empire(empire.id(),
                empire.name(), empire.raceId(), empire.societyStructure(), 10_000_000.0,
                empire.corporateTaxRate(), empire.controlledSystemIds(), empire.ministries(),
                empire.systemGovernorAssignments(), empire.unlockedTechIds(),
                empire.activeShipDesignIds())).toList();
        List<ShipDesign> designs = new java.util.ArrayList<>(opening.shipDesigns());
        designs.add(new ShipDesign("test_orbital_transport", "Test orbital transport",
                "terran_confederation", ShipRole.CARGO_TRANSPORT, "steel", List.of(),
                "steel", 0.0, 10_000.0, 250_000.0, 0.0, 1.0, 0.0, 0.0,
                true, false));
        List<Fleet> fleets = new java.util.ArrayList<>(opening.fleets());
        fleets.add(new Fleet("test_cargo_fleet", "Construction supply", "terran_confederation",
                "sol", "", 0.0, 0.0, 0.0, false, "PASSIVE", List.of(
                new ShipInstance("test_cargo_ship", "test_orbital_transport",
                        "terran_confederation", 1_000.0, 100.0, 100.0,
                        Map.of("refined_iron", 100_000.0, "refined_aluminum", 30_000.0,
                                "refined_copper", 30_000.0, "steel", 30_000.0,
                                "silicon", 10_000.0))),
                FleetLocation.at(FleetLocation.Site.orbit("earth"))));
        GameState initialState = opening.toBuilder().commercialHubs(stocked).empires(funded)
                .shipDesigns(designs).fleets(fleets).build();
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
        assertEquals(2, s3.constructionProjects().size());

        // A five-day station finishes before the ten-day elevator.
        for (int i = 0; i < 5; i++) engine.stepTurn();
        GameState built = engine.getGameState();
        assertEquals(1, built.orbitalStations().size());
        assertTrue(built.spaceElevators().isEmpty());
        String stationId = built.orbitalStations().getFirst().id();
        built = built.withFleets(List.of(built.fleets().getFirst().withLocation(
                FleetLocation.at(FleetLocation.Site.docked(stationId)))));
        AddStationModuleCommand hangarCmd = new AddStationModuleCommand(
                stationId, "Civilian Trading Berths", StationModule.TYPE_CIVILIAN_HANGAR,
                12, 28000.0, 30.0, 0.0, "technician", 5
        );
        AddStationModuleCommand commCmd = new AddStationModuleCommand(
                stationId, "B2B Commerce Terminal", StationModule.TYPE_COMMERCE,
                8, 14000.0, 20.0, 0.0, "bureaucrat", 3
        );

        GameState s4 = hangarCmd.apply(built);
        GameState s5 = commCmd.apply(s4);
        engine.applyGameState(s5);

        // Advance the remaining five turns in the engine
        for (int i = 0; i < 5; i++) {
            engine.stepTurn();
        }

        GameState advancedState = engine.getGameState();
        assertEquals(10, advancedState.turn());
        assertEquals(1, advancedState.orbitalStations().size());
        assertEquals(1, advancedState.spaceElevators().size());
        assertEquals(1, advancedState.sleeperAgents().size());
        assertTrue(advancedState.orbitalStations().get(0).isOperational());
        assertTrue(advancedState.commercialHubs().stream()
                .anyMatch(hub -> stationId.equals(hub.entityId())));

        // Verify clock
        GameClock clock = engine.getGameClock();
        assertEquals(10, clock.getCurrentTurn());
        assertNotNull(clock.getFormattedGameTime());
    }
}
