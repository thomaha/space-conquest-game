package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.*;
import java.util.Set;

/** Explicit hold order at an owned object's current site. No movement or sensor search is implied. */
public record GuardObjectCommand(String ownerEntityId, String fleetId,
                                FleetGuardOrder.TargetType targetType, String targetId) implements GameCommand {
    @Override public boolean validate(GameState state) {
        if (state == null || ownerEntityId == null || fleetId == null || targetType == null || targetId == null) return false;
        var fleet = state.fleets().stream().filter(item -> fleetId.equals(item.id())
                && ownerEntityId.equals(item.ownerEntityId())).findFirst().orElse(null);
        if (fleet == null || ShipRefitPlan.reserved(state, fleet) || fleet.ships().isEmpty()
                || fleet.ships().stream().anyMatch(ship -> !ownerEntityId.equals(ship.ownerEntityId()))
                || fleet.ships().stream().noneMatch(ship -> state.shipDesigns().stream().anyMatch(design ->
                ship.designId().equals(design.id()) && ownerEntityId.equals(design.ownerEntityId())
                        && design.role() != null && Set.of(ShipRole.COMBAT_SHIP, ShipRole.ESCORT, ShipRole.FIGHTER,
                        ShipRole.CARRIER_SHIP).contains(design.role())))
                || state.tradeRoutes().stream().anyMatch(route -> (route.isActive() || route.onboardKg() > 0
                || !com.spaceconquest.engine.logistics.TradeRoute.LOADING.equals(route.phase()))
                && route.assignedFreighterIds().stream().anyMatch(id -> fleet.ships().stream().anyMatch(ship -> id.equals(ship.id()))))) return false;
        return new FleetGuardOrder(targetType, targetId, fleet.currentSystemId(), fleet.location().current()).validTarget(state, fleet);
    }

    @Override public GameState apply(GameState state) {
        if (!validate(state)) return state;
        return state.withFleets(state.fleets().stream().map(fleet -> fleetId.equals(fleet.id())
                ? fleet.withGuardOrder(new FleetGuardOrder(targetType, targetId, fleet.currentSystemId(), fleet.location().current()))
                : fleet).toList());
    }
}
