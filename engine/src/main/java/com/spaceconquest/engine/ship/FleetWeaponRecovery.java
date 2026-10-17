package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;

/** Saved weapon service intent and non-spending readiness checks shared with return processing. */
public final class FleetWeaponRecovery {
    private FleetWeaponRecovery() {}
    public static boolean active(Fleet fleet) {
        return fleet.interceptOrder() != null && fleet.interceptOrder().protection() != null
                && fleet.interceptOrder().protection().weaponService();
    }
    public static boolean needs(GameState state, Fleet fleet) {
        return fleet.ships().stream().anyMatch(ship -> ShipWeaponReadiness.inspect(FleetSupplySimulation.design(state, ship), ship, true)
                .weapons().stream().anyMatch(weapon -> switch (weapon.blocker()) {
                    case AMMUNITION, POWER, DISABLED, CONDITION -> true;
                    default -> false;
                }));
    }
    public static Fleet pause(Fleet fleet) {
        var intent = FleetRepairIntent.capture(fleet);
        var origin = fleet.guardOrder() != null ? fleet.guardOrder().site()
                : RecoveredParkingTravel.ready(fleet) ? fleet.location().destination() : fleet.location().current();
        var assignment = new FleetProtectionAssignment(fleet.guardOrder(), "", fleet.id(), origin,
                FleetProtectionAssignment.Stage.RESUPPLYING, "", false, intent, true);
        return fleet.withGuardOrder(null).withEscortOrder(null).withInterceptOrder(
                new FleetInterceptOrder(fleet.ownerEntityId(), fleet.id(), fleet.currentSystemId(), assignment));
    }
    /** A ready opening salvo must also remain funded after the return journey. */
    public static boolean needsBeforeReturn(GameState state, Fleet fleet) {
        if (needs(state, fleet)) return true;
        if (!active(fleet)) return false;
        var destination = FleetProtectionReturn.destination(state, fleet);
        if (destination == null || fleet.location().stationaryAt(destination)) return false;
        var plan = FleetReturnTravel.plan(state, fleet, destination);
        if (plan == null) plan = LocalTravel.plan(state, fleet, destination);
        return plan != null && needs(state, LocalTravel.arrivalPreview(state, fleet, destination, plan));
    }
}
