package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.logistics.TradeLegReadiness;
import java.util.*;

/** Tick-owned saved return, fuel recovery and physical reattachment after defensive pursuit. */
public final class FleetProtectionReturn {
    private FleetProtectionReturn() {}
    public static GameState advance(GameState state) {
        var result = state;
        for (var original : state.fleets()) {
            var fleet = FleetOrganization.find(result, original.id());
            if (fleet == null || fleet.interceptOrder() == null || fleet.interceptOrder().protection() == null) continue;
            var saved = fleet.interceptOrder().protection();
            if (saved.stage() == FleetProtectionAssignment.Stage.PURSUING
                    || !FleetReturnTravel.available(fleet) && !(FleetEscortReturn.convoy(result, fleet) != null && FleetEscortReturn.ballisticAvailable(fleet))) continue;
            if (saved.escaped() && !saved.weaponService() && FleetWeaponRecovery.needs(result, fleet)) {
                fleet = fleet.withInterceptOrder(fleet.interceptOrder().withProtection(saved.forWeapons()));
                result = FleetReturnTravel.replace(result, fleet);
            }
            var assignment = fleet.interceptOrder().protection();
            if (assignment.stage() == FleetProtectionAssignment.Stage.SUPPLYING) { result = FleetFuelRecovery.deliver(result, fleet); continue; }
            if (assignment.escaped()) {
                var home = destination(result, fleet);
                if (home == null) { result = FleetReturnTravel.replace(result, fleet.withInterceptOrder(null)); continue; }
                if (!FleetEscapeReturn.clearToReturn(result, fleet, home)) continue;
            }
            if (assignment.stage() == FleetProtectionAssignment.Stage.REFUELING) {
                var port = result.commercialHubs().stream().filter(hub -> hub.id().equals(assignment.serviceTargetId())).findFirst().orElse(null);
                if (port != null && FleetPositioning.atHub(result, fleet, port)) {
                    result = TradeLegReadiness.refillAtPort(result, fleet, port);
                    fleet = FleetReturnTravel.stage(FleetOrganization.find(result, fleet.id()), FleetProtectionAssignment.Stage.RETURNING, "");
                    result = FleetReturnTravel.replace(result, fleet);
                } else {
                    result = FleetReturnTravel.replace(result, FleetReturnTravel.stage(fleet, FleetProtectionAssignment.Stage.RETURNING, ""));
                    fleet = FleetOrganization.find(result, fleet.id());
                }
            }
            var destination = destination(result, fleet);
            if (destination == null) {
                var convoy = assignment.convoyFleetId().isBlank() ? null : FleetOrganization.find(result, assignment.convoyFleetId());
                if (assignment.guard() != null || convoy == null || !fleet.ownerEntityId().equals(convoy.ownerEntityId()))
                    result = FleetReturnTravel.replace(result, fleet.withInterceptOrder(null));
                continue;
            }
            if (assignment.weaponService() && FleetWeaponRecovery.needsBeforeReturn(result, fleet)) {
                result = FleetRepairRecovery.recover(result, fleet, destination);
                fleet = FleetOrganization.find(result, fleet.id());
                if (fleet.interceptOrder().protection().stage() != FleetProtectionAssignment.Stage.REPAIRING)
                    result = FleetReturnTravel.replace(result, FleetReturnTravel.stage(fleet,
                            FleetProtectionAssignment.Stage.RESUPPLYING, fleet.interceptOrder().protection().serviceTargetId()));
                continue;
            }
            if (assignment.stage() == FleetProtectionAssignment.Stage.RESUPPLYING) {
                result = FleetReturnTravel.replace(result, FleetReturnTravel.stage(fleet, FleetProtectionAssignment.Stage.RETURNING, ""));
                fleet = FleetOrganization.find(result, fleet.id());
            }
            if (assignment.stage() == FleetProtectionAssignment.Stage.REPAIRING) {
                result = FleetReturnTravel.replace(result, FleetReturnTravel.stage(fleet, FleetProtectionAssignment.Stage.RETURNING, ""));
                fleet = FleetOrganization.find(result, fleet.id());
            }
            result = FleetRepairRecovery.recover(result, fleet, destination);
            fleet = FleetOrganization.find(result, fleet.id());
            if (fleet.interceptOrder().protection().stage() == FleetProtectionAssignment.Stage.REPAIRING) continue;
            if (assignment.stage() == FleetProtectionAssignment.Stage.REPAIRING) {
                result = FleetRepairRecovery.release(result, fleet, assignment.serviceTargetId());
                fleet = FleetOrganization.find(result, fleet.id());
            }
            var movingConvoy = FleetEscortReturn.convoy(result, fleet);
            if (assignment.guard() != null && assignment.guard().atSite(fleet.location())) { result = restore(result, fleet); continue; }
            if (movingConvoy == null && fleet.location().stationaryAt(destination)) { result = restore(result, fleet); continue; }
            if (FleetThreatAssessment.caution(result, fleet).action() == FleetThreatAssessment.Action.ESCAPE) continue;
            result = FleetReturnFuel.share(result, fleet, destination);
            fleet = FleetOrganization.find(result, fleet.id());
            var plan = FleetReturnTravel.plan(result, fleet, destination);
            if (plan != null) { result = FleetReturnTravel.replace(result, movingConvoy == null ? LocalTravel.depart(fleet, destination, plan)
                    : FleetEscortReturn.depart(fleet, movingConvoy, plan)); continue; }
            // Reach the convoy's parking radius first when a direct phase-matched approach cannot be funded.
            // The saved escort intent survives; physical contact still requires a subsequent rendezvous.
            if (movingConvoy != null && (!movingConvoy.location().inTransit() || RecoveredParkingTravel.ready(movingConvoy)) && RecoveredParkingTravel.ready(fleet)
                    && !RecoveredParkingTravel.atSite(fleet.location(), destination)) {
                var parkingPlan = FleetReturnTravel.localPlan(result, fleet, destination);
                if (parkingPlan != null) {
                    result = FleetReturnTravel.replace(result, LocalTravel.depart(fleet, destination, parkingPlan));
                    continue;
                }
            }
            result = splitAndRecover(result, fleet, destination);
        }
        return FleetServiceHistory.orders(state, result);
    }
    public static FleetLocation.Site destination(GameState state, Fleet fleet) {
        var assignment = fleet.interceptOrder().protection();
        if (!assignment.convoyFleetId().isBlank()) {
            var convoy = FleetOrganization.find(state, assignment.convoyFleetId());
            if (convoy == null || !fleet.ownerEntityId().equals(convoy.ownerEntityId())) return null;
            return fleet.currentSystemId().equals(convoy.currentSystemId()) && !convoy.hasInterstellarOrder() && !convoy.isInWarp()
                    ? convoy.location().inTransit() ? convoy.location().destination() : convoy.location().current() : null;
        }
        if (assignment.guard() != null) {
            var observer = fleet.withInterceptOrder(null).withLocation(FleetLocation.at(assignment.guard().site())).withGuardOrder(assignment.guard());
            if (!assignment.guard().validTarget(state, observer)) return null;
        }
        return assignment.origin();
    }
    private static GameState splitAndRecover(GameState state, Fleet fleet, FleetLocation.Site destination) {
        var ready = new ArrayList<String>(); var weak = new ArrayList<String>();
        for (var ship : fleet.ships()) (FleetReturnTravel.ready(state, fleet.withShips(List.of(ship)), destination) ? ready : weak).add(ship.id());
        if (!ready.isEmpty() && !weak.isEmpty()) {
            String id = uniqueId(state, fleet.id() + "_fuel_" + weak.getFirst());
            var split = FleetOrganization.split(state, fleet.ownerEntityId(), fleet.id(), weak, id, fleet.name() + " fuel recovery");
            var main = FleetOrganization.find(split, fleet.id()); var plan = FleetReturnTravel.plan(split, main, destination);
            if (plan == null) return state;
            var convoy = FleetEscortReturn.convoy(split, main);
            split = FleetReturnTravel.replace(split, convoy == null ? LocalTravel.depart(main, destination, plan) : FleetEscortReturn.depart(main, convoy, plan));
            return FleetFuelRecovery.recover(split, FleetOrganization.find(split, id), destination);
        }
        if (fleet.ships().size() > 1) {
            var result = state;
            for (var ship : fleet.ships().subList(1, fleet.ships().size())) {
                String id = uniqueId(result, fleet.id() + "_fuel_" + ship.id());
                result = FleetOrganization.split(result, fleet.ownerEntityId(), fleet.id(), List.of(ship.id()), id, fleet.name() + " fuel recovery");
                var fragment = FleetOrganization.find(result, id);
                if (fragment != null) result = FleetFuelRecovery.recover(result, fragment, destination);
            }
            return FleetFuelRecovery.recover(result, FleetOrganization.find(result, fleet.id()), destination);
        }
        return FleetFuelRecovery.recover(state, fleet, destination);
    }
    private static String uniqueId(GameState state, String prefix) {
        String id = prefix; int suffix = 1;
        while (FleetOrganization.find(state, id) != null) id = prefix + "_" + suffix++;
        return id;
    }
    static GameState restore(GameState state, Fleet fleet) {
        var assignment = fleet.interceptOrder().protection();
        if (!assignment.convoyFleetId().isBlank()) {
            var convoy = FleetOrganization.find(state, assignment.convoyFleetId());
            var existing = convoy.escortOrder();
            if (existing != null && !existing.escortFleetId().equals(assignment.homeFleetId())) return state;
            var ids = new ArrayList<String>(existing == null ? List.of() : existing.shipIds());
            fleet.ships().stream().map(ShipInstance::id).forEach(ids::add);
            var merged = FleetOrganization.transfer(state, fleet.ownerEntityId(), fleet.id(), convoy.id(), fleet.ships().stream().map(ShipInstance::id).toList());
            var joined = FleetOrganization.find(merged, convoy.id());
            if (FleetOrganization.find(merged, fleet.id()) != null) return state;
            return FleetReturnTravel.replace(merged, joined.withInterceptOrder(convoy.interceptOrder()).withEscortOrder(new FleetEscortOrder(fleet.ownerEntityId(),
                    assignment.homeFleetId(), fleet.name(), existing == null ? convoy.fleetStance() : existing.previousStance(), ids)).withStance("ESCORT"));
        }
        var home = FleetOrganization.find(state, assignment.homeFleetId());
        if (home != null && !home.id().equals(fleet.id()) && FleetOrganization.together(home, fleet)) {
            state = FleetOrganization.transfer(state, fleet.ownerEntityId(), fleet.id(), home.id(), fleet.ships().stream().map(ShipInstance::id).toList());
            fleet = FleetOrganization.find(state, home.id());
        }
        return FleetReturnTravel.replace(state, assignment.repairIntent() == null
                ? fleet.withInterceptOrder(null).withGuardOrder(assignment.guard()) : assignment.repairIntent().restore(state, fleet));
    }
}
