package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;

/** Explicit cancellation discards progress; zero-scientist reassignment pauses without discarding it. */
public record CancelResearchCommand(String empireId, String projectId) implements GameCommand {
    public boolean validate(GameState state) {
        return state != null && empireId != null && projectId != null && state.empires().stream().anyMatch(empire -> empireId.equals(empire.id()))
                && state.researchProjects().stream().anyMatch(project -> projectId.equals(project.id()) && empireId.equals(project.empireId()));
    }
    public GameState apply(GameState state) {
        if (!validate(state)) return state;
        var project = state.researchProjects().stream().filter(value -> projectId.equals(value.id()) && empireId.equals(value.empireId())).findFirst().orElseThrow();
        var cancelled = state.withResearchProjects(state.researchProjects().stream()
                .filter(value -> !(projectId.equals(value.id()) && empireId.equals(value.empireId()))).toList());
        return com.spaceconquest.engine.technology.ResearchHistory.record(cancelled, new com.spaceconquest.engine.technology.ResearchEvent(
                state.turn(), empireId, projectId, project.targetTechOrAppId(), com.spaceconquest.engine.technology.ResearchEvent.Kind.CANCELLED,
                new com.spaceconquest.engine.technology.ResearchDecision(com.spaceconquest.engine.technology.ResearchDecision.Actor.PLAYER,
                        "Cancelled; earned progress was discarded.", project.isApplication() ? project.targetTechOrAppId() : null), null));
    }
}
