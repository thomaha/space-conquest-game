package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import java.util.Comparator;
import java.util.List;

/** Pure defensive interception preview; a failed funded approach leaves protection assignments intact. */
public final class FleetProtectionIntercept {
    private FleetProtectionIntercept() {}

    public static GameState preview(GameState state, Fleet protectedFleet, FleetThreatAssessment.Assessment assessment) {
        if (assessment.action() != FleetThreatAssessment.Action.HOLD || protectedFleet.location().inTransit()
                || protectedFleet.hasInterstellarOrder() || protectedFleet.isInWarp() || protectedFleet.interceptOrder() != null
                || protectedFleet.flightMotion() != null || !protectedFleet.interstellarMode().isBlank()) return null;
        var prepared = prepare(state, protectedFleet);
        if (prepared == null) return null;
        String interceptorId = protectedFleet.escortOrder() == null ? protectedFleet.id() : protectedFleet.escortOrder().escortFleetId();
        var interceptor = FleetOrganization.find(prepared, interceptorId);
        if (!FleetInterceptProcessor.militaryAvailable(prepared, interceptor)) return null;
        double strength = FleetThreatAssessment.strength(prepared, List.of(interceptor));
        double potentialHostile = FleetThreatAssessment.strength(state, state.fleets().stream()
                .filter(target -> assessment.hostileFleetIds().contains(target.id())).toList());
        if (strength <= 0 || strength < potentialHostile) return null;
        var candidates = assessment.hostileFleetIds().stream().map(id -> FleetOrganization.find(state, id))
                .filter(target -> FleetInterceptProcessor.contact(state, protectedFleet, target))
                .sorted(Comparator.comparingDouble((Fleet target) -> FleetThreatAssessment.distance(state, target, protectedFleet.location()))
                        .thenComparing(Fleet::id)).limit(8).toList();
        if (assessment.hostileFleetIds().stream().map(id -> FleetOrganization.find(state, id))
                .anyMatch(target -> ShipWeaponRange.reaches(prepared, interceptor, target))) return null;
        for (var target : candidates) {
            var assignment = new FleetProtectionAssignment(protectedFleet.guardOrder(), protectedFleet.escortOrder() == null
                    ? "" : protectedFleet.id(), interceptorId, protectedFleet.location().current(), FleetProtectionAssignment.Stage.PURSUING, "");
            var ordered = interceptor.withInterceptOrder(new FleetInterceptOrder(interceptor.ownerEntityId(), target.id(), interceptor.currentSystemId(), assignment));
            var departed = FleetInterceptProcessor.advance(prepared, ordered);
            if (!departed.location().inTransit() || departed.location().equals(interceptor.location())
                    || departed.location().orbitalFlight() != null && departed.location().orbitalFlight().itinerary().waitSeconds() > 0) continue;
            return prepared.withFleets(prepared.fleets().stream().map(fleet -> fleet.id().equals(interceptorId) ? departed : fleet).toList());
        }
        return null;
    }

    private static GameState prepare(GameState state, Fleet fleet) {
        var escort = fleet.escortOrder();
        if (escort == null) {
            if (fleet.guardOrder() == null || !fleet.guardOrder().validTarget(state, fleet)) return null;
            return state.withFleets(state.fleets().stream().map(member -> member.id().equals(fleet.id()) ? member.withGuardOrder(null) : member).toList());
        }
        // An idle escort may detach; paid or assigned freight keeps the entire convoy together.
        if (fleet.ships().stream().anyMatch(ship -> ship.passengerCount() > 0 || !ship.supplyState().orders().isEmpty())
                || state.tradeRoutes().stream().anyMatch(route -> (route.isActive() || route.onboardKg() > 0
                || !com.spaceconquest.engine.logistics.TradeRoute.LOADING.equals(route.phase()))
                && com.spaceconquest.engine.logistics.TradeEscape.associated(route, fleet))) return null;
        if (FleetOrganization.splitProblem(state, fleet.ownerEntityId(), fleet.id(), escort.shipIds(),
                escort.escortFleetId(), escort.escortFleetName()) != null) return null;
        var split = FleetOrganization.split(state, fleet.ownerEntityId(), fleet.id(), escort.shipIds(), escort.escortFleetId(), escort.escortFleetName());
        return split.withFleets(split.fleets().stream().map(member -> member.id().equals(fleet.id())
                ? member.withEscortOrder(null).withStance("ESCORT".equals(fleet.fleetStance()) ? escort.previousStance() : fleet.fleetStance()) : member).toList());
    }
}
