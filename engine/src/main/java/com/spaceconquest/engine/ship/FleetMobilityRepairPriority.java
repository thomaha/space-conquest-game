package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import java.util.*;

/** Immutable complete-fleet quotes: drive work gains priority only when it unlocks funded motion. */
final class FleetMobilityRepairPriority {
    private FleetMobilityRepairPriority() {}

    static Map<String, Set<Integer>> useful(GameState state, Fleet fleet, boolean withdrawing) {
        boolean interrupted = Fleet.MODE_POWER_INTERRUPTED.equals(fleet.interstellarMode())
                || fleet.location().localFlight() != null && fleet.location().localFlight().interrupted()
                || OrbitalRecovery.recoverable(fleet);
        var order = fleet.interceptOrder();
        boolean returning = order != null && order.protection() != null
                && order.protection().stage() != FleetProtectionAssignment.Stage.PURSUING;
        if ((!interrupted && !withdrawing && !returning) || !PropulsionDamage.damaged(state, fleet)) return Map.of();
        if (funded(state, fleet)) return Map.of();
        var result = new HashMap<String, Set<Integer>>();
        for (var ship : fleet.ships()) {
            var design = FleetSupplySimulation.design(state, ship);
            if (design == null || ship.currentHullHealth() <= 0) continue;
            var useful = new HashSet<Integer>();
            for (int mount = 0; mount < design.equippedModuleIds().size(); mount++) {
                if (PropulsionCatalog.module(design.equippedModuleIds().get(mount)) == null
                        || ship.moduleDamage().condition(mount) >= 1) continue;
                var repaired = ship.withModuleDamage(ship.moduleDamage().repair(mount, ShipModuleDamage.DURABILITY));
                var candidate = fleet.withShips(fleet.ships().stream().map(member -> member.id().equals(ship.id()) ? repaired : member).toList());
                var projected = FleetReturnTravel.replace(state, candidate);
                if (funded(projected, candidate)) useful.add(mount);
            }
            if (!useful.isEmpty()) result.put(ship.id(), Set.copyOf(useful));
        }
        return Map.copyOf(result);
    }

    private static boolean funded(GameState state, Fleet fleet) {
        if (OrbitalRecovery.recoverable(fleet)) {
            var motion = OrbitalRecovery.initialState(state, fleet);
            if (motion == null) return false;
            var plan = OrbitalRecovery.plan(state, fleet, VectorFlight.norm(motion.position()),
                    Math.atan2(motion.position().yMeters(), motion.position().xMeters()));
            return plan != null && sustained(state, fleet, plan.days());
        }
        if (fleet.location().localFlight() != null && fleet.location().localFlight().interrupted()) {
            var plan = PausedTravelRecovery.preview(state, fleet);
            return plan != null && plan.ready() && sustained(state, fleet, plan.remainingDays());
        }
        if (Fleet.MODE_POWER_INTERRUPTED.equals(fleet.interstellarMode()) && fleet.interstellarDistanceMeters() > 0) {
            var check = FlightRecoveryReadiness.check(state, fleet);
            return check.ready() && sustained(state, fleet, check.plan().trajectory().totalSeconds() / 86400);
        }
        var order = fleet.interceptOrder();
        if (order != null && order.protection() != null && order.protection().stage() != FleetProtectionAssignment.Stage.PURSUING) {
            var site = FleetProtectionReturn.destination(state, fleet);
            if (site != null && fleet.location().stationaryAt(site)) return true;
            var plan = site == null ? null : FleetReturnTravel.plan(state, fleet, site);
            if (plan != null && sustained(state, fleet, plan.days())) return true;
        }
        return FleetThreatAssessment.assess(state, fleet).action() == FleetThreatAssessment.Action.ESCAPE
                && FleetProtectionProcessor.decide(state, fleet, List.of()).outcome() == FleetProtectionProcessor.Outcome.ESCAPING;
    }
    private static boolean sustained(GameState state, Fleet fleet, double days) {
        // Missing race supply definitions conservatively block promotion for booked passengers.
        return com.spaceconquest.engine.habitation.PassengerTransitProcessor.canSustainJourney(state, fleet, List.of(), days);
    }
}
