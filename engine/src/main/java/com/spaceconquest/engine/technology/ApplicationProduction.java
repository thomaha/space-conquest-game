package com.spaceconquest.engine.technology;

import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Technology;

import java.util.List;

/** Resolves an empire's researched application modifiers without changing the shared catalog. */
public final class ApplicationProduction {
    private static final ResearchVarianceResult BASELINE = new ResearchVarianceResult("BASELINE", 1.0, 1.0, 0);

    private ApplicationProduction() {}

    public static boolean canOptimize(Empire empire, String applicationId, List<Technology> technologies) {
        if (empire == null || !empire.unlockedTechIds().contains(applicationId)) return false;
        return canResearch(empire, applicationId, true, technologies);
    }

    public static boolean canResearch(Empire empire, String targetId, boolean isApplication, List<Technology> technologies) {
        if (empire == null || targetId == null || targetId.isBlank()) return false;
        if (!isApplication) return technologies.stream().anyMatch(technology -> targetId.equals(technology.id())
                && empire.unlockedTechIds().containsAll(technology.requiredTechnologies()));
        return technologies.stream().anyMatch(technology -> empire.unlockedTechIds().contains(technology.id())
                && technology.applications().stream().anyMatch(application -> application.id().equals(targetId)
                && empire.unlockedTechIds().containsAll(application.requiredTechnologies())));
    }

    public static ResearchVarianceResult modifiers(GameState state, String empireId, String applicationId) {
        if (state == null || empireId == null || applicationId == null) return BASELINE;
        boolean researched = state.empires().stream().anyMatch(empire -> empire.id().equals(empireId)
                && empire.unlockedTechIds().contains(applicationId));
        if (!researched) return BASELINE;
        return state.applicationOptimizations().stream()
                .filter(selection -> empireId.equals(selection.empireId())
                        && applicationId.equals(selection.applicationId()))
                .map(ApplicationOptimization::result).findFirst().orElse(BASELINE);
    }

    public static ResearchVarianceResult modifiersForOwner(GameState state, String ownerId, String applicationId) {
        String empireId = state.corporations().stream().filter(corporation -> corporation.id().equals(ownerId))
                .map(corporation -> corporation.empireId()).findFirst().orElse(ownerId);
        return modifiers(state, empireId, applicationId);
    }

    public static double constructionWorkHours(GameState state, String ownerId, String applicationId,
                                                double baseWorkHours) {
        return baseWorkHours * modifiersForOwner(state, ownerId, applicationId).costMultiplier();
    }
}
