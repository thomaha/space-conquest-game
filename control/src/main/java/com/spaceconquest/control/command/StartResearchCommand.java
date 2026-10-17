package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.technology.ApplicationProduction;
import com.spaceconquest.engine.technology.ApplicationRefinement;
import com.spaceconquest.engine.technology.ResearchProject;
import com.spaceconquest.engine.technology.ResearchWorkforce;
import com.spaceconquest.engine.technology.ResearchPolicy;
import com.spaceconquest.engine.technology.ResearchExchange;

import java.io.IOException;

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
        int assignedScientists,
        com.spaceconquest.engine.technology.ResearchDecision decision
) implements GameCommand {
    public StartResearchCommand(String empireId, String targetTechOrAppId, boolean isApplication, int assignedScientists) {
        this(empireId, targetTechOrAppId, isApplication, assignedScientists, null);
    }

    @Override
    public boolean validate(GameState state) {
        if (state == null || empireId == null || targetTechOrAppId == null) {
            return false;
        }
        if (assignedScientists < 0) {
            return false;
        }
        var empire = state.empires().stream().filter(value -> empireId.equals(value.id())).findFirst().orElse(null);
        if (empire == null) return false;
        var existing = state.researchProjects().stream().filter(project -> empireId.equals(project.empireId())
                && targetTechOrAppId.equals(project.targetTechOrAppId()) && project.isApplication() == isApplication).findFirst().orElse(null);
        if (assignedScientists == 0) return existing != null;
        try {
            if (!ApplicationProduction.canResearch(empire, targetTechOrAppId, isApplication,
                    DataModelLoader.loadTechnologies())) return false;
        } catch (IOException e) {
            return false;
        }
        if (empire.unlockedTechIds().contains(targetTechOrAppId)) {
            if (!isApplication) return false;
            var development = ApplicationRefinement.find(state.applicationOptimizations(), empireId, targetTechOrAppId);
            if (development != null && development.pendingOutcome() != null) return false;
        }

        // A funding shortage must not prevent reducing an existing requested assignment.
        if (existing != null && assignedScientists <= existing.assignedScientists()) return true;

        // Validate that total assigned scientists does not exceed available headcount
        long availableScientists = ResearchWorkforce.capacity(state, empire);

        long currentlyAssigned = state.researchProjects().stream()
                .filter(p -> p.empireId().equals(empireId) && !p.targetTechOrAppId().equals(targetTechOrAppId))
                .mapToLong(p -> Math.max(0, p.assignedScientists()))
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
        var receipt = decision == null ? new com.spaceconquest.engine.technology.ResearchDecision(
                com.spaceconquest.engine.technology.ResearchDecision.Actor.PLAYER, "Requested " + assignedScientists + " scientists.", isApplication ? targetTechOrAppId : null) : decision;

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
                        project.speedModifier(), project.decision() == null ? receipt : project.decision()
                ));
                replaced = true;
            } else {
                updatedProjects.add(project);
            }
        }

        if (!replaced) {
            var empire = state.empires().stream().filter(value -> empireId.equals(value.id())).findFirst().orElseThrow();
            double requiredPoints;
            double initialPoints;
            try {
                var catalog = DataModelLoader.loadTechnologies();
                requiredPoints = ResearchPolicy.current().cost(empire, targetTechOrAppId, isApplication, catalog);
                initialPoints = ResearchExchange.openingPoints(state, empire, targetTechOrAppId, requiredPoints, catalog);
            } catch (IOException failure) { return state; }
            updatedProjects.add(new ResearchProject(
                    "res_" + UUID.randomUUID().toString().substring(0, 8),
                    empireId,
                    targetTechOrAppId,
                    isApplication,
                    initialPoints,
                    requiredPoints,
                    assignedScientists,
                    1.0, receipt
            ));
        }

        var previous = state.researchProjects().stream().filter(project -> empireId.equals(project.empireId()) && targetTechOrAppId.equals(project.targetTechOrAppId())).findFirst().orElse(null);
        if (previous != null && previous.assignedScientists() == assignedScientists) return state;
        var kind = previous == null ? com.spaceconquest.engine.technology.ResearchEvent.Kind.STARTED
                : assignedScientists == 0 ? com.spaceconquest.engine.technology.ResearchEvent.Kind.PAUSED
                : previous.assignedScientists() == 0 ? com.spaceconquest.engine.technology.ResearchEvent.Kind.RESUMED
                : com.spaceconquest.engine.technology.ResearchEvent.Kind.REASSIGNED;
        var project = updatedProjects.stream().filter(value -> empireId.equals(value.empireId()) && targetTechOrAppId.equals(value.targetTechOrAppId())).findFirst().orElseThrow();
        return com.spaceconquest.engine.technology.ResearchHistory.record(state.withResearchProjects(updatedProjects),
                new com.spaceconquest.engine.technology.ResearchEvent(state.turn(), empireId, project.id(), targetTechOrAppId, kind, receipt, null));
    }
}
