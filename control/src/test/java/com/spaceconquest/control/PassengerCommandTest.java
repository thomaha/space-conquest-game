package com.spaceconquest.control;

import com.spaceconquest.control.command.CommandQueue;
import com.spaceconquest.control.command.LoadPassengersCommand;
import com.spaceconquest.control.command.SetPassengerTransitModeCommand;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.ShipInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class PassengerCommandTest {

    private CommandQueue commandQueue;
    private GameState initialState;

    @BeforeEach
    void setUp() {
        commandQueue = new CommandQueue();
        ShipInstance ship = new ShipInstance(
                "transport_alpha", "design_troop", "terran_confederation",
                500.0, 200.0, 100.0, Map.of()
        );
        Fleet fleet = new Fleet(
                "fleet_sol_transport", "Sol 1st Transport Wing", "terran_confederation",
                "sol", "", 0.0, 0.0, 0.0, false, "PASSIVE", List.of(ship)
        );

        initialState = new GameState(
                1, "RUNNING", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(fleet),
                List.of(), List.of(), List.of(), List.of()
        );
    }

    @Test
    void testLoadPassengersCommand() {
        LoadPassengersCommand cmd = new LoadPassengersCommand(
                "fleet_sol_transport", "transport_alpha", "human", 250, ShipInstance.MODE_CRYOGENIC_STASIS
        );

        assertTrue(cmd.validate(initialState));
        GameState nextState = cmd.apply(initialState);

        ShipInstance updatedShip = nextState.fleets().get(0).ships().get(0);
        assertEquals(250, updatedShip.passengerCount());
        assertEquals("human", updatedShip.passengerRaceId());
        assertEquals(ShipInstance.MODE_CRYOGENIC_STASIS, updatedShip.transitMode());
    }

    @Test
    void testSetPassengerTransitModeCommand() {
        // First load passengers
        LoadPassengersCommand loadCmd = new LoadPassengersCommand(
                "fleet_sol_transport", "transport_alpha", "human", 100, ShipInstance.MODE_CRYOGENIC_STASIS
        );
        GameState loadedState = loadCmd.apply(initialState);

        // Switch to conscious
        SetPassengerTransitModeCommand modeCmd = new SetPassengerTransitModeCommand(
                "fleet_sol_transport", "transport_alpha", ShipInstance.MODE_CONSCIOUS
        );
        assertTrue(modeCmd.validate(loadedState));
        GameState updatedState = modeCmd.apply(loadedState);

        ShipInstance updatedShip = updatedState.fleets().get(0).ships().get(0);
        assertEquals(100, updatedShip.passengerCount());
        assertEquals(ShipInstance.MODE_CONSCIOUS, updatedShip.transitMode());
    }
}
