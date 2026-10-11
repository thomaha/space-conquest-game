package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;

/** Friendly rendezvous keeps return intent and joins only after actual funded contact. */
public final class FleetEscortReturn {
    private FleetEscortReturn() {}
    public static boolean ballisticAvailable(Fleet fleet) {
        var local = fleet.location().localFlight();
        return BodyFleetRendezvous.ballisticAvailable(fleet) || local != null && local.interrupted()
                && (local.vectorFlight() == null || local.vectorFlight().trajectory().gravity() == null)
                && !fleet.hasInterstellarOrder() && !fleet.isInWarp() && fleet.flightMotion() == null;
    }
    public static Fleet convoy(GameState state, Fleet fleet) {
        var order = fleet.interceptOrder();
        if (order == null || order.protection() == null || order.protection().stage() != FleetProtectionAssignment.Stage.RETURNING
                || order.protection().convoyFleetId().isBlank()) return null;
        var target = FleetOrganization.find(state, order.protection().convoyFleetId());
        return target != null && fleet.ownerEntityId().equals(target.ownerEntityId())
                && fleet.currentSystemId().equals(target.currentSystemId()) && !target.hasInterstellarOrder() && !target.isInWarp()
                && (target.location().inTransit() || fleet.location().inTransit()
                && target.location().current().kind() == FleetLocation.Kind.ORBIT
                && OrbitalTravel.parking(state, target.currentSystemId(), target.location().current()) != null) ? target : null;
    }
    public static FleetLocation.Site site(Fleet fleet) {
        return fleet.location().inTransit() ? fleet.location().destination() : fleet.location().current();
    }
    public static LocalTravel.Plan plan(GameState state, Fleet fleet, Fleet convoy) {
        if (!FleetReturnTravel.available(fleet) && !ballisticAvailable(fleet)) return null;
        for (int attempt = 0; attempt < 8; attempt++) {
            // A maximum-speed approach can use nearly all available fuel; hold more for the convoy's later burns.
            var policy = fleet.fuelPolicy(); double extra = .05 * attempt;
            var reserved = fleet.withFuelPolicy(new FleetFuelPolicy(Math.min(.99, policy.routineFraction() + extra),
                    Math.min(.99, policy.elevatedFraction() + extra), Math.min(.99, policy.wartimeFraction() + extra)));
            var plan = BodyFleetRendezvous.plan(state, reserved, convoy);
            if (plan == null && convoy.location().inTransit()) plan = convoy.location().orbitalFlight() != null ? OrbitalRendezvousPlanner.plan(state, reserved, convoy)
                    : MovingFleetRendezvous.plan(state, reserved, convoy);
            if (plan == null || !ShipPowerForecast.ready(ShipPowerForecast.departure(state, reserved, site(convoy), plan, null))) continue;
            var projected = EscortFlightContinuation.projected(convoy, Math.ceil(plan.days()) * 86400);
            if (projected == null) continue;
            var arrival = LocalTravel.arrivalPreview(state, fleet, site(convoy), plan);
            if (EscortFlightContinuation.align(state.withTurn(state.turn() + (long) Math.ceil(plan.days())), arrival, projected) != null) return plan;
        }
        return null;
    }
    public static Fleet depart(Fleet fleet, Fleet convoy, LocalTravel.Plan plan) {
        var order = fleet.interceptOrder();
        return MovingFleetRendezvous.depart(fleet.withInterceptOrder(new FleetInterceptOrder(order.ownerEntityId(), convoy.id(),
                order.systemId(), order.protection())), convoy, plan);
    }
    public static GameState finishContacts(GameState state) {
        var result = state;
        for (var original : state.fleets()) {
            var fleet = FleetOrganization.find(result, original.id());
            if (fleet == null) continue;
            var convoy = convoy(result, fleet);
            if (convoy == null || !MovingFleetRendezvous.physicalMatches(result, fleet, convoy)) continue;
            var aligned = EscortFlightContinuation.align(result, fleet, convoy);
            if (aligned != null) result = FleetProtectionReturn.restore(FleetReturnTravel.replace(result, aligned), aligned);
        }
        return result;
    }
}
