package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.galaxy.SensorPosition;
import java.util.HashMap;
import static com.spaceconquest.engine.ship.VectorFlight.*;

/** Bounded stellar-frame shooting against an observed two-body coast. */
public final class OrbitalRendezvousPlanner {
    private static final LocalSiteGeometry.Point ZERO = new LocalSiteGeometry.Point(0, 0, 0);
    private OrbitalRendezvousPlanner() {}

    public static LocalTravel.Plan plan(GameState state, Fleet hunter, Fleet target) {
        var observed = target.location().orbitalFlight();
        if (!supported(observed) || hunter.location().current().kind() != FleetLocation.Kind.DEEP_SPACE) return null;
        var system = state.solarSystems().stream().filter(item -> hunter.currentSystemId().equals(item.id())).findFirst().orElse(null);
        var start = SensorPosition.at(state, hunter.currentSystemId(), hunter.location());
        if (system == null || start == null) return null;
        double mu = CircularOrbitalEphemeris.gravitationalParameter(system);
        if (Math.abs(observed.itinerary().coast().gravitationalParameter() / mu - 1) > 1e-9) return null;
        if (norm(start) < .1 * ShipSolarEnvironment.AU_KM * 1000) return null;
        var gravity = new TwoBodyFlight.Gravity(mu, system.sunDiameter() * 500);
        double horizon = observed.failed() ? 7 * 86400 : Math.min(7 * 86400,
                observed.itinerary().waitSeconds() + observed.itinerary().coastDurationSeconds() - observed.elapsedSeconds());
        var capabilities = new HashMap<String, LocalSpaceTravel.Capability>();
        double acceleration = Double.POSITIVE_INFINITY, deltaV = Double.POSITIVE_INFINITY;
        for (var ship : hunter.ships()) {
            var capability = LocalSpaceTravel.capability(state, hunter, ship, target.location().destination(), hunter.fuelPolicy().reserveKg(state, hunter, ship));
            if (capability == null) return null;
            capabilities.put(ship.id(), capability);
            acceleration = Math.min(acceleration, capability.accelerationMps2()); deltaV = Math.min(deltaV, capability.deltaVMps());
        }
        for (int days = 1; days <= 7 && days * 86400 <= horizon; days++) {
            double seconds = days * 86400.0;
            var goal = observed.itinerary().coast().at(observed.elapsedSeconds() - observed.itinerary().waitSeconds() + seconds);
            var position = new LocalSiteGeometry.Point(goal.xMeters(), goal.yMeters(), 0);
            if (norm(position) < .1 * ShipSolarEnvironment.AU_KM * 1000) continue;
            var velocity = new LocalSiteGeometry.Point(goal.vxMps(), goal.vyMps(), 0);
            if (norm(velocity) > deltaV) continue;
            var trajectory = shoot(start, ZERO, position, velocity, seconds, gravity, acceleration, deltaV);
            if (trajectory == null) continue;
            double distance = norm(subtract(position, start));
            var geometry = new LocalSiteGeometry.Leg(start, position, Math.max(1, distance));
            var physical = VectorInterceptPlanner.budget(state, hunter, geometry, trajectory, capabilities);
            var local = new LocalTravel.Plan(physical.days(), physical.propellantKg(), physical.reactorFuelKg(), physical);
            if (ShipPowerForecast.ready(ShipPowerForecast.departure(state, hunter, target.location().destination(), local, null))) return local;
        }
        return null;
    }

    static VectorFlight.Trajectory shoot(LocalSiteGeometry.Point start, LocalSiteGeometry.Point initial, LocalSiteGeometry.Point goal,
                                                  LocalSiteGeometry.Point velocity, double seconds, TwoBodyFlight.Gravity gravity,
                                                  double acceleration, double deltaV) {
        double burn = seconds / 4;
        VectorFlight baseline;
        var tiny = new LocalSiteGeometry.Point(1e-30, 0, 0);
        try { baseline = new VectorFlight.Trajectory(start, initial, tiny, tiny, burn, seconds - 2 * burn, gravity).at(seconds); }
        catch (IllegalArgumentException outsideDomain) { return null; }
        var change = subtract(velocity, baseline.velocity());
        var first = scale(subtract(subtract(goal, baseline.position()), scale(change, burn / 2)), 1 / (burn * (seconds - burn)));
        var last = subtract(scale(change, 1 / burn), first);
        for (int iteration = 0; iteration < 20; iteration++) {
            var trajectory = new VectorFlight.Trajectory(start, initial, first, last, burn, seconds - 2 * burn, gravity);
            if (norm(first) > acceleration || norm(last) > acceleration || trajectory.impulse(seconds) > deltaV) return null;
            VectorFlight end;
            try { end = trajectory.at(seconds); } catch (IllegalArgumentException outsideDomain) { return null; }
            var positionError = subtract(goal, end.position()); var velocityError = subtract(velocity, end.velocity());
            if (norm(positionError) <= .05 && norm(velocityError) <= .00005) return trajectory;
            var correction = scale(subtract(positionError, scale(velocityError, burn / 2)), 1 / (burn * (seconds - burn)));
            first = add(first, correction); last = add(last, subtract(scale(velocityError, 1 / burn), correction));
        }
        return null;
    }

    public static boolean supported(OrbitalFlight flight) {
        return flight != null && flight.nextManeuver() == 1 && flight.itinerary().frame().kind() == OrbitalFlight.CenterKind.STELLAR
                && flight.elapsedSeconds() >= flight.itinerary().waitSeconds()
                && (flight.status() == OrbitalFlight.Status.COAST || flight.status() == OrbitalFlight.Status.MISSED);
    }

    public static boolean matches(GameState state, Fleet hunter, Fleet target) {
        var flight = hunter.location().localFlight(); var observed = target.location().orbitalFlight();
        if (!supported(observed) || flight.vectorFlight() == null || flight.vectorFlight().trajectory().gravity() == null) return false;
        var gravity = flight.vectorFlight().trajectory().gravity();
        if (gravity.frame().kind() != OrbitalFlight.CenterKind.STELLAR) return false;
        if (Math.abs(observed.itinerary().coast().gravitationalParameter() / gravity.gravitationalParameter() - 1) > 1e-9) return false;
        var actual = flight.vectorFlight(); var position = SensorPosition.at(state, target.currentSystemId(), target.location());
        var velocity = observed.position();
        return position != null && norm(subtract(actual.position(), position)) <= 1
                && norm(subtract(actual.velocity(), new LocalSiteGeometry.Point(velocity.vxMps(), velocity.vyMps(), 0))) <= .001;
    }
}
