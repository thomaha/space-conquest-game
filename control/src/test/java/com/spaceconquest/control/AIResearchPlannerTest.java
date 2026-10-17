package com.spaceconquest.control;

import com.spaceconquest.control.ai.EmpireResearchPlanner;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.technology.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AIResearchPlannerTest {
    @Test void completedCatalogStillAllowsPaidApplicationRefinement() throws Exception {
        var state = new ResearchLifecycleTest().world(); var old = state.empires().getFirst();
        var known = new ArrayList<String>();
        for (var technology : DataModelLoader.loadTechnologies()) {
            known.add(technology.id()); technology.applications().forEach(application -> known.add(application.id()));
        }
        var empire = new Empire(old.id(), old.name(), old.raceId(), old.societyStructure(), old.treasuryCredits(), old.corporateTaxRate(),
                old.controlledSystemIds(), old.ministries(), old.systemGovernorAssignments(), known, old.activeShipDesignIds());
        state = state.withEmpires(List.of(empire));
        var start = (StartResearchCommand) EmpireResearchPlanner.plan(state, empire).stream()
                .filter(command -> command instanceof StartResearchCommand).findFirst().orElseThrow();
        assertTrue(start.isApplication()); assertTrue(known.contains(start.targetTechOrAppId())); assertTrue(start.validate(state));
        assertEquals(ResearchPolicy.current().cost(empire, start.targetTechOrAppId(), true, DataModelLoader.loadTechnologies()),
                start.apply(state).researchProjects().getFirst().requiredPoints());
    }
    @Test void availableApplicationsAreDevelopedWithTheActualSmallWorkforce() {
        var fixture = new ResearchLifecycleTest(); var state = fixture.scientists(fixture.world(), 2);
        var plan = EmpireResearchPlanner.plan(state, state.empires().getFirst());
        var start = (StartResearchCommand) plan.stream().filter(command -> command instanceof StartResearchCommand).findFirst().orElseThrow();
        assertTrue(start.isApplication()); assertEquals(2, start.assignedScientists()); assertTrue(start.validate(state));
        var queue = new CommandQueue(); plan.forEach(queue::submit); var researching = queue.drainAndExecute(state);
        assertTrue(researching.researchProjects().getFirst().isApplication());
        assertTrue(EmpireResearchPlanner.plan(researching, researching.empires().getFirst()).isEmpty());
    }
    @Test void pendingOutcomesAreConsumedThroughCommandsAndCannotBeReused() {
        var state = new ResearchLifecycleTest().world();
        var project = new ResearchProject("done", "research", "solar_power", true, 500, 500, 5, 1);
        state = state.withApplicationOptimizations(ApplicationRefinement.completed(state.applicationOptimizations(), project,
                new ResearchProcessor().rollBreakthrough(.1)));
        var plan = EmpireResearchPlanner.plan(state, state.empires().getFirst());
        assertTrue(plan.stream().anyMatch(command -> command instanceof SelectOptimizationPathCommand));
        var queue = new CommandQueue(); plan.forEach(queue::submit); var resolved = queue.drainAndExecute(state);
        var development = ApplicationRefinement.find(resolved.applicationOptimizations(), "research", "solar_power");
        assertNull(development.pendingOutcome()); assertEquals(1, development.completedRefinements());
        assertEquals(.85, development.result().costMultiplier());
        assertFalse(EmpireResearchPlanner.plan(resolved, resolved.empires().getFirst()).stream()
                .anyMatch(command -> command instanceof SelectOptimizationPathCommand));
    }
    @Test void flawedPrototypeIsDiscardedAndPausedProjectsResumeInsteadOfDuplicating() {
        var state = new ResearchLifecycleTest().world();
        state = new StartResearchCommand("research", "nuclear_fission", false, 5).apply(state);
        state = new StartResearchCommand("research", "nuclear_fission", false, 0).apply(state);
        var project = new ResearchProject("done", "research", "solar_power", true, 500, 500, 5, 1);
        state = state.withApplicationOptimizations(ApplicationRefinement.completed(state.applicationOptimizations(), project,
                new ResearchProcessor().rollBreakthrough(.95)));
        var queue = new CommandQueue(); EmpireResearchPlanner.plan(state, state.empires().getFirst()).forEach(queue::submit);
        var resumed = queue.drainAndExecute(state);
        assertEquals(1, resumed.researchProjects().size()); assertEquals(5, resumed.researchProjects().getFirst().assignedScientists());
        assertEquals("nuclear_fission", resumed.researchProjects().getFirst().targetTechOrAppId());
        assertEquals(1, resumed.applicationOptimizations().getFirst().result().effectMultiplier());
        assertNull(resumed.applicationOptimizations().getFirst().pendingOutcome());
    }
}
