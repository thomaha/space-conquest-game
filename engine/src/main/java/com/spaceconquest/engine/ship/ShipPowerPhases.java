package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import java.util.*;

/** Actual remaining burn/coast durations, rounded only at the ordinary daily arrival endpoint. */
final class ShipPowerPhases {
    private ShipPowerPhases() {}
    static List<ShipShieldPower.Phase> withActivity(List<ShipShieldPower.Phase> phases, ShipServicePower.Activity activity) {
        return phases.stream().map(phase -> new ShipShieldPower.Phase(phase.hours(), phase.driveKw(), phase.environment(), activity)).toList();
    }
    static List<ShipShieldPower.Phase> local(ShipPowerProfile p, ShipSolarEnvironment environment, LocalTravel.Plan plan) {
        if (plan.physical() == null) return List.of(new ShipShieldPower.Phase(Math.ceil(plan.days()) * 24, p.driveKw(), environment));
        var t = plan.physical().trajectory();
        return trajectory(p, environment, t.accelerationSeconds(), t.brakingStart(), t.totalSeconds(), 0, Math.ceil(plan.days()) * 24);
    }
    static List<ShipShieldPower.Phase> crossing(ShipPowerProfile p, InterstellarTravel.Plan plan) {
        double hours = Math.ceil(plan.days()) * 24;
        if (!Fleet.MODE_SUBLIGHT.equals(plan.mode())) return List.of(new ShipShieldPower.Phase(hours, p.driveKw(), ShipSolarEnvironment.DARK));
        double burn = plan.peakSpeedMps() / plan.accelerationMps2();
        double total = InterstellarTravel.travelSeconds(plan.distanceMeters(), plan.accelerationMps2(), plan.peakSpeedMps());
        return trajectory(p, ShipSolarEnvironment.DARK, burn, total - burn, total, 0, hours);
    }
    static List<ShipShieldPower.Phase> remaining(GameState state, Fleet fleet, ShipDesign design) {
        var phases = new ArrayList<>(current(state, fleet, design));
        if (fleet.location().inTransit() && fleet.hasInterstellarOrder()) {
            var crossing = new InterstellarTravel.Plan(fleet.interstellarMode(), fleet.interstellarTravelDays(),
                    fleet.interstellarDistanceMeters(), fleet.interstellarAccelerationMps2(), fleet.interstellarPeakSpeedMps(),
                    fleet.interstellarFuelBudgetKg(), Map.of(), fleet.journeyPropulsion());
            phases.addAll(crossing(design.powerProfile(), crossing));
        }
        return phases;
    }

    private static List<ShipShieldPower.Phase> current(GameState state, Fleet fleet, ShipDesign design) {
        var p = design.powerProfile();
        var environment = fleet.hasInterstellarOrder() && !fleet.location().inTransit() ? ShipSolarEnvironment.DARK
                : fleet.location().inTransit() ? ShipSolarEnvironment.journey(state, fleet, fleet.location().destination())
                : ShipSolarEnvironment.at(state, fleet, fleet.location().current());
        if (Fleet.MODE_POWER_INTERRUPTED.equals(fleet.interstellarMode())) return List.of(new ShipShieldPower.Phase(24, 0, environment));
        if (fleet.location().localFlight() != null) {
            var flight = fleet.location().localFlight(); var motion = flight.motion(); var t = motion.trajectory();
            double hours = Math.max(24, Math.ceil(Math.max(0, t.totalSeconds() - motion.elapsedSeconds()) / 86400) * 24);
            return flight.interrupted() ? List.of(new ShipShieldPower.Phase(24, 0, environment))
                    : trajectory(p, environment, t.accelerationSeconds(), t.brakingStart(), t.totalSeconds(), motion.elapsedSeconds(), hours);
        }
        if (Fleet.MODE_RECOVERY.equals(fleet.interstellarMode()) && fleet.flightMotion() != null) {
            var motion = fleet.flightMotion(); var t = motion.trajectory();
            return trajectory(p, environment, t.accelerationSeconds(), t.brakingStart(), t.totalSeconds(), motion.elapsedSeconds(),
                    Math.max(24, Math.ceil(Math.max(0, t.totalSeconds() - motion.elapsedSeconds()) / 86400) * 24));
        }
        if (fleet.hasInterstellarOrder()) {
            double hours = Math.max(24, Math.ceil(fleet.interstellarTravelDays() - fleet.interstellarElapsedDays()) * 24);
            if (Fleet.MODE_SUBLIGHT.equals(fleet.interstellarMode()) && fleet.interstellarAccelerationMps2() > 0) {
                double burn = fleet.interstellarPeakSpeedMps() / fleet.interstellarAccelerationMps2();
                double total = InterstellarTravel.travelSeconds(fleet.interstellarDistanceMeters(), fleet.interstellarAccelerationMps2(), fleet.interstellarPeakSpeedMps());
                return trajectory(p, environment, burn, total - burn, total, fleet.interstellarElapsedDays() * 86400, hours);
            }
            return List.of(new ShipShieldPower.Phase(hours, p.driveKw(), environment));
        }
        double hours = fleet.location().inTransit() ? Math.max(24, Math.ceil(fleet.location().travelDays() * (1 - fleet.location().progress())) * 24) : 24;
        return List.of(new ShipShieldPower.Phase(hours, fleet.location().inTransit() ? p.driveKw() : 0, environment));
    }
    private static List<ShipShieldPower.Phase> trajectory(ShipPowerProfile p, ShipSolarEnvironment environment,
                                                         double burnEnd, double brakeStart, double total, double start, double hours) {
        double end = start + hours * 3600; var result = new ArrayList<ShipShieldPower.Phase>();
        double[] bounds = {0, burnEnd, brakeStart, total, end};
        for (int i = 0; i < 4; i++) {
            double from = Math.max(start, bounds[i]), to = Math.min(end, bounds[i + 1]);
            if (to > from) result.add(new ShipShieldPower.Phase((to - from) / 3600, i == 0 || i == 2 ? p.driveKw() : 0, environment));
        }
        return result;
    }
}
