package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.logistics.TradeRoute;
import com.spaceconquest.engine.ship.ShipRole;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Command to establish an automated cargo trade route between commercial hubs.
 */
public record CreateTradeRouteCommand(
        String ownerEntityId,
        String name,
        String originEntityId,
        String destinationEntityId,
        String materialId,
        double transferAmountPerTurnKg,
        double minSourceThresholdKg,
        double maxDestinationCapacityKg,
        List<String> assignedFreighterIds,
        boolean roaming
) implements GameCommand {

    public CreateTradeRouteCommand(String ownerEntityId, String name, String originEntityId, String destinationEntityId,
                                   String materialId, double transferAmountPerTurnKg, double minSourceThresholdKg,
                                   double maxDestinationCapacityKg, List<String> assignedFreighterIds) {
        this(ownerEntityId, name, originEntityId, destinationEntityId, materialId, transferAmountPerTurnKg,
                minSourceThresholdKg, maxDestinationCapacityKg, assignedFreighterIds, false);
    }

    @Override
    public boolean validate(GameState state) {
        if (state == null || ownerEntityId == null || originEntityId == null
                || destinationEntityId == null || materialId == null
                || assignedFreighterIds == null || assignedFreighterIds.size() != 1) {
            return false;
        }
        if ((!roaming && originEntityId.equals(destinationEntityId))
                || (state.corporations().stream().noneMatch(item -> ownerEntityId.equals(item.id()))
                && state.empires().stream().noneMatch(item -> ownerEntityId.equals(item.id())))
                || state.commercialHubs().stream().noneMatch(hub -> originEntityId.equals(hub.id()))
                || state.commercialHubs().stream().noneMatch(hub -> destinationEntityId.equals(hub.id()))) {
            return false;
        }
        if (!Double.isFinite(transferAmountPerTurnKg) || transferAmountPerTurnKg <= 0
                || !Double.isFinite(minSourceThresholdKg) || minSourceThresholdKg < 0
                || !Double.isFinite(maxDestinationCapacityKg) || maxDestinationCapacityKg <= 0)
            return false;
        String shipId = assignedFreighterIds.getFirst();
        if (shipId == null || state.tradeRoutes().stream().anyMatch(route -> route.isActive()
                && route.assignedFreighterIds().contains(shipId))) return false;
        boolean fleetIsCommitted = state.fleets().stream().filter(fleet ->
                fleet.ships().stream().anyMatch(ship -> shipId.equals(ship.id())))
                .anyMatch(fleet -> state.tradeRoutes().stream().filter(TradeRoute::isActive)
                        .flatMap(route -> route.assignedFreighterIds().stream())
                        .anyMatch(assigned -> fleet.ships().stream()
                                .anyMatch(ship -> assigned.equals(ship.id()))));
        if (fleetIsCommitted) return false;
        return state.fleets().stream().filter(fleet -> ownerEntityId.equals(fleet.ownerEntityId()))
                .flatMap(fleet -> fleet.ships().stream())
                .anyMatch(ship -> shipId.equals(ship.id())
                        && ownerEntityId.equals(ship.ownerEntityId())
                        && state.shipDesigns().stream().anyMatch(design ->
                        design.id().equals(ship.designId())
                                && ShipRole.CARGO_TRANSPORT.equalsIgnoreCase(design.role())));
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) return state;

        String routeId = "route_" + UUID.randomUUID().toString().substring(0, 8);
        String routeName = name != null && !name.isEmpty() ? name : "Automated " + materialId + " Route";

        TradeRoute newRoute = new TradeRoute(
                routeId, routeName, ownerEntityId, originEntityId, destinationEntityId,
                materialId, transferAmountPerTurnKg, minSourceThresholdKg,
                maxDestinationCapacityKg > 0 ? maxDestinationCapacityKg : 50000.0,
                assignedFreighterIds != null ? assignedFreighterIds : List.of(),
                0.0, true
        ).withRoaming(roaming);

        List<TradeRoute> updated = new ArrayList<>(state.tradeRoutes());
        updated.add(newRoute);

        return state.toBuilder()
                .tradeRoutes(updated)
                .build();
    }
}
