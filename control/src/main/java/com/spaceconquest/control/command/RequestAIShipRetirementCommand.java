package com.spaceconquest.control.command;

import com.spaceconquest.control.ai.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.EconomicOwnership;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Retirement only after the saved replacement is operational and removing the source adds no coverage deficit. */
public record RequestAIShipRetirementCommand(String ownerId, String shipId) implements GameCommand {
    @Override public boolean validate(GameState state) {
        return replacementReady(state, state.shipyardLedger().replacements().get(shipId)) && ShipRetirementQuote.inspect(state, ownerId, shipId).ready();
    }
    public boolean replacementReady(GameState state, String replacementId) {
        var source = ShipRetirementPlan.fleet(state, shipId);
        var home = EconomicOwnership.empireFor(state, ownerId);
        if (source == null || home == null || !ownerId.equals(source.ownerEntityId()) || !AIShipDeploymentIntent.idle(source)
                || !ShipReplacementPlan.operational(state, shipId, replacementId, ShipRetirementPolicy.current().minimumReplacementHullFraction())) return false;
        var old = FleetSupplySimulation.design(state, source.ships().stream().filter(item -> shipId.equals(item.id())).findFirst().orElseThrow());
        return !EmpireFleetPlanner.combat(old.role()) || coverage(state, home);
    }
    private boolean coverage(GameState state, Empire home) {
        var before = new HashMap<String, Integer>();
        EmpireFleetNeeds.assess(state, home).forEach(need -> before.merge(need.systemId() + "/" + need.kind() + "/" + need.targetId(), need.missingShips(), Integer::sum));
        var without = state.withFleets(state.fleets().stream().map(fleet -> fleet.withShips(fleet.ships().stream().filter(ship -> !shipId.equals(ship.id())).toList())).filter(fleet -> !fleet.ships().isEmpty()).toList());
        return EmpireFleetNeeds.assess(without, home).stream().allMatch(need -> need.missingShips() <= before.getOrDefault(need.systemId() + "/" + need.kind() + "/" + need.targetId(), 0));
    }
    @Override public GameState apply(GameState state) {
        if (!validate(state)) return state;
        var queued = new QueueShipRetirementCommand(ownerId, shipId).apply(state);
        return queued.withShipConstructionOrders(queued.shipConstructionOrders().stream().map(order -> order.retirement() != null
                && shipId.equals(order.retirement().shipId()) ? order.withRetirement(order.retirement().withReplacement(state.shipyardLedger().replacements().get(shipId),
                        ShipRetirementPolicy.current().minimumReplacementHullFraction())) : order).toList());
    }
}
