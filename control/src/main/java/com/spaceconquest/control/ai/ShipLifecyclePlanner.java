package com.spaceconquest.control.ai;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.EconomicOwnership;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** One affordable lifecycle decision per affiliated empire, with explicit bounds on quote work. */
public final class ShipLifecyclePlanner {
    private ShipLifecyclePlanner() {}
    public record Plan(List<GameCommand> commands, Set<String> reservedFleetIds) {
        public Plan { commands = List.copyOf(commands); reservedFleetIds = Set.copyOf(reservedFleetIds); }
    }
    public static Plan plan(GameState state, Empire empire, Set<String> reservedFleetIds) {
        for (var order : state.shipConstructionOrders().stream().filter(order -> order.retirement() != null && order.retirement().replacementShipId() != null)
                .limit(ShipLifecyclePolicy.current().maximumShipPreviews()).toList()) {
            if (!EconomicOwnership.transportOperator(state, empire, order.ownerEntityId())) continue;
            if (!new RequestAIShipRetirementCommand(order.ownerEntityId(), order.retirement().shipId()).replacementReady(state, order.retirement().replacementShipId())) {
                var fleet = ShipRetirementPlan.fleet(state, order.retirement().shipId());
                return new Plan(List.of(new CancelShipYardWorkCommand(order.ownerEntityId(), order.id())), fleet == null ? Set.of() : Set.of(fleet.id()));
            }
        }
        for (var source : state.shipyardLedger().replacements().keySet().stream().sorted().limit(ShipLifecyclePolicy.current().maximumShipPreviews()).toList()) {
            var fleet = ShipRetirementPlan.fleet(state, source);
            if (fleet == null || reservedFleetIds.contains(fleet.id()) || !EconomicOwnership.transportOperator(state, empire, fleet.ownerEntityId())) continue;
            var command = new RequestAIShipRetirementCommand(fleet.ownerEntityId(), source);
            if (command.validate(state)) return new Plan(List.of(command), Set.of(fleet.id()));
        }
        int previews = 0; AIShipLifecycleQuote best = null;
        for (var fleet : state.fleets().stream().filter(item -> EconomicOwnership.transportOperator(state, empire, item.ownerEntityId()))
                .sorted(Comparator.comparing(Fleet::id)).toList()) {
            if (reservedFleetIds.contains(fleet.id())) continue;
            for (var ship : fleet.ships().stream().sorted(Comparator.comparing(ShipInstance::id)).toList()) {
                if (++previews > ShipLifecyclePolicy.current().maximumShipPreviews()) break;
                var quote = AIShipLifecycleQuote.inspect(state, fleet.ownerEntityId(), ship.id());
                if (quote != null && (best == null || quote.score() < best.score())) best = quote;
            }
            if (previews >= ShipLifecyclePolicy.current().maximumShipPreviews()) break;
        }
        return best == null ? new Plan(List.of(), Set.of()) : new Plan(List.of(new RequestAIShipLifecycleCommand(best.target().ownerEntityId(), best.shipId())), Set.of(best.fleetId()));
    }
}
