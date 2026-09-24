package com.spaceconquest.control.command;

import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.technology.ResearchProject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Command to start or update a scientific research project for an empire.
 */
public record StartResearchCommand(
        String empireId,
        String targetTechOrAppId,
        boolean isApplication,
        int assignedScientists
) implements GameCommand {

    @Override
    public boolean validate(GameState state) {
        if (state == null || empireId == null || targetTechOrAppId == null) {
            return false;
        }
        if (assignedScientists <= 0) {
            return false;
        }

        // Validate that total assigned scientists does not exceed available headcount
        long availableScientists = (state.systemEconomies() == null || state.systemEconomies().isEmpty()) ? 100 : state.systemEconomies().stream()
                .filter(se -> se.empireId().equals(empireId))
                .mapToLong(se -> se.employedScientists())
                .sum();

        int currentlyAssigned = (state.researchProjects() == null) ? 0 : state.researchProjects().stream()
                .filter(p -> p.empireId().equals(empireId) && !p.targetTechOrAppId().equals(targetTechOrAppId))
                .mapToInt(p -> p.assignedScientists())
                .sum();

        if (currentlyAssigned + assignedScientists > availableScientists) {
            return false;
        }

        return state.empires().stream().anyMatch(e -> e.id().equals(empireId));
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        List<ResearchProject> updatedProjects = new ArrayList<>();
        boolean replaced = false;

        for (ResearchProject project : state.researchProjects()) {
            if (project.empireId().equals(empireId) && project.targetTechOrAppId().equals(targetTechOrAppId)) {
                updatedProjects.add(new ResearchProject(
                        project.id(),
                        empireId,
                        targetTechOrAppId,
                        isApplication,
                        project.accumulatedPoints(),
                        project.requiredPoints(),
                        assignedScientists,
                        project.speedModifier()
                ));
                replaced = true;
            } else {
                updatedProjects.add(project);
            }
        }

        if (!replaced) {
            double requiredPoints = isApplication ? 500.0 : 1000.0;
            updatedProjects.add(new ResearchProject(
                    "res_" + UUID.randomUUID().toString().substring(0, 8),
                    empireId,
                    targetTechOrAppId,
                    isApplication,
                    0.0,
                    requiredPoints,
                    assignedScientists,
                    1.0
            ));
        }

        return state.toBuilder()
                .researchProjects(updatedProjects)
                .build();
    }
}
