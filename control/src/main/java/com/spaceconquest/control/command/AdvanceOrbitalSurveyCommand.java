package com.spaceconquest.control.command;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Buys mapping equipment and stages reachable coverage gaps through ordinary travel commands. */
public record AdvanceOrbitalSurveyCommand(String empireId, String fleetId) implements GameCommand {
    @Override public boolean validate(GameState state) { return state != null && next(state) != state; }
    @Override public GameState apply(GameState state) { return state == null ? null : next(state); }
    private GameState next(GameState state) {
        if (empireId == null || fleetId == null) return state;
        var fleet = state.fleets().stream().filter(f -> f.id().equals(fleetId)).findFirst().orElse(null);
        if (!OrbitalSurveyOperations.eligible(state, fleet, empireId) || fleet.hasInterstellarOrder() || fleet.location().inTransit()) return state;
        var team = state.surveyTeams().stream().filter(t -> t.fleetId().equals(fleetId) && t.empireId().equals(empireId)).findFirst().orElse(null);
        if (team == null) return OrbitalSurveyOperations.establish(state, empireId, fleetId);
        var targets = OrbitalSurveyOperations.targets(state, empireId);
        if (team.progressWork() == 0) {
            var candidates = targets.stream().filter(body -> !body.equals(team.bodyId()))
                    .filter(body -> !targets.contains(team.bodyId()) || coverage(state, empireId, body) < coverage(state, empireId, team.bodyId()))
                    .sorted(Comparator.comparingLong((String body) -> coverage(state, empireId, body))
                            .thenComparing(body -> !fleet.currentSystemId().equals(ConstructionMaterials.systemForBody(state, body)))
                            .thenComparing(body -> body)).limit(8).toList();
            for (var target : candidates) {
                var assigned = OrbitalSurveyOperations.retarget(state, empireId, fleetId, target);
                if (assigned == state) continue;
                var updated = assigned.surveyTeams().stream().filter(t -> t.id().equals(team.id())).findFirst().orElseThrow();
                var travelled = travel(assigned, fleet, updated);
                if (travelled != assigned || OrbitalSurveyOperations.atOrbit(fleet, target)) return travelled;
            }
        }
        return targets.contains(team.bodyId()) ? travel(state, fleet, team) : state;
    }
    private GameState travel(GameState state, Fleet fleet, SurveyTeam team) {
        if (OrbitalSurveyOperations.atOrbit(fleet, team.bodyId())) return state;
        String system = ConstructionMaterials.systemForBody(state, team.bodyId());
        GameCommand move; var supplied = state;
        if (fleet.currentSystemId().equals(system)) {
            supplied = ShipPowerResupply.prepareLocal(state, fleet, FleetLocation.Site.orbit(team.bodyId()));
            move = new MoveFleetLocalCommand(fleetId, FleetLocation.Kind.ORBIT, team.bodyId());
        } else move = new MoveFleetCommand(fleetId, system);
        return move.validate(supplied) ? move.apply(supplied) : state;
    }
    private static long coverage(GameState state, String empire, String body) {
        return state.surveyTeams().stream().filter(t -> t.empireId().equals(empire) && !t.fleetId().isBlank())
                .mapToLong(t -> t.coveragePasses().getOrDefault(body, 0L)).sum();
    }
}
