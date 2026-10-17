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
        return plan(state, empire, Set.of());
    }
    public static List<GameCommand> plan(GameState state, Empire empire, Set<String> reservedFleetIds) {
        var commands = new ArrayList<GameCommand>();
        for (var fleet : state.fleets().stream().filter(item -> com.spaceconquest.engine.economy.EconomicOwnership.transportOperator(state, empire, item.ownerEntityId()))
                .sorted(Comparator.comparing(Fleet::id)).toList()) {
            if (reservedFleetIds.contains(fleet.id()) || fleet.ships().stream().anyMatch(ship -> ShipReplacementPlan.pending(state, ship.id()))) continue;
            if (com.spaceconquest.engine.ship.ShipRefitPlan.reserved(state, fleet) || fleet.ships().stream().anyMatch(ship -> state.shipyardLedger().deployments().containsKey(ship.id()))) continue;
            var service = new AssignAIShipServiceCommand(fleet.ownerEntityId(), fleet.id());
            if (!reservedFleetIds.contains(fleet.id()) && service.validate(state)) commands.add(service);
            var combatService = new ServiceAICombatFleetCommand(empire.id(), fleet.id());
            if (combatService.validate(state)) commands.add(combatService);
        }
        for (var design : state.shipDesigns().stream().filter(item -> empire.id().equals(item.ownerEntityId()))
                .sorted(Comparator.comparingInt((ShipDesign item) -> combat(item.role()) ? 0 : 1)
                        .thenComparing(ShipDesign::id)).toList()) {
            if (combat(design.role())) continue;
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
        if (ShipRole.EXPLORER.equals(design.role())) return com.spaceconquest.engine.industry.OrbitalSurveyOperations.definition(state, empire.id()) != null
                && !com.spaceconquest.engine.industry.OrbitalSurveyOperations.targets(state, empire.id()).isEmpty()
                && SensorModuleCatalog.profile(design).rangeKm() > 0 && design.powerProfile() != null
                && state.fleets().stream().filter(f -> empire.id().equals(f.ownerEntityId()))
                .flatMap(f -> f.ships().stream()).noneMatch(s -> s.currentHullHealth() > 0 && state.shipDesigns().stream()
                        .anyMatch(d -> d.id().equals(s.designId()) && ShipRole.EXPLORER.equals(d.role()) && SensorModuleCatalog.profile(d).rangeKm() > 0));
        boolean military = combat(design.role());
        if (military) {
            if (state.diplomaticRelations().stream().noneMatch(relation -> DiplomacyProcessor.TOTAL_WAR.equals(relation.tier())
                    && (empire.id().equals(relation.empireAId()) || empire.id().equals(relation.empireBId())))) return false;
        } else if (!ShipRole.CARGO_TRANSPORT.equals(design.role()) || design.maxCargoMassKg() <= 0
                || !FacilitySupplyNeeds.shortages(state, empire, LogisticsPolicy.current()).isEmpty()
                || FacilityTransportBuildQuote.pending(state, empire)
                || state.commercialHubs().stream().noneMatch(hub -> tradeSeed(state, hub) != null)) return false;
        return state.fleets().stream().filter(fleet -> military ? empire.id().equals(fleet.ownerEntityId())
                        : com.spaceconquest.engine.economy.EconomicOwnership.transportOperator(state, empire, fleet.ownerEntityId()))
                .flatMap(fleet -> fleet.ships().stream()).filter(ship -> ship.currentHullHealth() > 0)
                .noneMatch(ship -> state.shipDesigns().stream().anyMatch(owned -> owned.id().equals(ship.designId())
                        && ship.ownerEntityId().equals(owned.ownerEntityId())
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
