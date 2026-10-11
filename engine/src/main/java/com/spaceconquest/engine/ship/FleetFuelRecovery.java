package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.logistics.*;
import java.util.*;

/** Reachable stocked bases and nearby reserved fuel transports; every departure is physically funded. */
public final class FleetFuelRecovery {
    private FleetFuelRecovery() {}
    public static GameState recover(GameState state, Fleet fleet, FleetLocation.Site destination) {
        var opening = state; var startingFleet = fleet;
        var ports = state.commercialHubs().stream().filter(port -> ownedSafePort(opening, startingFleet, port))
                .sorted(Comparator.comparingDouble((CommercialHub port) -> FleetThreatAssessment.distance(opening, startingFleet,
                        FleetLocation.at(FleetPositioning.hubSite(opening, port)))).thenComparing(CommercialHub::id)).toList();
        for (var port : ports) {
            if (FleetPositioning.atHub(state, fleet, port)) {
                var refilled = TradeLegReadiness.refillAtPort(state, fleet, port);
                var supplied = FleetOrganization.find(refilled, fleet.id());
                var returnPlan = FleetReturnTravel.plan(refilled, supplied, destination);
                if (returnPlan != null) {
                    var convoy = FleetEscortReturn.convoy(refilled, supplied);
                    return FleetReturnTravel.replace(refilled, convoy == null ? LocalTravel.depart(supplied, destination, returnPlan)
                            : FleetEscortReturn.depart(supplied, convoy, returnPlan));
                }
                state = refilled; fleet = supplied;
                continue;
            }
            var plan = FleetReturnTravel.localPlan(state, fleet, FleetPositioning.hubSite(state, port));
            if (plan == null) continue;
            var arrival = LocalTravel.arrivalPreview(state, fleet, FleetPositioning.hubSite(state, port), plan);
            var projected = FleetReturnTravel.replace(state.withTurn(state.turn() + (long) Math.ceil(plan.days())), arrival);
            var refilled = TradeLegReadiness.refillAtPort(projected, arrival, port);
            if (!FleetReturnTravel.ready(refilled, FleetOrganization.find(refilled, fleet.id()), destination)) continue;
            var outbound = FleetReturnTravel.stage(fleet, FleetProtectionAssignment.Stage.REFUELING, port.id());
            return FleetReturnTravel.replace(state, LocalTravel.depart(outbound, FleetPositioning.hubSite(state, port), plan));
        }
        return requestTransport(state, fleet, destination);
    }
    private static boolean ownedSafePort(GameState state, Fleet fleet, CommercialHub port) {
        if (!fleet.currentSystemId().equals(FleetPositioning.systemForHub(state, port))) return false;
        var site = FleetPositioning.hubSite(state, port);
        if (site == null) return false;
        boolean owned = site.kind() == FleetLocation.Kind.DOCKED
                ? state.orbitalStations().stream().anyMatch(station -> site.entityId().equals(station.id()) && station.isOperational()
                    && fleet.ownerEntityId().equals(station.ownerEntityId()))
                : state.empires().stream().anyMatch(empire -> fleet.ownerEntityId().equals(empire.id()) && empire.controlledSystemIds().contains(fleet.currentSystemId()));
        if (!owned) return false;
        return state.fleets().stream().filter(enemy -> FleetInterceptProcessor.contact(state, fleet, enemy))
                .noneMatch(enemy -> FleetThreatAssessment.distance(state, enemy, FleetLocation.at(site)) <= FleetThreatAssessment.THREAT_RADIUS_METERS);
    }
    private static GameState requestTransport(GameState state, Fleet receiver, FleetLocation.Site destination) {
        if (state.fleets().stream().anyMatch(fleet -> fleet.interceptOrder() != null && fleet.interceptOrder().protection() != null
                && fleet.interceptOrder().protection().stage() == FleetProtectionAssignment.Stage.SUPPLYING
                && receiver.id().equals(fleet.interceptOrder().protection().serviceTargetId()))) return state;
        var suppliers = state.fleets().stream().filter(fleet -> eligible(state, fleet, receiver))
                .sorted(Comparator.comparingDouble((Fleet fleet) -> FleetThreatAssessment.distance(state, fleet, receiver.location())).thenComparing(Fleet::id)).limit(8).toList();
        for (var supplier : suppliers) {
            var plan = ShipSupplyTransfer.coLocated(supplier, receiver) ? null : FleetReturnTravel.plan(state, supplier, receiver.location().current());
            if (plan == null && !ShipSupplyTransfer.coLocated(supplier, receiver)) continue;
            var arrival = plan == null ? supplier : LocalTravel.arrivalPreview(state, supplier, receiver.location().current(), plan);
            var projected = FleetReturnTravel.replace(state.withTurn(state.turn() + (plan == null ? 0 : (long) Math.ceil(plan.days()))), arrival);
            var supplied = transfer(projected, arrival, receiver, destination);
            if (!FleetReturnTravel.ready(supplied, FleetOrganization.find(supplied, receiver.id()), destination)
                    || !FleetReturnTravel.ready(supplied, FleetOrganization.find(supplied, supplier.id()), supplier.location().current())) continue;
            var assignment = new FleetProtectionAssignment(null, "", supplier.id(), supplier.location().current(),
                    FleetProtectionAssignment.Stage.SUPPLYING, receiver.id());
            var ordered = supplier.withInterceptOrder(new FleetInterceptOrder(supplier.ownerEntityId(), receiver.id(), supplier.currentSystemId(), assignment));
            var committed = FleetReturnTravel.replace(state, plan == null ? ordered : LocalTravel.depart(ordered, receiver.location().current(), plan));
            return plan == null ? deliver(committed, ordered) : committed;
        }
        return state;
    }
    private static boolean eligible(GameState state, Fleet supplier, Fleet receiver) {
        return !supplier.id().equals(receiver.id()) && receiver.ownerEntityId().equals(supplier.ownerEntityId())
                && receiver.currentSystemId().equals(supplier.currentSystemId()) && FleetReturnTravel.available(supplier)
                && !supplier.location().inTransit() && supplier.interceptOrder() == null && supplier.guardOrder() == null && supplier.escortOrder() == null
                && FleetThreatAssessment.distance(state, supplier, receiver.location()) <= FleetThreatAssessment.THREAT_RADIUS_METERS
                && supplier.ships().stream().allMatch(ship -> ship.passengerCount() == 0 && ship.supplyState().orders().isEmpty())
                && supplier.ships().stream().anyMatch(ship -> {
                    var design = FleetSupplySimulation.design(state, ship);
                    return design != null && design.equippedModuleIds().contains(ShipSupplyCatalog.TRANSFER_PUMP) && ship.supplyFuelMassKg() > 0;
                }) && state.tradeRoutes().stream().noneMatch(route -> (route.isActive() || route.onboardKg() > 0)
                    && TradeEscape.associated(route, supplier));
    }
    public static GameState deliver(GameState state, Fleet supplier) {
        var assignment = supplier.interceptOrder().protection();
        var receiver = FleetOrganization.find(state, assignment.serviceTargetId());
        if (receiver == null || receiver.interceptOrder() == null || receiver.interceptOrder().protection() == null)
            return FleetReturnTravel.replace(state, FleetReturnTravel.stage(supplier, FleetProtectionAssignment.Stage.RETURNING, ""));
        if (!ShipSupplyTransfer.coLocated(supplier, receiver))
            return FleetReturnTravel.replace(state, FleetReturnTravel.stage(supplier, FleetProtectionAssignment.Stage.RETURNING, ""));
        var destination = FleetProtectionReturn.destination(state, receiver);
        if (destination == null) return state;
        var transferred = transfer(state, supplier, receiver, destination);
        var actualSupplier = FleetOrganization.find(transferred, supplier.id());
        if (!FleetReturnTravel.ready(transferred, actualSupplier, assignment.origin())) return state;
        return FleetReturnTravel.replace(transferred, FleetReturnTravel.stage(actualSupplier, FleetProtectionAssignment.Stage.RETURNING, ""));
    }
    private static GameState transfer(GameState state, Fleet supplier, Fleet receiver, FleetLocation.Site destination) {
        var result = state;
        for (var donor : supplier.ships()) for (var ship : receiver.ships())
            result = FleetReturnFuel.supply(result, FleetOrganization.find(result, receiver.id()), donor.id(), ship,
                    ShipSupplyTransfer.Source.SUPPLY_TANK, destination);
        return result;
    }
}
