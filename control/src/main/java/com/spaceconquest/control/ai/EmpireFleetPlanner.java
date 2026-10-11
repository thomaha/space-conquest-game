package com.spaceconquest.control.ai;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.governance.DiplomacyProcessor;
import com.spaceconquest.engine.industry.IndustryMarketProcessor;
import com.spaceconquest.engine.logistics.HubReplenishment;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Small service bootstrap using owned blueprints and ordinary tick-validated commands. */
public final class EmpireFleetPlanner {
    private EmpireFleetPlanner() {}

    public static List<GameCommand> plan(GameState state, Empire empire) {
        var commands = new ArrayList<GameCommand>();
        for (var fleet : state.fleets().stream().filter(item -> empire.id().equals(item.ownerEntityId()))
                .sorted(Comparator.comparing(Fleet::id)).toList()) {
            var service = new AssignAIShipServiceCommand(empire.id(), fleet.id());
            if (service.validate(state)) commands.add(service);
        }
        for (var design : state.shipDesigns().stream().filter(item -> empire.id().equals(item.ownerEntityId()))
                .sorted(Comparator.comparingInt((ShipDesign item) -> combat(item.role()) ? 0 : 1)
                        .thenComparing(ShipDesign::id)).toList()) {
            for (String system : empire.controlledSystemIds().stream().sorted().toList()) {
                var build = new RequestAIShipBuildCommand(empire.id(), design.id(), system);
                if (build.validate(state)) {
                    commands.add(build);
                    return List.copyOf(commands);
                }
            }
        }
        return List.copyOf(commands);
    }

    public static boolean combat(String role) {
        return role != null && Set.of(ShipRole.COMBAT_SHIP, ShipRole.FIGHTER, ShipRole.ESCORT, ShipRole.CARRIER_SHIP).contains(role);
    }

    public static boolean needsShip(GameState state, Empire empire, ShipDesign design) {
        boolean military = combat(design.role());
        if (military) {
            if (state.diplomaticRelations().stream().noneMatch(relation -> DiplomacyProcessor.TOTAL_WAR.equals(relation.tier())
                    && (empire.id().equals(relation.empireAId()) || empire.id().equals(relation.empireBId())))) return false;
        } else if (!ShipRole.CARGO_TRANSPORT.equals(design.role()) || design.maxCargoMassKg() <= 0
                || state.commercialHubs().stream().noneMatch(hub -> tradeSeed(state, hub) != null)) return false;
        return state.fleets().stream().filter(fleet -> empire.id().equals(fleet.ownerEntityId()))
                .flatMap(fleet -> fleet.ships().stream()).filter(ship -> empire.id().equals(ship.ownerEntityId()))
                .noneMatch(ship -> state.shipDesigns().stream().anyMatch(owned -> owned.id().equals(ship.designId())
                        && empire.id().equals(owned.ownerEntityId())
                        && (military ? combat(owned.role()) : ShipRole.CARGO_TRANSPORT.equals(owned.role()))));
    }

    public static String tradeSeed(GameState state, CommercialHub source) {
        if (!portAvailable(state, source)) return null;
        return source.activeOrders().values().stream().filter(order -> order.supplyKg() > 0
                        && Double.isFinite(order.pricePerKg()) && order.pricePerKg() >= 0)
                .sorted(Comparator.comparing(MarketOrder::resourceId)).filter(seller ->
                        state.commercialHubs().stream().anyMatch(target -> !target.id().equals(source.id())
                                && portAvailable(state, target) && target.activeOrders().containsKey(seller.resourceId())
                                && Double.isFinite(target.activeOrders().get(seller.resourceId()).pricePerKg())
                                && target.activeOrders().get(seller.resourceId()).pricePerKg() * IndustryMarketProcessor.WHOLESALE_SHARE > seller.pricePerKg()
                                && HubReplenishment.requestedKg(state, target, seller.resourceId(), null) > 0
                                && HubReplenishment.storageRoomKg(state, target, null) > 0
                                && HubReplenishment.buyerCashAvailable(state, target, null) > 0))
                .map(MarketOrder::resourceId).findFirst().orElse(null);
    }

    private static boolean portAvailable(GameState state, CommercialHub hub) {
        return FleetPositioning.hubSite(state, hub) != null && state.orbitalStations().stream()
                .noneMatch(station -> station.id().equals(hub.entityId()) && !station.isOperational());
    }
}
