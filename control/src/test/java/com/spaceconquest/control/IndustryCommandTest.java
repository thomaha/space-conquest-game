package com.spaceconquest.control;

import com.spaceconquest.control.command.BuildFacilityCommand;
import com.spaceconquest.control.command.CommandQueue;
import com.spaceconquest.control.command.ExpandFacilityCommand;
import com.spaceconquest.control.command.StartProspectingMissionCommand;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.GeologicalDeposit;
import com.spaceconquest.engine.industry.IndustrialFacility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class IndustryCommandTest {

    private CommandQueue commandQueue;
    private GameState initialState;

    @BeforeEach
    public void setUp() {
        commandQueue = new CommandQueue();
        GeologicalDeposit hiddenVein = new GeologicalDeposit(
                "dep_hidden_1", "earth", "titanium_ore", 200_000.0, 200_000.0, 1.2, false, ""
        );

        initialState = GameState.builder()
                .turn(1)
                .status("RUNNING")
                .geologicalDeposits(List.of(hiddenVein))
                .build();
    }

    @Test
    public void testBuildFacilityCommand() {
        BuildFacilityCommand cmd = new BuildFacilityCommand(
                "earth", "refining_blast_furnace", "emp_terran",
                IndustrialFacility.PUBLIC_STATE, 25, "industrial_worker"
        );
        assertTrue(cmd.validate(initialState));

        GameState stateWithFacility = cmd.apply(initialState);
        assertEquals(1, stateWithFacility.industrialFacilities().size());
        IndustrialFacility fac = stateWithFacility.industrialFacilities().getFirst();
        assertEquals("earth", fac.planetId());
        assertEquals("refining_blast_furnace", fac.applicationId());
        assertEquals(1, fac.tier());
        assertEquals(25, fac.allocatedWorkers());
        assertFalse(fac.isUndergoingExpansion());
    }

    @Test
    public void testExpandFacilityCommand() {
        BuildFacilityCommand buildCmd = new BuildFacilityCommand(
                "earth", "refining_blast_furnace", "emp_terran",
                IndustrialFacility.PUBLIC_STATE, 25, "industrial_worker"
        );
        GameState stateWithFacility = buildCmd.apply(initialState);
        String facId = stateWithFacility.industrialFacilities().getFirst().id();

        ExpandFacilityCommand expandCmd = new ExpandFacilityCommand(facId, 2, 200.0, 4000.0);
        assertTrue(expandCmd.validate(stateWithFacility));

        GameState expandingState = expandCmd.apply(stateWithFacility);
        IndustrialFacility fac = expandingState.industrialFacilities().getFirst();
        assertTrue(fac.isUndergoingExpansion(), "Facility must be flagged as undergoing expansion");
        assertEquals(0.50, fac.getEffectiveThroughputMultiplier(), 0.001);
        assertEquals(1, expandingState.expansionProjects().size());
    }

    @Test
    public void testStartProspectingMissionCommand() {
        StartProspectingMissionCommand cmd = new StartProspectingMissionCommand("earth", "emp_terran");
        assertTrue(cmd.validate(initialState));

        GameState postSurveyState = cmd.apply(initialState);
        assertFalse(postSurveyState.geologicalDeposits().isEmpty());
    }
}
