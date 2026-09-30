package com.spaceconquest.control;

import com.spaceconquest.control.ai.EmpireAIController;
import com.spaceconquest.control.command.BuildMegastructureCommand;
import com.spaceconquest.control.command.CommandQueue;
import com.spaceconquest.control.command.ProposeResolutionCommand;
import com.spaceconquest.control.command.StartTerraformingProjectCommand;
import com.spaceconquest.control.command.VoteResolutionCommand;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.MarketOrder;
import com.spaceconquest.engine.SpaceConquestEngine;
import com.spaceconquest.engine.community.GalacticResolution;
import com.spaceconquest.engine.megastructure.Megastructure;
import com.spaceconquest.engine.terraforming.GeoengineeringProject;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetLocation;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

public class NewPhasesIntegrationSimulationTest {

    private SpaceConquestEngine engine;
    private CommandQueue commandQueue;

    @BeforeEach
    public void setup() {
        engine = SpaceConquestEngine.fromSolScenario();
        commandQueue = new CommandQueue();
    }

    @Test
    public void testTerraformingAndMegastructureCommandsAndTurnProgression() {
        // 1. Give terran confederation sufficient credits and required research for megastructure and terraforming
        List<Empire> updatedEmpires = engine.getGameState().empires().stream().map(e -> {
            if ("terran_confederation".equalsIgnoreCase(e.id())) {
                List<String> techs = new java.util.ArrayList<>(e.unlockedTechIds());
                if (!techs.contains("stellar_megastructures")) techs.add("stellar_megastructures");
                return new Empire(
                        e.id(), e.name(), e.raceId(), e.societyStructure(),
                        5_000_000.0, e.corporateTaxRate(), e.controlledSystemIds(),
                        e.ministries(), e.systemGovernorAssignments(),
                        techs, e.activeShipDesignIds()
                );
            }
            return e;
        }).toList();

        List<CommercialHub> stocked = new java.util.ArrayList<>(engine.getGameState().commercialHubs().stream().map(hub -> {
            if (!"earth".equals(hub.entityId())) return hub;
            Map<String, MarketOrder> orders = new HashMap<>(hub.activeOrders());
            for (String material : List.of("refined_iron", "refined_aluminum",
                    "refined_copper", "silicon")) {
                orders.put(material, new MarketOrder(material, 1_000_000.0, 0, 1.0, 0.0));
            }
            return new CommercialHub(hub.id(), hub.entityId(), hub.transactionTariffRate(),
                    5_000_000.0, 4_000_000.0, hub.logisticsRangeUnits(), Map.copyOf(orders));
        }).toList());
        stocked.add(new CommercialHub("hub_mars", "mars", 0.0, 100_000.0,
                10_000.0, 10.0, Map.of("refined_iron", new MarketOrder("refined_iron",
                10_000.0, 0.0, 1.0, 0.0), "refined_copper",
                new MarketOrder("refined_copper", 5_000.0, 0.0, 1.0, 0.0),
                "silicon", new MarketOrder("silicon", 1_000.0, 0.0, 1.0, 0.0))));
        ShipDesign transport = new ShipDesign("test_mega_transport", "Mega transport",
                "terran_confederation", ShipRole.CARGO_TRANSPORT, "steel", List.of(),
                "steel", 0.0, 10_000.0, 10_000_000.0, 0.0, 1.0, 0.0, 0.0,
                true, false);
        Fleet supply = new Fleet("mega_supply", "Mega supply", "terran_confederation",
                "sol", "", 0.0, 0.0, 0.0, false, "PASSIVE", List.of(
                new ShipInstance("mega_transport", transport.id(), "terran_confederation",
                        1_000.0, 100.0, 100.0, Map.of("refined_iron", 2_000_000.0,
                        "refined_aluminum", 1_000_000.0, "refined_copper", 500_000.0,
                        "silicon", 50_000.0))),
                FleetLocation.at(FleetLocation.Site.deepSpace("sol_star")));
        List<ShipDesign> designs = new java.util.ArrayList<>(engine.getGameState().shipDesigns());
        designs.add(transport);
        List<Fleet> fleets = new java.util.ArrayList<>(engine.getGameState().fleets());
        fleets.add(supply);
        engine.applyGameState(engine.getGameState().toBuilder().empires(updatedEmpires)
                .commercialHubs(stocked).shipDesigns(designs).fleets(fleets).build());

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
