package com.spaceconquest.control;

import com.spaceconquest.control.command.CommandQueue;
import com.spaceconquest.control.command.LaunchMassDriverPayloadCommand;
import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class MassDriverCommandTest {

    private CommandQueue commandQueue;
    private GameState initialState;

    @BeforeEach
    void setUp() {
        commandQueue = new CommandQueue();
        Empire empire = new Empire(
                "terran_confederation", "Terran Confederation", "human", "Individualist",
                50000.0, 0.05, List.of("sol"),
                List.of(), Map.of(), List.of(), List.of()
        );
        Corporation corp = new Corporation(
                "corp_mining", "Sol Mining", "terran_confederation", "earth", "EXTRACTION",
                10000.0, List.of(), List.of(), List.of()
        );

        initialState = new GameState(
                1, "RUNNING", List.of(), List.of(empire), List.of(corp), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of()
        );
    }

    @Test
    void testLaunchMassDriverPayloadByCorporation() {
        LaunchMassDriverPayloadCommand cmd = new LaunchMassDriverPayloadCommand(
                "mass_driver_earth_1", "corp_mining", "iron_ore", 1000.0, 300.0
        );

        assertTrue(cmd.validate(initialState));
        GameState nextState = cmd.apply(initialState);

        Corporation updatedCorp = nextState.corporations().get(0);
        // Cost = 1000 tons * 0.5 credits = 500 credits -> 10000 - 500 = 9500
        assertEquals(9500.0, updatedCorp.liquidCapitalReserves(), 0.01);
    }
}
