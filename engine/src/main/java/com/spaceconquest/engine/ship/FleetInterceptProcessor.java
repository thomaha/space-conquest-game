package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.galaxy.SensorCoverage;
import com.spaceconquest.engine.governance.DiplomacyProcessor;
import com.spaceconquest.engine.logistics.TradeRoute;
import java.util.Set;

/** Tick-owned site pursuit using current contacts and ordinary funded travel. */
public final class FleetInterceptProcessor {
    private FleetInterceptProcessor() {}

    public static boolean militaryAvailable(GameState state, Fleet fleet) {
        return fleet != null && !ShipRefitPlan.reserved(state, fleet) && !fleet.ships().isEmpty() && fleet.guardOrder() == null && fleet.escortOrder() == null
                && fleet.ships().stream().allMatch(ship -> fleet.ownerEntityId().equals(ship.ownerEntityId())
                && ship.currentHullHealth() > 0 && ship.passengerCount() == 0 && ship.supplyState().orders().isEmpty()
                && (ship.powerState() == null || ship.powerState().rescueStatus() == null
                || ship.powerState().rescueStatus().phase() != RescueStatus.Phase.APPROACHING)
                && state.shipDesigns().stream().anyMatch(design -> ship.designId().equals(design.id())
                && fleet.ownerEntityId().equals(design.ownerEntityId()) && design.role() != null
                && PropulsionCatalog.mainDrive(design.equippedModuleIds()) != null
                && Set.of(ShipRole.COMBAT_SHIP, ShipRole.ESCORT, ShipRole.FIGHTER, ShipRole.CARRIER_SHIP).contains(design.role())))
                && state.tradeRoutes().stream().noneMatch(route -> (route.isActive() || route.onboardKg() > 0
                || !TradeRoute.LOADING.equals(route.phase())) && route.assignedFreighterIds().stream()
                .anyMatch(id -> fleet.ships().stream().anyMatch(ship -> id.equals(ship.id()))));
    }

    public static boolean contact(GameState state, Fleet fleet, Fleet target) {
        return target != null && !fleet.id().equals(target.id()) && !target.ships().isEmpty()
                && fleet.currentSystemId().equals(target.currentSystemId())
                && !target.hasInterstellarOrder() && !target.isInWarp()
                && DiplomacyProcessor.TOTAL_WAR.equalsIgnoreCase(new DiplomacyProcessor().getDiplomaticTier(
                fleet.ownerEntityId(), target.ownerEntityId(), state.diplomaticRelations()))
                && SensorCoverage.detects(state, fleet.ownerEntityId(), target);
    }

    public static GameState advance(GameState state) {
        var prepared = FleetRepairOrders.advance(state);
        var pursued = prepared.withFleets(prepared.fleets().stream().map(fleet -> advance(prepared, fleet)).toList());
        return FleetProtectionReturn.advance(pursued);
    }

    static Fleet advance(GameState state, Fleet fleet) {
        var order = fleet.interceptOrder();
        if (order == null) return fleet;
        if (order.protection() != null && order.protection().stage() != FleetProtectionAssignment.Stage.PURSUING) return fleet;
        var target = FleetOrganization.find(state, order.targetFleetId());
        if (!militaryAvailable(state, fleet) || !contact(state, fleet, target)) return finish(fleet);
        // A committed journey continues physically after cancellation. No mid-flight teleport or redirection.
        if (fleet.location().inTransit() && !BodyFleetRendezvous.ballisticAvailable(fleet) || fleet.hasInterstellarOrder()
                || fleet.isInWarp() || fleet.flightMotion() != null || !fleet.interstellarMode().isBlank()
                || !fleet.location().inTransit() && FleetCombatPositioning.holds(state, fleet, target)
                || !target.location().inTransit() && fleet.location().isAt(target.location().current())) return fleet;
        if (target.location().inTransit()) {
            var standOff = BodyFleetStandOff.plan(state, fleet, target);
            if (standOff != null) return MovingFleetRendezvous.depart(fleet, target, standOff);
            var rendezvous = BodyFleetRendezvous.plan(state, fleet, target);
            if (rendezvous == null) rendezvous = target.location().orbitalFlight() != null ? OrbitalRendezvousPlanner.plan(state, fleet, target)
                    : MovingFleetRendezvous.plan(state, fleet, target);
            return rendezvous == null ? fleet : MovingFleetRendezvous.depart(fleet, target, rendezvous);
        }
        var destination = target.location().current();
        if (fleet.location().current().kind() == FleetLocation.Kind.SURFACE
                || destination.kind() == FleetLocation.Kind.SURFACE) return fleet;
        var standOff = FleetStandOffApproach.plan(state, fleet, target);
        if (standOff != null) return LocalTravel.depart(fleet, standOff.destination(), standOff.plan());
        if (ShipWeaponRange.reaches(state, fleet, target)) return fleet;
        var plan = LocalTravel.plan(state, fleet, destination);
        if (plan == null || plan.orbital() == null && plan.physical() == null
                || !ShipPowerForecast.ready(ShipPowerForecast.departure(state, fleet, destination, plan, null)))
            return fleet;
        return LocalTravel.depart(fleet, destination, plan);
    }

    /** Complete a site rendezvous after the ordinary encounter resolver has had its turn. */
    public static GameState finishContacts(GameState state) {
        state = FleetEscortReturn.finishContacts(state);
        var contacts = state;
        return state.withFleets(state.fleets().stream().map(fleet -> {
            if (MovingFleetRendezvous.finished(fleet)) {
                var released = MovingFleetRendezvous.release(fleet);
                return fleet.interceptOrder() != null && fleet.interceptOrder().protection() != null
                        ? finish(released.withInterceptOrder(fleet.interceptOrder())) : released;
            }
            if (fleet.interceptOrder() == null) return fleet;
            if (fleet.interceptOrder().protection() != null
                    && fleet.interceptOrder().protection().stage() != FleetProtectionAssignment.Stage.PURSUING) return fleet;
            var target = FleetOrganization.find(contacts, fleet.interceptOrder().targetFleetId());
            return target == null || !contact(contacts, fleet, target)
                    || !target.location().inTransit() && fleet.currentSystemId().equals(target.currentSystemId())
                    && fleet.location().isAt(target.location().current())
                    || !fleet.location().inTransit() && FleetCombatPositioning.holds(contacts, fleet, target) ? finish(fleet) : fleet;
        }).toList());
    }

    private static Fleet finish(Fleet fleet) {
        var order = fleet.interceptOrder();
        return fleet.withInterceptOrder(order.protection() == null ? null
                : order.withProtection(order.protection().at(FleetProtectionAssignment.Stage.RETURNING, "")));
    }
}
