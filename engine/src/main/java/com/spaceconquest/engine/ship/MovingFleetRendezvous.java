package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.galaxy.SensorPosition;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/** Funded interception of an observed constant-velocity local corridor, including sideways approaches. */
public final class MovingFleetRendezvous {
    private static final double DAY = InterstellarTravel.SECONDS_PER_DAY;
    private MovingFleetRendezvous() {}

    public static LocalTravel.Plan plan(GameState state, Fleet hunter, Fleet target) {
        var observed = target.location().localFlight();
        if (observed == null || observed.vectorFlight() != null
                || hunter.location().inTransit() && !FleetEscortReturn.ballisticAvailable(hunter) || hunter.hasInterstellarOrder()
                || hunter.location().current().kind() != FleetLocation.Kind.DEEP_SPACE
                || hunter.location().current().equals(target.location().destination())) return null;
        double horizon = 30 * DAY;
        if (!observed.interrupted()) {
            var itinerary = observed.motion().trajectory(); double elapsed = observed.motion().elapsedSeconds();
            if (elapsed < itinerary.accelerationSeconds() || elapsed >= itinerary.brakingStart()) return null;
            horizon = Math.min(horizon, itinerary.brakingStart() - elapsed);
        }
        if (hunter.location().inTransit()) return VectorInterceptPlanner.plan(state, hunter, target, horizon);
        var point = SensorPosition.at(state, hunter.currentSystemId(), hunter.location());
        var geometry = observed.geometry();
        if (point == null) return null;
        double dx = geometry.destination().xMeters() - geometry.origin().xMeters();
        double dy = geometry.destination().yMeters() - geometry.origin().yMeters();
        double dz = geometry.destination().zMeters() - geometry.origin().zMeters();
        double length = Math.hypot(Math.hypot(dx, dy), dz);
        if (Math.abs(length - geometry.distanceMeters()) > .001 || length <= 0) return null;
        double start = ((point.xMeters() - geometry.origin().xMeters()) * dx
                + (point.yMeters() - geometry.origin().yMeters()) * dy
                + (point.zMeters() - geometry.origin().zMeters()) * dz) / length;
        double offLine = Math.hypot(Math.hypot(point.xMeters() - geometry.origin().xMeters() - start * dx / length,
                point.yMeters() - geometry.origin().yMeters() - start * dy / length),
                point.zMeters() - geometry.origin().zMeters() - start * dz / length);
        if (start < 0 || offLine > .001 || observed.motion().positionMeters() - start <= 1)
            return VectorInterceptPlanner.plan(state, hunter, target, horizon);
        var physical = solve(state, hunter, target, geometry, start, horizon);
        if (physical == null) return null;
        var plan = new LocalTravel.Plan(physical.days(), physical.propellantKg(), physical.reactorFuelKg(), physical);
        return ShipPowerForecast.ready(ShipPowerForecast.departure(state, hunter, target.location().destination(), plan, null))
                ? plan : null;
    }

    private static LocalSpaceTravel.Plan solve(GameState state, Fleet hunter, Fleet target,
                                               LocalSiteGeometry.Leg geometry, double start, double horizon) {
        double gap = target.location().localFlight().motion().positionMeters() - start;
        double velocity = target.location().localFlight().motion().velocityMps();
        double acceleration = Double.POSITIVE_INFINITY, deltaV = Double.POSITIVE_INFINITY;
        var capabilities = new HashMap<String, LocalSpaceTravel.Capability>();
        for (var ship : hunter.ships()) {
            var capability = LocalSpaceTravel.capability(state, hunter, ship, target.location().destination(),
                    hunter.fuelPolicy().reserveKg(state, hunter, ship));
            if (capability == null) return null;
            capabilities.put(ship.id(), capability);
            acceleration = Math.min(acceleration, capability.accelerationMps2());
            deltaV = Math.min(deltaV, capability.deltaVMps());
        }
        if (!Double.isFinite(acceleration) || acceleration <= 0 || velocity >= InterstellarTravel.MAX_CRUISE_MPS) return null;
        double peak = Math.min(InterstellarTravel.MAX_CRUISE_MPS, Math.min((deltaV + velocity) / 2,
                velocity + Math.sqrt(acceleration * gap + .5 * velocity * velocity)));
        if (peak <= velocity) return null;
        var trajectory = trajectory(start, gap, velocity, acceleration, peak);
        // Both fleets must stay on the observed coast through the full coordinated tick.
        if (!Double.isFinite(trajectory.totalSeconds()) || Math.ceil(trajectory.totalSeconds() / DAY) * DAY > horizon) return null;
        double deadline = Math.ceil(trajectory.totalSeconds() / DAY) * DAY - .001;
        double low = velocity, high = peak;
        // Retain the earliest funded arrival tick without spending all available delta-v on extra speed.
        for (int attempt = 0; attempt < 32; attempt++) {
            double middle = (low + high) / 2;
            if (trajectory(start, gap, velocity, acceleration, middle).totalSeconds() <= deadline) high = middle;
            else low = middle;
        }
        var economical = trajectory(start, gap, velocity, acceleration, high);
        if (Math.ceil(economical.totalSeconds() / DAY) == Math.ceil(trajectory.totalSeconds() / DAY)) trajectory = economical;
        Map<String, Double> fuel = new HashMap<>();
        Map<String, InterstellarTravel.ReactorFuelUse> feeds = new HashMap<>();
        Map<String, JourneyPropulsion> propulsion = new HashMap<>();
        for (var entry : capabilities.entrySet()) {
            var capability = entry.getValue();
            double kg = capability.massKg() * -Math.expm1(-trajectory.impulse(trajectory.totalSeconds()) / capability.exhaustMps());
            if (!Double.isFinite(kg) || kg <= 0 || kg > capability.ship().currentFuelKg()) return null;
            fuel.put(entry.getKey(), kg);
            var feed = capability.reactor();
            if (feed != null && feed.kgPerPropellantKg() > 0)
                feeds.put(entry.getKey(), new InterstellarTravel.ReactorFuelUse(feed.materialId(), kg * feed.kgPerPropellantKg()));
            propulsion.put(entry.getKey(), JourneyPropulsion.capture(capability.ship(), capability.drive(), feed, kg)
                    .withProtectedPropellant(hunter.fuelPolicy().reserveKg(state, hunter, capability.ship())));
        }
        return new LocalSpaceTravel.Plan(geometry, trajectory, fuel, feeds, propulsion);
    }

    private static FlightMotion.Trajectory trajectory(double start, double gap, double velocity, double acceleration, double peak) {
        double covered = ((peak - velocity) * (peak - velocity) - .5 * velocity * velocity) / acceleration;
        double coast = Math.max(0, (gap - covered) / (peak - velocity));
        return new FlightMotion.Trajectory(start, 0, acceleration, peak,
                peak / acceleration, coast, (peak - velocity) / acceleration, velocity, null);
    }

    public static Fleet depart(Fleet hunter, Fleet target, LocalTravel.Plan plan) {
        var departed = LocalTravel.depart(hunter, target.location().inTransit() ? target.location().destination() : target.location().current(), plan);
        var flight = departed.location().localFlight();
        return departed.withLocation(departed.location().withFlight(new LocalFlight(flight.geometry(), flight.motion(),
                flight.propellantKg(), flight.propulsion(), false, false, target.id(), flight.vectorFlight())));
    }

    public static boolean finished(Fleet fleet) {
        var flight = fleet.location().localFlight();
        return flight != null && flight.rendezvousTargetId() != null && (flight.interrupted()
                || flight.motion().elapsedSeconds() + 1e-6 >= flight.motion().trajectory().totalSeconds());
    }

    public static boolean matches(GameState state, Fleet hunter, Fleet target) {
        return hunter != null && FleetInterceptProcessor.contact(state, hunter, target) && physicalMatches(state, hunter, target);
    }

    /** Actual contact without hostile diplomacy or sensor authorization; callers validate friendly ownership. */
    public static boolean physicalMatches(GameState state, Fleet hunter, Fleet target) {
        if (hunter == null || target == null || hunter.interceptOrder() == null
                || !target.id().equals(hunter.interceptOrder().targetFleetId())
                || !hunter.currentSystemId().equals(target.currentSystemId())
                || hunter.hasInterstellarOrder() || target.hasInterstellarOrder() || target.isInWarp()) return false;
        var flight = hunter.location().localFlight(); var other = target.location().localFlight();
        if (finished(hunter) && !flight.interrupted() && target.id().equals(flight.rendezvousTargetId())
                && flight.vectorFlight() != null && flight.vectorFlight().trajectory().gravity() != null
                && flight.vectorFlight().trajectory().gravity().frame().kind() != OrbitalFlight.CenterKind.STELLAR)
            return BodyFleetRendezvous.matches(state, hunter, target);
        if (finished(hunter) && !flight.interrupted() && target.id().equals(flight.rendezvousTargetId())
                && target.location().orbitalFlight() != null) return OrbitalRendezvousPlanner.matches(state, hunter, target);
        return finished(hunter) && !flight.interrupted() && target.id().equals(flight.rendezvousTargetId())
                && other != null && flight.geometry().equals(other.geometry())
                && VectorFlight.norm(VectorFlight.subtract(com.spaceconquest.engine.galaxy.SensorPosition.at(state, hunter.currentSystemId(), hunter.location()),
                com.spaceconquest.engine.galaxy.SensorPosition.at(state, target.currentSystemId(), target.location()))) <= 1
                && VectorFlight.norm(VectorFlight.subtract(VectorFlight.velocity(flight), VectorFlight.velocity(other))) <= .001;
    }

    public static Set<String> participants(GameState state) {
        var ids = new HashSet<String>();
        for (var hunter : state.fleets()) {
            if (hunter.interceptOrder() == null || !finished(hunter)) continue;
            var target = FleetOrganization.find(state, hunter.interceptOrder().targetFleetId());
            if (matches(state, hunter, target)) { ids.add(hunter.id()); ids.add(target.id()); }
        }
        return Set.copyOf(ids);
    }

    /** Completed or failed approaches become real drift eligible for ordinary local recovery. */
    public static Fleet release(Fleet fleet) {
        var flight = fleet.location().localFlight();
        return fleet.withInterceptOrder(null).withLocation(fleet.location().withFlight(new LocalFlight(flight.geometry(),
                flight.motion(), flight.propellantKg(), flight.propulsion(), true, flight.advancedInPowerTick(), null, flight.vectorFlight())));
    }
}
