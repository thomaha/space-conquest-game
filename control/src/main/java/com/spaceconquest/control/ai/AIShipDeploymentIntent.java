package com.spaceconquest.control.ai;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.EconomicOwnership;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Captures the concrete need behind a build without creating assets or orders. */
public final class AIShipDeploymentIntent {
    private AIShipDeploymentIntent() {}
    public static ShipDeploymentIntent choose(GameState state, ShipDesign design, String system, String yard) {
        var home = EconomicOwnership.empireFor(state, design.ownerEntityId());
        if (home == null || !home.controlledSystemIds().contains(system) || !design.ownerEntityId().equals(home.id()) && !EconomicOwnership.corporationsAllowed(home)) return null;
        if (ShipRole.CARGO_TRANSPORT.equals(design.role())) {
            var need = FacilitySupplyNeeds.shortages(state, home, LogisticsPolicy.current()).stream().findFirst().orElse(null);
            return need == null ? intent(state, design, ShipDeploymentIntent.Kind.TRADE, "", "")
                    : intent(state, design, ShipDeploymentIntent.Kind.SUPPLY, need.hubId(), need.materialId());
        }
        if (!EmpireFleetPlanner.combat(design.role()) || !design.ownerEntityId().equals(home.id())) return null;
        return StrategicDeploymentPlanner.choose(state, design, system, yard);
    }
    public static boolean idle(Fleet fleet) {
        return FleetReturnTravel.available(fleet) && !fleet.location().inTransit() && fleet.guardOrder() == null && fleet.escortOrder() == null
                && fleet.interceptOrder() == null && "PASSIVE".equalsIgnoreCase(fleet.fleetStance()) && !fleet.ships().isEmpty()
                && fleet.ships().stream().allMatch(ship -> ship.currentHullHealth() > 0);
    }
    private static ShipDeploymentIntent intent(GameState state, ShipDesign design, ShipDeploymentIntent.Kind kind, String target, String material) {
        return new ShipDeploymentIntent(design.ownerEntityId(), kind, target, material, state.turn(), "Planned " + kind.name().toLowerCase(java.util.Locale.ROOT));
    }
    public static GameState capture(GameState state, String ownerId) {
        return state.withShipConstructionOrders(state.shipConstructionOrders().stream().map(order -> {
            if (order.refit() != null || order.retirement() != null || order.deployment() != null || !ownerId.equals(order.ownerEntityId())) return order;
            var design = state.shipDesigns().stream().filter(item -> order.designId().equals(item.id()) && order.ownerEntityId().equals(item.ownerEntityId())).findFirst().orElse(null);
            return design == null ? order : order.withDeployment(choose(state, design, order.systemId(), order.yardBodyId()));
        }).toList());
    }
}
