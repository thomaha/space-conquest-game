package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.galaxy.SensorPosition;
import java.util.HashMap;
import java.util.Map;
import static com.spaceconquest.engine.ship.VectorFlight.*;

/** Bounded finite-burn rendezvous from arbitrary deep-space positions, without gravity. */
public final class VectorInterceptPlanner {
    private static final LocalSiteGeometry.Point ZERO = new LocalSiteGeometry.Point(0, 0, 0);
    private VectorInterceptPlanner() {}

    public static LocalTravel.Plan plan(GameState state, Fleet hunter, Fleet target, double horizon) {
        var observed = target.location().localFlight();
        if (observed.vectorFlight() != null) return null;
        var start = SensorPosition.at(state, hunter.currentSystemId(), hunter.location());
        var goal = SensorPosition.at(state, target.currentSystemId(), target.location());
        if (start == null || goal == null) return null;
        var initial = hunter.location().inTransit() ? velocity(hunter.location().localFlight()) : ZERO;
        var physical = solve(state, hunter, target.location().destination(), observed.geometry(), start, initial,
                goal, velocity(observed), horizon);
        return physical == null ? null : new LocalTravel.Plan(physical.days(), physical.propellantKg(), physical.reactorFuelKg(), physical);
    }

    public static LocalSpaceTravel.Plan recovery(GameState state, Fleet fleet) {
        var flight = fleet.location().localFlight(); var actual = flight.vectorFlight();
        if (actual.trajectory().gravity() != null) return null;
        return solve(state, fleet, fleet.location().destination(), flight.geometry(), actual.position(), actual.velocity(),
                flight.geometry().destination(), ZERO, 30 * InterstellarTravel.SECONDS_PER_DAY);
    }

    static LocalTravel.Plan escape(GameState state, Fleet fleet, FleetLocation.Site destination) {
        var flight = fleet.location().localFlight();
        if (flight == null || flight.vectorFlight() != null && flight.vectorFlight().trajectory().gravity() != null) return null;
        var start = SensorPosition.at(state, fleet.currentSystemId(), fleet.location());
        var goal = SensorPosition.at(state, fleet.currentSystemId(), FleetLocation.at(destination));
        if (start == null || goal == null || norm(subtract(goal, start)) < 1) return null;
        var geometry = new LocalSiteGeometry.Leg(start, goal, norm(subtract(goal, start)));
        var physical = solve(state, fleet, destination, geometry, start, velocity(flight), goal, ZERO, 30 * 86400);
        return physical == null ? null : new LocalTravel.Plan(physical.days(), physical.propellantKg(), physical.reactorFuelKg(), physical);
    }

    private static LocalSpaceTravel.Plan solve(GameState state, Fleet fleet, FleetLocation.Site destination,
                                               LocalSiteGeometry.Leg geometry, LocalSiteGeometry.Point start,
                                               LocalSiteGeometry.Point initial, LocalSiteGeometry.Point goal,
                                               LocalSiteGeometry.Point terminal, double horizon) {
        var capabilities = new HashMap<String, LocalSpaceTravel.Capability>();
        double acceleration = Double.POSITIVE_INFINITY, deltaV = Double.POSITIVE_INFINITY;
        for (var ship : fleet.ships()) {
            var capability = LocalSpaceTravel.capability(state, fleet, ship, destination, fleet.fuelPolicy().reserveKg(state, fleet, ship));
            if (capability == null) return null;
            capabilities.put(ship.id(), capability);
            acceleration = Math.min(acceleration, capability.accelerationMps2()); deltaV = Math.min(deltaV, capability.deltaVMps());
        }
        if (!Double.isFinite(acceleration) || acceleration <= 0 || norm(initial) >= InterstellarTravel.MAX_CRUISE_MPS
                || norm(terminal) >= InterstellarTravel.MAX_CRUISE_MPS || norm(subtract(terminal, initial)) > deltaV) return null;
        double limit = Math.floor(horizon / InterstellarTravel.SECONDS_PER_DAY) * InterstellarTravel.SECONDS_PER_DAY;
        if (limit <= 0) return null;
        for (int candidate = 0; candidate < 48; candidate++) {
            double seconds = Math.min(limit, 60 * Math.pow(1.4, candidate));
            var trajectory = trajectory(start, initial, goal, terminal, seconds);
            if (norm(trajectory.firstAcceleration()) <= acceleration && norm(trajectory.lastAcceleration()) <= acceleration
                    && trajectory.impulse(seconds) > 1e-9
                    && trajectory.impulse(seconds) <= deltaV
                    && norm(add(initial, scale(trajectory.firstAcceleration(), trajectory.burnSeconds()))) < InterstellarTravel.MAX_CRUISE_MPS) {
                var physical = budget(state, fleet, geometry, trajectory, capabilities);
                var local = new LocalTravel.Plan(physical.days(), physical.propellantKg(), physical.reactorFuelKg(), physical);
                if (ShipPowerForecast.ready(ShipPowerForecast.departure(state, fleet, destination, local, null))) return physical;
            }
            if (seconds >= limit) break;
        }
        return null;
    }

    private static VectorFlight.Trajectory trajectory(LocalSiteGeometry.Point start, LocalSiteGeometry.Point initial,
                                                       LocalSiteGeometry.Point goal, LocalSiteGeometry.Point terminal, double seconds) {
        double burn = seconds / 4;
        var displacement = subtract(add(goal, scale(terminal, seconds)), start);
        var change = subtract(terminal, initial);
        var first = scale(subtract(subtract(displacement, scale(initial, seconds)), scale(change, burn / 2)),
                1 / (burn * (seconds - burn)));
        var last = subtract(scale(change, 1 / burn), first);
        return new VectorFlight.Trajectory(start, initial, first, last, burn, seconds - 2 * burn);
    }

    static LocalSpaceTravel.Plan budget(GameState state, Fleet fleet, LocalSiteGeometry.Leg geometry,
                                                VectorFlight.Trajectory trajectory, Map<String, LocalSpaceTravel.Capability> capabilities) {
        Map<String, Double> fuel = new HashMap<>();
        Map<String, InterstellarTravel.ReactorFuelUse> feeds = new HashMap<>();
        Map<String, JourneyPropulsion> propulsion = new HashMap<>();
        for (var entry : capabilities.entrySet()) {
            var capability = entry.getValue();
            double kg = capability.massKg() * -Math.expm1(-trajectory.impulse(trajectory.totalSeconds()) / capability.exhaustMps());
            fuel.put(entry.getKey(), kg);
            var feed = capability.reactor();
            if (feed != null && feed.kgPerPropellantKg() > 0)
                feeds.put(entry.getKey(), new InterstellarTravel.ReactorFuelUse(feed.materialId(), kg * feed.kgPerPropellantKg()));
            propulsion.put(entry.getKey(), JourneyPropulsion.capture(capability.ship(), capability.drive(), feed, kg)
                    .withProtectedPropellant(fleet.fuelPolicy().reserveKg(state, fleet, capability.ship())));
        }
        return new LocalSpaceTravel.Plan(geometry, trajectory.envelope(), fuel, feeds, propulsion, trajectory);
    }
}
