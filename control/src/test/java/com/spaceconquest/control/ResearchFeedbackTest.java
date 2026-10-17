package com.spaceconquest.control;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.technology.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResearchFeedbackTest {
    private ResearchFeedback.Estimate estimate(GameState state, ResearchProject project) throws Exception {
        var catalog = DataModelLoader.loadTechnologies(); var empire = state.empires().getFirst();
        return ResearchFeedback.estimate(empire, project, catalog,
                ResearchFunding.quote(state, empire, DataModelLoader.loadRaces(), catalog, new ResearchProcessor()));
    }
    @Test void estimatesMatchActualDailyProgressAndShareShortagesWithoutPromisingFullAssignments() throws Exception {
        var fixture = new ResearchLifecycleTest(); var state = fixture.scientists(fixture.world(), 6);
        var first = new ResearchProject("first", "research", "nuclear_fission", false, 11, 100, 15, 2);
        var second = new ResearchProject("second", "research", "solar_power", true, 0, 500, 15, 1);
        state = state.withResearchProjects(List.of(first, second));
        var forecast = estimate(state, first); assertEquals(3, forecast.fundedScientists()); assertEquals(6, forecast.pointsPerDay());
        assertEquals(15L, forecast.daysRemaining()); assertTrue(forecast.reason().contains("shared"));
        var actual = new ResearchTickProcessor().process(state, DataModelLoader.loadRaces(), new ResearchProcessor(new Random(42)));
        assertEquals(forecast.pointsPerDay(), actual.projects().getFirst().accumulatedPoints() - first.accumulatedPoints());
    }
    @Test void onlyCurrentEligibleExchangeBonusesReduceTheCompletionEstimate() throws Exception {
        var state = new ResearchLifecycleTest().world();
        var sender = new Empire("source", "Source", "human", "Individualist", 0, 0, List.of(), List.of(), Map.of(), List.of("electricity", "nuclear_fission"), List.of());
        var project = new ResearchProject("project", "research", "nuclear_fission", false, 10, 100, 5, 1);
        state = state.withEmpires(List.of(state.empires().getFirst(), sender)).withResearchProjects(List.of(project))
                .withDiplomaticRelations(List.of(new DiplomaticRelation("source", "research", "COMMERCIAL_ALLIANCE", 0)))
                .withTechnologyExchangeRoutes(List.of(new TechnologyExchangeRoute("one", "source", "research", "nuclear_fission", .4, .5),
                        new TechnologyExchangeRoute("two", "source", "research", "nuclear_fission", .3, .2)));
        var forecast = estimate(state, project); assertEquals(.5, forecast.exchangeBonus()); assertEquals(7.5, forecast.pointsPerDay());
        assertEquals(12L, forecast.daysRemaining());
        var actual = new ResearchTickProcessor().process(state, DataModelLoader.loadRaces(), new ResearchProcessor());
        assertEquals(7.5, actual.projects().getFirst().accumulatedPoints() - 10);
        var revoked = estimate(state.withDiplomaticRelations(List.of()), project);
        assertEquals(0, revoked.exchangeBonus()); assertEquals(18L, revoked.daysRemaining());
    }
    @Test void pausedUnfundedAndIneligibleProjectsHaveReasonsInsteadOfFiniteCompletionDates() throws Exception {
        var fixture = new ResearchLifecycleTest(); var state = fixture.world();
        var project = new ResearchProject("project", "research", "nuclear_fission", false, 10, 100, 0, 1);
        state = state.withResearchProjects(List.of(project));
        assertNull(estimate(state, project).daysRemaining()); assertTrue(estimate(state, project).reason().contains("Paused"));
        project = new ResearchProject("project", "research", "nuclear_fission", false, 10, 100, 5, 1);
        state = fixture.scientists(state.withResearchProjects(List.of(project)), 0);
        assertNull(estimate(state, project).daysRemaining()); assertTrue(estimate(state, project).reason().contains("No funded"));
        var locked = new ResearchProject("locked", "research", "warp", false, 10, 100, 5, 1);
        assertNull(estimate(state, locked).daysRemaining()); assertTrue(estimate(state, locked).reason().contains("Requires research"));
        assertTrue(ResearchFeedback.prerequisites(state.empires().getFirst(), "unknown", true, DataModelLoader.loadTechnologies()).contains("Unknown"));
    }
    @Test void completedProjectsRoundUpTheFinalDayAndNeverForecastAnotherGrant() throws Exception {
        var state = new ResearchLifecycleTest().world();
        var project = new ResearchProject("project", "research", "nuclear_fission", false, 99, 100, 5, 1);
        state = state.withResearchProjects(List.of(project)); assertEquals(1L, estimate(state, project).daysRemaining());
        var done = new ResearchProject("project", "research", "nuclear_fission", false, 100, 100, 5, 1);
        assertEquals(0L, estimate(state, done).daysRemaining()); assertEquals(0, estimate(state, done).pointsPerDay());
    }
}
