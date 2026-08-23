package com.spaceconquest.control;

import com.spaceconquest.control.ai.EmpireAIController;
import com.spaceconquest.control.command.CommandQueue;
import com.spaceconquest.control.command.ReverseEngineerSalvageCommand;
import com.spaceconquest.control.command.SelectOptimizationPathCommand;
import com.spaceconquest.control.command.StartResearchCommand;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.technology.ResearchProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ResearchCommandTest {

    private CommandQueue commandQueue;
    private GameState initialState;

    @BeforeEach
    public void setUp() {
        commandQueue = new CommandQueue();
        Empire empire = new Empire(
                "emp_terran", "Terran Federation", "terran", "Individualist",
                50000.0, 0.05, List.of("sol"),
                List.of(), Map.of(), List.of("electricity"), List.of()
        );

        initialState = new GameState(
                1, "RUNNING", List.of(), List.of(empire), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }

    @Test
    public void testStartResearchCommand() {
        StartResearchCommand command = new StartResearchCommand("emp_terran", "nuclear_fission", false, 8);
        assertTrue(command.validate(initialState));

        GameState updated = command.apply(initialState);
        assertFalse(updated.researchProjects().isEmpty());
        ResearchProject proj = updated.researchProjects().getFirst();
        assertEquals("emp_terran", proj.empireId());
        assertEquals("nuclear_fission", proj.targetTechOrAppId());
        assertEquals(8, proj.assignedScientists());
        assertEquals(0.0, proj.accumulatedPoints());
    }

    @Test
    public void testSelectOptimizationPathCommand() {
        SelectOptimizationPathCommand command = new SelectOptimizationPathCommand("emp_terran", "fission_engine", "PATH_A");
        assertTrue(command.validate(initialState));

        SelectOptimizationPathCommand invalid = new SelectOptimizationPathCommand("emp_terran", "fission_engine", "INVALID");
        assertFalse(invalid.validate(initialState));
    }

    @Test
    public void testReverseEngineerSalvageCommand() {
        StartResearchCommand startCmd = new StartResearchCommand("emp_terran", "fusion_power", false, 5);
        GameState withProject = startCmd.apply(initialState);

        ReverseEngineerSalvageCommand salvageCmd = new ReverseEngineerSalvageCommand("emp_terran", "fusion_power", "terran", true, 2);
        assertTrue(salvageCmd.validate(withProject));

        GameState postSalvage = salvageCmd.apply(withProject);
        ResearchProject proj = postSalvage.researchProjects().getFirst();
        assertTrue(proj.accumulatedPoints() > 0.0, "Progress points should have been injected from salvage");
    }

    @Test
    public void testEmpireAIControllerAutonomousResearchSelection() {
        EmpireAIController controller = new EmpireAIController("emp_terran", commandQueue);
        controller.onGameStateUpdate(initialState);

        assertFalse(commandQueue.isEmpty(), "Empire AI should have submitted research and appointment commands");
        GameState nextState = commandQueue.drainAndExecute(initialState);
        assertFalse(nextState.researchProjects().isEmpty(), "Research project should be active");
    }
}
