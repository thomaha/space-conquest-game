package com.spaceconquest.control.ai;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;
import java.util.*;

/** Continuous local fieldwork, independent of marketplace demand. Hidden deposits never inform allocation. */
public final class SurveyProgramPlanner {
    private SurveyProgramPlanner() {}
    public static int desiredTeams(GameState state, String body) {
        boolean shortage = state.commercialHubs().stream().filter(h -> h.entityId().equals(body))
                .flatMap(h -> h.activeOrders().values().stream()).anyMatch(o -> o.demandKg() > o.supplyKg());
        boolean depletion = state.geologicalDeposits().stream().anyMatch(d -> d.planetId().equals(body)
                && d.isDiscovered() && d.remainingVolumeKg() <= 0);
        return 1 + (shortage ? 1 : 0) + (depletion ? 1 : 0);
    }
    public static List<String> targets(GameState state, Empire empire) {
        return state.commercialHubs().stream().map(CommercialHub::entityId).distinct()
                .filter(body -> SurveyOperations.controls(state, empire.id(), body))
                .sorted(Comparator.comparingDouble((String body) -> state.surveyTeams().stream()
                        .filter(t -> t.fleetId().isBlank() && t.empireId().equals(empire.id()) && t.bodyId().equals(body))
                        .mapToDouble(SurveyTeam::totalWork).sum() / desiredTeams(state, body)).thenComparing(body -> body)).toList();
    }
    public static Set<String> reservedFleets(GameState state, Empire empire) {
        if (OrbitalSurveyOperations.definition(state, empire.id()) == null) return Set.of();
        return state.fleets().stream().filter(f -> OrbitalSurveyOperations.eligible(state, f, empire.id()))
                .map(com.spaceconquest.engine.ship.Fleet::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    public static List<GameCommand> plan(GameState state, Empire empire) {
        var commands = new ArrayList<GameCommand>(); var preview = state;
        for (var body : targets(state, empire)) {
            var command = new MaintainSurveyProgramCommand(empire.id(), body, desiredTeams(state, body));
            var next = command.apply(preview);
            if (next != preview) { commands.add(command); preview = next; }
            if (commands.size() >= 8) break;
        }
        var reserved = reservedFleets(state, empire);
        for (var fleet : state.fleets()) if (reserved.contains(fleet.id())) {
            var command = new AdvanceOrbitalSurveyCommand(empire.id(), fleet.id());
            if (command.validate(preview)) { commands.add(command); preview = command.apply(preview); }
        }
        return List.copyOf(commands);
    }
}
