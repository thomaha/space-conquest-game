package com.spaceconquest.control;

import com.spaceconquest.control.ai.CorporationAIController;
import com.spaceconquest.control.command.CommandQueue;
import com.spaceconquest.control.command.DesignShipCommand;
import com.spaceconquest.control.command.MoveFleetCommand;
import com.spaceconquest.control.command.QueueShipBuildCommand;
import com.spaceconquest.control.command.SetFleetStanceCommand;
import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.MarketOrder;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ShipCommandTest {

    private CommandQueue commandQueue;
    private GameState initialState;
    private ShipDesign cargoDesign;

    @BeforeEach
    public void setUp() {
        commandQueue = new CommandQueue();
        cargoDesign = new ShipDesign(
                "design_atlas_hauler", "Atlas Hauler", "emp_terran",
                ShipRole.CARGO_TRANSPORT, "steel", List.of(), "steel", 1.0,
                10000.0, 20000.0, 100.0, 1.5, 100000.0, 500000.0, true, false
        );

        initialState = GameState.builder()
                .turn(1)
                .status("RUNNING")
                .shipDesigns(List.of(cargoDesign))
                .build();
    }

    @Test
    public void testDesignShipCommand() {
        ShipDesign explorer = new ShipDesign(
                "design_scout_1", "Starlight Scout", "emp_terran",
                ShipRole.EXPLORER, "carbon_nanotubes", List.of(), "steel", 1.0,
                5000.0, 500.0, 50.0, 2.0, 50000.0, 250000.0, true, false
        );

        DesignShipCommand cmd = new DesignShipCommand(explorer);
        assertTrue(cmd.validate(initialState));

        GameState updated = cmd.apply(initialState);
        assertEquals(2, updated.shipDesigns().size());
        assertTrue(updated.shipDesigns().stream().anyMatch(d -> d.id().equals("design_scout_1")));
    }

    @Test
    public void testQueueShipBuildCommand() {
        QueueShipBuildCommand buildCmd = new QueueShipBuildCommand("emp_terran", "design_atlas_hauler", "sol");
        assertTrue(buildCmd.validate(initialState));

        GameState stateWithShip = buildCmd.apply(initialState);
        assertFalse(stateWithShip.fleets().isEmpty());
        Fleet fleet = stateWithShip.fleets().getFirst();
        assertEquals("emp_terran", fleet.ownerEntityId());
        assertEquals("sol", fleet.currentSystemId());
        assertEquals(1, fleet.ships().size());
        assertEquals("design_atlas_hauler", fleet.ships().getFirst().designId());
    }

    @Test
    public void testMoveFleetAndSetStanceCommands() {
        QueueShipBuildCommand buildCmd = new QueueShipBuildCommand("emp_terran", "design_atlas_hauler", "sol");
        GameState stateWithShip = buildCmd.apply(initialState);
        String fleetId = stateWithShip.fleets().getFirst().id();

        MoveFleetCommand moveCmd = new MoveFleetCommand(fleetId, "alpha_centauri", 50.0, 50.0);
        assertTrue(moveCmd.validate(stateWithShip));
        GameState stateInTransit = moveCmd.apply(stateWithShip);

        Fleet transitFleet = stateInTransit.fleets().getFirst();
        assertTrue(transitFleet.isInWarp());
        assertEquals("alpha_centauri", transitFleet.targetSystemId());

        SetFleetStanceCommand stanceCmd = new SetFleetStanceCommand(fleetId, "PATROL");
        assertTrue(stanceCmd.validate(stateInTransit));
        GameState statePatrol = stanceCmd.apply(stateInTransit);

        assertEquals("PATROL", statePatrol.fleets().getFirst().fleetStance());
    }

    @Test
    public void testCorporationAIAutonomousProprietaryDesignOnShortcoming() {
        Corporation corp = new Corporation(
                "corp_atlas", "Atlas Logistics", "emp_terran", "sol", "transportation",
                50000.0, List.of(), List.of(), List.of()
        );

        MarketOrder severeShortageOrder = new MarketOrder("refined_silicon", 10.0, 500.0, 50.0, 0.80);
        CommercialHub hub = new CommercialHub(
                "hub_sol", "earth", 0.05, 500000.0, 50000.0, 15.0,
                Map.of("refined_silicon", severeShortageOrder)
        );

        // State with NO cargo blueprints
        GameState stateWithoutCargo = GameState.builder()
                .turn(1)
                .status("RUNNING")
                .corporations(List.of(corp))
                .commercialHubs(List.of(hub))
                .build();

        CorporationAIController corpAI = new CorporationAIController("corp_atlas", commandQueue);
        corpAI.onGameStateUpdate(stateWithoutCargo);

        assertFalse(commandQueue.isEmpty(), "Corporation AI should submit proprietary design and build commands");
        GameState postExecutionState = commandQueue.drainAndExecute(stateWithoutCargo);
        assertFalse(postExecutionState.shipDesigns().isEmpty(), "Proprietary design should be registered");
        ShipDesign proprietaryDesign = postExecutionState.shipDesigns().getFirst();
        assertTrue(proprietaryDesign.isProprietaryCorporateDesign(), "Blueprint must be tagged proprietary");
        assertEquals("corp_atlas", proprietaryDesign.ownerEntityId());
    }
}
