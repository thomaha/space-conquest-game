package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import java.util.HashMap;
import static com.spaceconquest.engine.ship.VectorFlight.*;

/** Shared funded circular recovery in the saved stellar, planetary or lunar gravity frame. */
public final class OrbitalRecovery {
    public record InitialState(LocalSiteGeometry.Point position, LocalSiteGeometry.Point velocity,
                               TwoBodyFlight.Gravity gravity) {}
    private OrbitalRecovery() {}

    public static boolean applies(Fleet fleet) {
        if (fleet == null) return false;
        var orbital = fleet.location().orbitalFlight();
        return (orbital != null && orbital.nextManeuver() == 1 && orbital.status() == OrbitalFlight.Status.MISSED
                && orbital.elapsedSeconds() >= orbital.itinerary().waitSeconds())
                || fleet.location().localFlight() != null
                && fleet.location().localFlight().vectorFlight() != null
                && fleet.location().localFlight().vectorFlight().trajectory().gravity() != null;
    }

    public static boolean recoverable(Fleet fleet) {
        return applies(fleet) && (fleet.location().orbitalFlight() != null || fleet.location().localFlight().interrupted());
    }

    public static OrbitalFlight.Frame frame(Fleet fleet) {
        return fleet.location().orbitalFlight() != null ? fleet.location().orbitalFlight().itinerary().frame()
                : fleet.location().localFlight().vectorFlight().trajectory().gravity().frame();
    }

    /** Reads the physical state without converting, paying for or modifying a saved itinerary. */
    public static InitialState initialState(GameState state, Fleet fleet) {
        if (state == null || !applies(fleet)) return null;
        var system = state.solarSystems().stream().filter(item -> item.id().equals(fleet.currentSystemId())).findFirst().orElse(null);
        if (system == null) return null;
        var frame = frame(fleet); var body = OrbitalBody.find(state, system.id(), frame.centerBodyId());
        boolean stellar = frame.kind() == OrbitalFlight.CenterKind.STELLAR;
        if (!stellar && (body == null || !body.frame().equals(frame))) return null;
        double mu = stellar ? CircularOrbitalEphemeris.gravitationalParameter(system)
                : PlanetaryTransferComparison.GRAVITATIONAL_CONSTANT * body.mass();
        var gravity = new TwoBodyFlight.Gravity(mu, (stellar ? system.sunDiameter() : body.diameter()) * 500,
                frame, stellar ? Double.MAX_VALUE : body.sphereKm(system) * 1000);
        if (fleet.location().localFlight() != null) {
            var actual = fleet.location().localFlight().vectorFlight(); var saved = actual.trajectory().gravity();
            if (Math.abs(saved.gravitationalParameter() / mu - 1) > 1e-9) return null;
            return new InitialState(actual.position(), actual.velocity(), saved);
        }
        var orbital = fleet.location().orbitalFlight();
        if (Math.abs(orbital.itinerary().coast().gravitationalParameter() / mu - 1) > 1e-9) return null;
        var actual = orbital.position();
        return new InitialState(new LocalSiteGeometry.Point(actual.xMeters(), actual.yMeters(), 0),
                new LocalSiteGeometry.Point(actual.vxMps(), actual.vyMps(), 0), gravity);
    }

    /** Phase is the selected orbit's angle at the planning instant, in radians. */
    public static LocalSpaceTravel.Plan plan(GameState state, Fleet fleet, double radiusMeters, double phaseRadians) {
        if (state == null || !recoverable(fleet)
                || fleet.ships().isEmpty() || fleet.hasInterstellarOrder() || fleet.isInWarp()
                || !Double.isFinite(radiusMeters)
                || !Double.isFinite(phaseRadians)) return null;
        var system = state.solarSystems().stream().filter(item -> item.id().equals(fleet.currentSystemId())).findFirst().orElse(null);
        if (system == null) return null;
        var actual = initialState(state, fleet);
        if (actual == null) return null;
        var gravity = actual.gravity();
        boolean stellar = gravity.frame().kind() == OrbitalFlight.CenterKind.STELLAR;
        double minimum = stellar ? .1 * ShipSolarEnvironment.AU_KM * 1000 : gravity.minimumRadiusMeters();
        if (radiusMeters <= minimum || norm(actual.position()) < minimum || radiusMeters >= gravity.maximumRadiusMeters()
                || norm(actual.position()) >= gravity.maximumRadiusMeters()) return null;
        var destination = stellar ? FleetLocation.Site.deepSpace()
                : FleetLocation.Site.orbit(gravity.frame().centerBodyId(), (radiusMeters - gravity.minimumRadiusMeters()) / 1000);
        var capabilities = new HashMap<String, LocalSpaceTravel.Capability>();
        double acceleration = Double.POSITIVE_INFINITY, deltaV = Double.POSITIVE_INFINITY;
        for (var ship : fleet.ships()) {
            var capability = LocalSpaceTravel.capability(state, fleet, ship, destination,
                    fleet.fuelPolicy().reserveKg(state, fleet, ship));
            if (capability == null || ship.currentHullHealth() <= 0) return null;
            capabilities.put(ship.id(), capability);
            acceleration = Math.min(acceleration, capability.accelerationMps2()); deltaV = Math.min(deltaV, capability.deltaVMps());
        }
        double speed = Math.sqrt(gravity.gravitationalParameter() / radiusMeters);
        double period = 2 * Math.PI * radiusMeters / speed;
        for (int candidate = 1; candidate <= 7; candidate++) {
            double seconds = stellar ? candidate * 86400.0 : Math.min(7 * 86400, period / 128 * Math.pow(1.6, candidate - 1));
            double angle = phaseRadians + speed / radiusMeters * seconds;
            var goal = new LocalSiteGeometry.Point(radiusMeters * Math.cos(angle), radiusMeters * Math.sin(angle), 0);
            var terminal = new LocalSiteGeometry.Point(-speed * Math.sin(angle), speed * Math.cos(angle), 0);
            var trajectory = OrbitalRendezvousPlanner.shoot(actual.position(), actual.velocity(), goal, terminal,
                    seconds, gravity, acceleration, deltaV);
            if (trajectory == null) continue;
            var geometry = new LocalSiteGeometry.Leg(actual.position(), goal, Math.max(1, norm(subtract(goal, actual.position()))));
            var physical = VectorInterceptPlanner.budget(state, fleet, geometry, trajectory, capabilities);
            var local = new LocalTravel.Plan(physical.days(), physical.propellantKg(), physical.reactorFuelKg(), physical);
            if (ShipPowerForecast.ready(ShipPowerForecast.departure(state, fleet, destination, local, null))) return physical;
        }
        return null;
    }

    public static Fleet depart(Fleet fleet, LocalSpaceTravel.Plan plan) {
        var gravity = plan.vectorTrajectory().gravity();
        boolean stellar = gravity.frame().kind() == OrbitalFlight.CenterKind.STELLAR;
        var source = stellar ? FleetLocation.Site.deepSpace() : FleetLocation.Site.orbit(gravity.frame().centerBodyId());
        var destination = stellar ? FleetLocation.Site.deepSpace(fleet.currentSystemId() + "_circular_coast")
                : FleetLocation.Site.orbit(gravity.frame().centerBodyId(),
                (norm(plan.geometry().destination()) - gravity.minimumRadiusMeters()) / 1000);
        var planning = fleet.withInterceptOrder(null).withLocation(new FleetLocation(source, destination, 0, plan.days()));
        var moved = LocalSpaceTravel.resume(planning, plan);
        var location = moved.location();
        return moved.withLocation(new FleetLocation(source, destination,
                location.progress(), location.travelDays(), location.localFlight()));
    }

    /** Cancels thrust immediately without advancing the clock or altering physical state. */
    public static Fleet cancel(Fleet fleet) {
        if (fleet.location().orbitalFlight() != null) return OrbitalFlightProcessor.cancel(fleet.withInterceptOrder(null));
        var flight = fleet.location().localFlight();
        return fleet.withInterceptOrder(null).withLocation(fleet.location().withFlight(new LocalFlight(flight.geometry(),
                flight.motion(), flight.propellantKg(), flight.propulsion(), true, flight.advancedInPowerTick(), null, flight.vectorFlight())));
    }
}
