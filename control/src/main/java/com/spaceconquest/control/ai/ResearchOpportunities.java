package com.spaceconquest.control.ai;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.technology.*;
import java.util.*;

/** Candidates include eligible discoveries, prerequisites of usable applications and urgent paid refinements. */
final class ResearchOpportunities {
    record Candidate(String target, boolean application, int priority, boolean refinement, double cost, String goalApplicationId, String reason) {}
    private ResearchOpportunities() {}
    static Optional<Candidate> choose(GameState state, Empire empire, List<Technology> catalog, Set<String> active, EmpireResearchNeeds needs) {
        var candidates = new ArrayList<Candidate>(); var foundationRanks = new HashMap<String, Integer>();
        var goals = new HashMap<String, String>();
        for (var technology : catalog) for (var app : technology.applications()) {
            if (ApplicationIntegration.describe(app).status() == ApplicationIntegration.Status.PLANNED) continue;
            int rank = needs.priority(app); boolean owned = empire.unlockedTechIds().contains(app.id());
            if (!owned) {
                collect(technology.id(), rank, app.id(), empire, catalog, foundationRanks, goals, new HashSet<>());
                for (String prerequisite : app.requiredTechnologies()) collect(prerequisite, rank, app.id(), empire, catalog, foundationRanks, goals, new HashSet<>());
            }
            var development = ApplicationRefinement.find(state.applicationOptimizations(), empire.id(), app.id());
            if (!active.contains(app.id()) && (development == null || development.pendingOutcome() == null)
                    && ApplicationProduction.canResearch(empire, app.id(), true, catalog))
                candidates.add(new Candidate(app.id(), true, owned && rank > 4 ? 9 : rank, owned,
                        ResearchPolicy.current().cost(empire, app.id(), true, catalog), app.id(),
                        (owned ? "Refinement: " : "Discovery: ") + needs.reason(app)));
        }
        for (var technology : catalog) if (!active.contains(technology.id()) && foundationRanks.containsKey(technology.id())
                && ApplicationProduction.canResearch(empire, technology.id(), false, catalog))
            candidates.add(new Candidate(technology.id(), false, foundationRanks.get(technology.id()), false,
                    ResearchPolicy.current().cost(empire, technology.id(), false, catalog), goals.get(technology.id()),
                    "Prerequisite toward " + ResearchFeedback.name(goals.get(technology.id()), catalog) + "."));
        return candidates.stream().min(Comparator.comparingInt(Candidate::priority)
                .thenComparing(Candidate::refinement).thenComparing(candidate -> !candidate.application())
                .thenComparingDouble(Candidate::cost).thenComparing(Candidate::target));
    }
    private static void collect(String id, int rank, String goal, Empire empire, List<Technology> catalog, Map<String, Integer> ranks, Map<String, String> goals, Set<String> seen) {
        if (empire.unlockedTechIds().contains(id) || !seen.add(id)) return;
        for (var technology : catalog) {
            if (technology.id().equals(id)) {
                if (rank < ranks.getOrDefault(id, Integer.MAX_VALUE) || rank == ranks.getOrDefault(id, Integer.MAX_VALUE) && goal.compareTo(goals.get(id)) < 0) goals.put(id, goal);
                ranks.merge(id, rank, Math::min);
                technology.requiredTechnologies().forEach(prerequisite -> collect(prerequisite, rank, goal, empire, catalog, ranks, goals, seen));
                return;
            }
            for (var app : technology.applications()) if (app.id().equals(id)) {
                collect(technology.id(), rank, goal, empire, catalog, ranks, goals, seen);
                app.requiredTechnologies().forEach(prerequisite -> collect(prerequisite, rank, goal, empire, catalog, ranks, goals, seen));
                return;
            }
        }
    }
}
