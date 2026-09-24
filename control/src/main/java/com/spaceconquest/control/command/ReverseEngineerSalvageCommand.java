package com.spaceconquest.control.command;

import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Race;
import com.spaceconquest.engine.technology.ResearchProcessor;
import com.spaceconquest.engine.technology.ResearchProject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Command to inject research progress vectors into a project from foreign salvage or captured debris.
 */
public record ReverseEngineerSalvageCommand(
        String empireId,
        String targetTechOrAppId,
        String sourceRaceId,
        boolean isIntact,
        int componentCount
) implements GameCommand {

    public ReverseEngineerSalvageCommand(String empireId, String salvageDebrisId, double salvageQuality, String targetTechId) {
        this(empireId, targetTechId, "vulkan", salvageQuality >= 0.5, 1);
    }

    @Override
    public boolean validate(GameState state) {
        if (state == null || empireId == null || targetTechOrAppId == null) {
            return false;
        }
        if (componentCount <= 0) {
            return false;
        }
        return state.empires().stream().anyMatch(e -> e.id().equals(empireId));
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        Empire empire = state.empires().stream().filter(e -> e.id().equals(empireId)).findFirst().orElse(null);
        if (empire == null) return state;

        Race analyzingRace = null;
        Race sourceRace = null;
        try {
            List<Race> races = DataModelLoader.loadRaces();
            for (Race r : races) {
                if (r.id().equalsIgnoreCase(empire.raceId())) {
                    analyzingRace = r;
                }
                if (sourceRaceId != null && r.id().equalsIgnoreCase(sourceRaceId)) {
                    sourceRace = r;
                }
            }
        } catch (IOException ignored) {}

        ResearchProcessor processor = new ResearchProcessor();
        double baseRequired = 1000.0;
        double injectedPoints = processor.calculateSalvageProgressPoints(
                isIntact,
                componentCount,
                analyzingRace,
                sourceRace,
                baseRequired
        );

        List<ResearchProject> updatedProjects = new ArrayList<>();
        boolean found = false;
        for (ResearchProject project : state.researchProjects()) {
            if (project.empireId().equals(empireId) && project.targetTechOrAppId().equals(targetTechOrAppId)) {
                updatedProjects.add(new ResearchProject(
                        project.id(),
                        empireId,
                        targetTechOrAppId,
                        project.isApplication(),
                        project.accumulatedPoints() + injectedPoints,
                        project.requiredPoints(),
                        project.assignedScientists(),
                        project.speedModifier()
                ));
                found = true;
            } else {
                updatedProjects.add(project);
            }
        }

        if (!found) {
            updatedProjects.add(new ResearchProject(
                    "res_" + UUID.randomUUID().toString().substring(0, 8),
                    empireId,
                    targetTechOrAppId,
                    false,
                    injectedPoints,
                    baseRequired,
                    1,
                    1.0
            ));
        }

        return state.toBuilder()
                .researchProjects(updatedProjects)
                .build();
    }
}
