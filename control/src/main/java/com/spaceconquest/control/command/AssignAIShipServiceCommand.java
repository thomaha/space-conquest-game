package com.spaceconquest.control.command;

import com.spaceconquest.control.ai.EmpireFleetPlanner;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.logistics.TradeRoute;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Live ownership and commitment gate for civilian trading; military ships await explicit orders. */
public record AssignAIShipServiceCommand(String ownerEntityId, String fleetId) implements GameCommand {
    private GameCommand assignment(GameState state) {
        if (state == null || ownerEntityId == null || fleetId == null) return null;
        if (state.empires().stream().noneMatch(empire -> ownerEntityId.equals(empire.id()))
                && state.corporations().stream().noneMatch(corp -> ownerEntityId.equals(corp.id()))) return null;
        var home = com.spaceconquest.engine.economy.EconomicOwnership.empireFor(state, ownerEntityId);
        if (home == null || !ownerEntityId.equals(home.id()) && !com.spaceconquest.engine.economy.EconomicOwnership.corporationsAllowed(home)) return null;
        var fleet = state.fleets().stream().filter(item -> fleetId.equals(item.id())
                && ownerEntityId.equals(item.ownerEntityId())).findFirst().orElse(null);
        if (fleet == null || ShipRefitPlan.reserved(state, fleet) || fleet.hasInterstellarOrder() || fleet.location().inTransit() || fleet.ships().isEmpty()
                || fleet.ships().stream().anyMatch(ship -> state.shipyardLedger().deployments().containsKey(ship.id()))
                || !"PASSIVE".equalsIgnoreCase(fleet.fleetStance())
                || fleet.ships().stream().anyMatch(ship -> !ownerEntityId.equals(ship.ownerEntityId()))
                || state.tradeRoutes().stream().anyMatch(route -> (route.isActive() || route.onboardKg() > 0
                        || !TradeRoute.LOADING.equals(route.phase())) && route.assignedFreighterIds().stream()
                        .anyMatch(id -> fleet.ships().stream().anyMatch(ship -> id.equals(ship.id()))))) return null;
        var ship = fleet.ships().stream().filter(item -> state.shipDesigns().stream().anyMatch(design ->
                design.id().equals(item.designId()) && ownerEntityId.equals(design.ownerEntityId())
                        && ShipRole.CARGO_TRANSPORT.equals(design.role()) && design.maxCargoMassKg() > 0))
                .sorted(Comparator.comparing(ShipInstance::id)).findFirst().orElse(null);
        if (ship == null) return null;
        var port = state.commercialHubs().stream().filter(hub -> FleetPositioning.atHub(state, fleet, hub)).findFirst().orElse(null);
        String seed = port == null ? null : EmpireFleetPlanner.tradeSeed(state, port);
        if (seed == null) return null;
        var design = state.shipDesigns().stream().filter(item -> item.id().equals(ship.designId())
                && ownerEntityId.equals(item.ownerEntityId())).findFirst().orElseThrow();
        double capacity = state.commercialHubs().stream().mapToDouble(CommercialHub::storageCapacityKg).max().orElse(0);
        return new CreateTradeRouteCommand(ownerEntityId, "AI roaming trader", port.id(), port.id(), seed,
                design.maxCargoMassKg(), 0, capacity, List.of(ship.id()), true);
    }

    @Override public boolean validate(GameState state) {
        var command = assignment(state);
        return command != null && command.validate(state);
    }

    @Override public GameState apply(GameState state) {
        var command = assignment(state);
        return command != null && command.validate(state) ? command.apply(state) : state;
    }
}
