package com.spaceconquest.control.ai;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.EconomicOwnership;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Corporate-first bounded investment after repeated unmet facility supply needs. */
public final class TransportConstructionPlanner {
    private record Observation(long first, long last, int count) {}
    private final Map<String, Observation> observations = new HashMap<>();
    private long lastTurn = -1;
    public List<GameCommand> plan(GameState state, Empire empire, boolean availableService) {
        if (state.turn() < lastTurn) observations.clear(); lastTurn = state.turn();
        var needs = FacilitySupplyNeeds.shortages(state, empire, LogisticsPolicy.current()).stream().filter(n ->
                state.supplyDevelopmentPlans().stream().noneMatch(p -> !p.closed() && p.empireId().equals(empire.id())
                        && p.destinationHubId().equals(n.hubId()) && p.materialId().equals(n.materialId()) && !p.fleetId().isBlank()
                        && state.fleets().stream().anyMatch(f -> f.id().equals(p.fleetId()) && f.ships().stream().anyMatch(s -> s.currentHullHealth() > 0)))).toList();
        observations.keySet().retainAll(needs.stream().map(FacilitySupplyNeeds.Need::key).toList());
        if (availableService || FacilityTransportBuildQuote.pending(state, empire)) { observations.clear(); return List.of(); }
        var policy = TransportConstructionPolicy.current(); int previews = 0;
        var owners = new ArrayList<String>();
        if (EconomicOwnership.corporationsAllowed(empire)) state.corporations().stream().filter(corp -> EconomicOwnership.transportOperator(state, empire, corp.id()))
                .sorted(Comparator.comparing(Corporation::id)).forEach(corp -> owners.add(corp.id()));
        owners.add(empire.id());
        for (var need : needs) {
            var old = observations.get(need.key()); var observation = old == null || state.turn() > old.last() + 1
                    ? new Observation(state.turn(), state.turn(), 1) : old.last() == state.turn() ? old : new Observation(old.first(), state.turn(), old.count() + 1);
            observations.put(need.key(), observation);
            if (observation.count() < policy.observationTurns()) continue;
            for (String owner : owners) {
                var designs = new ArrayList<>(state.shipDesigns().stream().filter(item -> owner.equals(item.ownerEntityId())
                        && ShipRole.CARGO_TRANSPORT.equals(item.role())).map(ShipDesign::id).sorted().toList());
                if (!owner.equals(empire.id())) designs.add("");
                for (String designId : designs) {
                    for (String system : empire.controlledSystemIds().stream().sorted().toList()) {
                        if (previews++ >= policy.maximumBuildPreviews()) return List.of();
                        var command = new RequestAIFacilityTransportBuildCommand(owner, designId, system, need.hubId(), need.materialId(), observation.first());
                        if (command.validate(state)) return List.of(command);
                    }
                }
            }
        }
        return List.of();
    }
}
