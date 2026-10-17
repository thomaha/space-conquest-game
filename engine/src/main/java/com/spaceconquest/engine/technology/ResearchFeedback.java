package com.spaceconquest.engine.technology;

import com.spaceconquest.engine.*;
import java.util.*;

/** Player-facing reasons and estimates calculated from a supplied immutable snapshot. */
public final class ResearchFeedback {
    public record Estimate(double fundedScientists, double pointsPerDay, Long daysRemaining, double exchangeBonus, String reason) {}
    private ResearchFeedback() {}
    public static String name(String id, List<Technology> catalog) {
        for (var technology : catalog) {
            if (technology.id().equals(id)) return technology.name();
            for (var app : technology.applications()) if (app.id().equals(id)) return app.name();
        }
        return id;
    }
    public static String prerequisites(Empire empire, String target, boolean application, List<Technology> catalog) {
        if (empire == null) return "No empire selected.";
        var required = new LinkedHashSet<String>(); boolean found = false;
        for (var technology : catalog) {
            if (!application && technology.id().equals(target)) { required.addAll(technology.requiredTechnologies()); found = true; }
            if (application) for (var app : technology.applications()) if (app.id().equals(target)) {
                required.add(technology.id()); required.addAll(app.requiredTechnologies()); found = true;
            }
        }
        if (!found) return "Unknown research target or incorrect target type.";
        required.removeAll(empire.unlockedTechIds());
        return required.isEmpty() ? "" : "Requires research: " + String.join(", ", required.stream().map(id -> name(id, catalog)).toList()) + ".";
    }
    public static String availability(GameState state, Empire empire, String target, boolean application, List<Technology> catalog) {
        String blocked = prerequisites(empire, target, application, catalog);
        if (!blocked.isEmpty()) return blocked;
        if (!application && empire.unlockedTechIds().contains(target)) return "Already researched.";
        if (application) {
            var development = ApplicationRefinement.find(state.applicationOptimizations(), empire.id(), target);
            if (development != null && development.pendingOutcome() != null) return "Resolve the completed outcome before further research.";
        }
        if (state.researchProjects().stream().anyMatch(project -> empire.id().equals(project.empireId()) && target.equals(project.targetTechOrAppId())))
            return "Project exists. Update its assignment or resume it above.";
        if (ResearchWorkforce.capacity(state, empire) == 0) return "No funded scientists. Check controlled population, education funding and scientist staffing.";
        long reserved = state.researchProjects().stream().filter(project -> empire.id().equals(project.empireId()))
                .mapToLong(project -> Math.max(0, project.assignedScientists())).sum();
        if (reserved >= ResearchWorkforce.capacity(state, empire)) return "All funded scientists are reserved. Reduce or pause another project above.";
        return "";
    }
    public static Estimate estimate(Empire empire, ResearchProject project, List<Technology> catalog, ResearchFunding funding) {
        String reason = prerequisites(empire, project.targetTechOrAppId(), project.isApplication(), catalog);
        if (!reason.isEmpty()) return new Estimate(0, 0, null, 0, reason);
        if (project.isComplete()) return new Estimate(0, 0, 0L, 0, "Complete; unlock and outcome processing occurs on the next tick.");
        if (project.assignedScientists() <= 0) return new Estimate(0, 0, null, 0, "Paused: assign scientists to resume. Earned progress is retained.");
        double points = funding.points(project), scientists = funding.scientists(project);
        double bonus = ResearchFunding.exchangeBonus(project, funding.routes());
        if (points <= 0) return new Estimate(scientists, 0, null, bonus,
                "No funded progress. Check controlled population, education funding, scientist staffing and project speed.");
        long days = (long) Math.ceil(Math.max(0, project.requiredPoints() - project.accumulatedPoints()) / points);
        return new Estimate(scientists, points, days, bonus, scientists < project.assignedScientists()
                ? "Staffing shortage: available scientists are shared across active projects." : "");
    }
}
