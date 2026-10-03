package com.spaceconquest.engine.technology;

import com.spaceconquest.engine.DataModelLoader;

import java.io.IOException;
import java.io.UncheckedIOException;

import java.util.ArrayList;
import java.util.List;

/** Keeps completed application research pending until the owner chooses how to use it. */
public final class ApplicationRefinement {
    private ApplicationRefinement() {}

    public static ApplicationOptimization find(List<ApplicationOptimization> values, String empireId, String appId) {
        return values.stream().filter(value -> empireId.equals(value.empireId())
                && appId.equals(value.applicationId())).findFirst().orElse(null);
    }

    public static List<ApplicationOptimization> completed(List<ApplicationOptimization> values,
                                                         ResearchProject project, ResearchVarianceResult outcome) {
        ApplicationOptimization previous = find(values, project.empireId(), project.targetTechOrAppId());
        if (previous == null) previous = new ApplicationOptimization(
                project.empireId(), project.targetTechOrAppId(), "NONE", null);
        // A pending decision must not be overwritten by a duplicate project in an older save.
        if (previous.pendingOutcome() != null) return values;
        ApplicationOptimization pending = new ApplicationOptimization(previous.empireId(), previous.applicationId(),
                previous.pathChoice(), previous.result(), outcome, previous.completedRefinements());
        return replace(values, pending);
    }

    public static List<ApplicationOptimization> replace(List<ApplicationOptimization> values,
                                                       ApplicationOptimization replacement) {
        List<ApplicationOptimization> updated = new ArrayList<>(values);
        updated.removeIf(value -> value.empireId().equals(replacement.empireId())
                && value.applicationId().equals(replacement.applicationId()));
        updated.add(replacement);
        return updated;
    }

    public static ApplicationOptimization resolve(ApplicationOptimization development,
                                                  ResearchVarianceResult improvement, String path) {
        ApplicationOptimization resolved = development.resolve(improvement, path);
        try {
            int baseline = DataModelLoader.loadTechnologies().stream().flatMap(technology -> technology.applications().stream())
                    .filter(application -> application.id().equals(development.applicationId()))
                    .mapToInt(application -> application.complexity()).findFirst().orElse(1);
            ResearchVarianceResult modifiers = resolved.result();
            int shift = Math.max(1 - baseline, modifiers.complexityShift());
            return new ApplicationOptimization(resolved.empireId(), resolved.applicationId(), resolved.pathChoice(),
                    new ResearchVarianceResult(modifiers.outcomeType(), modifiers.effectMultiplier(),
                            modifiers.costMultiplier(), shift), resolved.pendingOutcome(), resolved.completedRefinements());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot resolve application research", e);
        }
    }
}
