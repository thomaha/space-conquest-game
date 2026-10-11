package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.logistics.TradeRoute;
import java.util.*;

/** Shared player and AI order handling: pause only available fleets when a useful repair is fundable. */
public final class FleetRepairOrders {
    private FleetRepairOrders() {}
    public static GameState advance(GameState state) {
        var result = state;
        for (var original : state.fleets()) {
            var fleet = FleetOrganization.find(result, original.id());
            if (!eligible(result, fleet) || FleetRepairRecovery.need(result, fleet) <= 1e-9) continue;
            var intent = FleetRepairIntent.capture(fleet);
            var assignment = new FleetProtectionAssignment(fleet.guardOrder(), "", fleet.id(), fleet.location().current(),
                    FleetProtectionAssignment.Stage.RETURNING, "", false, intent);
            var ordered = fleet.withGuardOrder(null).withEscortOrder(null)
                    .withInterceptOrder(new FleetInterceptOrder(fleet.ownerEntityId(), fleet.id(), fleet.currentSystemId(), assignment));
            var prepared = FleetReturnTravel.replace(result, ordered);
            var repair = FleetRepairRecovery.recover(prepared, ordered, assignment.origin());
            if (FleetOrganization.find(repair, fleet.id()).interceptOrder().protection().stage() == FleetProtectionAssignment.Stage.REPAIRING)
                result = repair;
        }
        return result;
    }
    private static boolean eligible(GameState state, Fleet fleet) {
        return fleet != null && !fleet.ships().isEmpty() && FleetReturnTravel.available(fleet) && !fleet.location().inTransit()
                && fleet.location().current().kind() != FleetLocation.Kind.SURFACE
                && fleet.interstellarMode().isBlank() && (fleet.interceptOrder() == null || fleet.interceptOrder().protection() == null)
                && fleet.ships().stream().allMatch(ship -> fleet.ownerEntityId().equals(ship.ownerEntityId())
                    && ship.currentHullHealth() > 0 && ship.passengerCount() == 0 && ship.supplyState().orders().isEmpty()
                    && state.shipDesigns().stream().anyMatch(design -> ship.designId().equals(design.id())
                        && fleet.ownerEntityId().equals(design.ownerEntityId()))
                    && (ship.powerState() == null || ship.powerState().rescueStatus() == null
                        || ship.powerState().rescueStatus().phase() != RescueStatus.Phase.APPROACHING))
                && state.tradeRoutes().stream().noneMatch(route -> (route.isActive() || route.onboardKg() > 0
                    || !TradeRoute.LOADING.equals(route.phase())) && route.assignedFreighterIds().stream()
                        .anyMatch(id -> fleet.ships().stream().anyMatch(ship -> ship.id().equals(id))))
                && state.fleets().stream().flatMap(item -> item.ships().stream()).noneMatch(ship -> ship.powerState() != null
                    && ship.powerState().rescueStatus() != null && ship.powerState().rescueStatus().phase() == RescueStatus.Phase.APPROACHING
                    && (fleet.id().equals(ship.powerState().rescueStatus().rescuerFleetId())
                        || fleet.id().equals(ship.powerState().rescueStatus().order().targetFleetId())));
    }
}
