package com.spaceconquest.control.ai;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.Fleet;
import java.util.*;

/** Observes distinct shortage days and reserves feasible idle carriers ahead of generic roaming assignments. */
public final class EmpireLogisticsPlanner {
    public record Plan(List<GameCommand> commands, Set<String> reservedFleetIds) {
        public Plan { commands = List.copyOf(commands); reservedFleetIds = Set.copyOf(reservedFleetIds); }
    }
    private record Observation(long lastTurn, long firstTurn, int count) {}
    private final Map<String, Observation> observations = new HashMap<>();
    private long lastTurn = -1;
    public Plan plan(GameState state, Empire empire) {
        if (state.turn() < lastTurn) observations.clear(); lastTurn = state.turn();
        for (var route : state.tradeRoutes()) {
            if (!com.spaceconquest.engine.economy.EconomicOwnership.transportOperator(state, empire, route.ownerEntityId()) || route.supplyReservation() == null) continue;
            var loading = new LoadAIFacilitySupplyCommand(route.ownerEntityId(), route.id());
            if (loading.validate(state)) return new Plan(List.of(loading), Set.of());
        }
        var policy = LogisticsPolicy.current(); var needs = FacilitySupplyNeeds.shortages(state, empire, policy);
        observations.keySet().retainAll(needs.stream().map(FacilitySupplyNeeds.Need::key).toList());
        var reserved = new HashSet<String>(); var commands = new ArrayList<GameCommand>(); int previews = 0;
        for (var need : needs) {
            var old = observations.get(need.key());
            var observed = old == null || state.turn() > old.lastTurn() + 1 ? new Observation(state.turn(), state.turn(), 1)
                    : old.lastTurn() == state.turn() ? old : new Observation(state.turn(), old.firstTurn(), old.count() + 1);
            observations.put(need.key(), observed);
            for (var fleet : state.fleets().stream().sorted(Comparator.comparingInt((Fleet item) -> empire.id().equals(item.ownerEntityId()) ? 1 : 0).thenComparing(Fleet::id)).toList()) {
                if (!FacilitySupplyShipment.available(state, empire, fleet) || previews >= policy.maximumShipmentPreviews()) continue;
                previews++;
                var shipment = FacilitySupplyShipment.quote(state, empire, need, fleet.ships().getFirst().id(), "ai_supply_preview", policy);
                if (shipment == null) {
                    for (var source : state.commercialHubs().stream().sorted(Comparator.comparing(CommercialHub::id)).toList()) {
                        if (previews >= policy.maximumShipmentPreviews()) break;
                        if (need.hubId().equals(source.id()) || !empire.controlledSystemIds().contains(
                                com.spaceconquest.engine.ship.FleetPositioning.systemForHub(state, source))) continue;
                        previews++;
                        var reposition = FacilitySupplyReposition.quote(state, empire, need, fleet.ships().getFirst().id(), source.id(), "ai_supply_preview", policy);
                        if (reposition == null) continue;
                        reserved.add(fleet.id());
                        if (observed.count() >= policy.shortageObservationTurns()) commands.add(new RepositionAIFacilitySupplyCommand(fleet.ownerEntityId(), need.hubId(),
                                need.materialId(), fleet.ships().getFirst().id(), source.id(), observed.firstTurn()));
                        return new Plan(commands, reserved);
                    }
                    continue;
                }
                reserved.add(fleet.id());
                if (observed.count() >= policy.shortageObservationTurns()) commands.add(new RequestAIFacilitySupplyCommand(fleet.ownerEntityId(), need.hubId(),
                        need.materialId(), fleet.ships().getFirst().id(), observed.firstTurn()));
                return new Plan(commands, reserved);
            }
        }
        return new Plan(commands, reserved);
    }
}
