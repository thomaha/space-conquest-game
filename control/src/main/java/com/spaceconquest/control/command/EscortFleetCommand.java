package com.spaceconquest.control.command;

import com.spaceconquest.control.ai.EmpireFleetPlanner;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.*;

/** Joins an idle military fleet to an owned fleet's physical convoy. */
public record EscortFleetCommand(String ownerEntityId, String escortFleetId, String targetFleetId) implements GameCommand {
    @Override public boolean validate(GameState state) {
        if (state == null || ownerEntityId == null || escortFleetId == null || targetFleetId == null) return false;
        var source = FleetOrganization.find(state, escortFleetId);
        var target = FleetOrganization.find(state, targetFleetId);
        if (source == null || target == null || source.escortOrder() != null || target.escortOrder() != null
                || source.guardOrder() != null || target.guardOrder() != null
                || source.interceptOrder() != null || target.interceptOrder() != null
                || !idle(source) || !idle(target) || source.ships().isEmpty()
                || source.ships().stream().anyMatch(ship -> state.shipDesigns().stream().noneMatch(design ->
                ship.designId().equals(design.id()) && ownerEntityId.equals(design.ownerEntityId())
                        && EmpireFleetPlanner.combat(design.role())))
                || state.tradeRoutes().stream().anyMatch(route -> (route.isActive() || route.onboardKg() > 0
                || !com.spaceconquest.engine.logistics.TradeRoute.LOADING.equals(route.phase()))
                && route.assignedFreighterIds().stream().anyMatch(id -> source.ships().stream().anyMatch(ship -> id.equals(ship.id()))))) return false;
        return new MergeFleetsCommand(ownerEntityId, escortFleetId, targetFleetId).validate(state);
    }

    private static boolean idle(Fleet fleet) {
        return !fleet.hasInterstellarOrder() && !fleet.isInWarp() && !fleet.location().inTransit()
                && fleet.flightMotion() == null && fleet.interstellarMode().isBlank();
    }

    @Override public GameState apply(GameState state) {
        if (!validate(state)) return state;
        var escort = FleetOrganization.find(state, escortFleetId);
        var target = FleetOrganization.find(state, targetFleetId);
        var order = new FleetEscortOrder(ownerEntityId, escort.id(), escort.name(), target.fleetStance(),
                escort.ships().stream().map(ShipInstance::id).toList());
        var joined = new MergeFleetsCommand(ownerEntityId, escortFleetId, targetFleetId).apply(state);
        var stationed = new SetFleetStanceCommand(targetFleetId, "ESCORT").apply(joined);
        return stationed.withFleets(stationed.fleets().stream().map(fleet -> targetFleetId.equals(fleet.id())
                ? fleet.withEscortOrder(order) : fleet).toList());
    }
}
