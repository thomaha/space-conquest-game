package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import java.util.HashMap;
import static com.spaceconquest.engine.ship.VectorFlight.*;

/** Same-body pursuit from actual parking or interrupted coast, with funded finite burns. */
public final class BodyFleetRendezvous {
    private BodyFleetRendezvous() {}

    public static boolean ballisticAvailable(Fleet fleet) {
        if (fleet == null) return false;
        var local = fleet.location().localFlight(); var orbital = fleet.location().orbitalFlight();
        return local != null && local.interrupted() && local.vectorFlight() != null
                && local.vectorFlight().trajectory().gravity() != null
                && local.vectorFlight().trajectory().gravity().frame().kind() != OrbitalFlight.CenterKind.STELLAR
                || orbital != null && orbital.nextManeuver() == 1 && orbital.status() == OrbitalFlight.Status.MISSED
                && orbital.elapsedSeconds() >= orbital.itinerary().waitSeconds() && orbital.itinerary().bodyCentered();
    }

    static OrbitalRecovery.InitialState initial(GameState state, Fleet fleet) {
        if (fleet.location().localFlight() != null) return OrbitalRecovery.initialState(state, fleet);
        var orbital = fleet.location().orbitalFlight();
        var parking = orbital == null ? OrbitalTravel.parking(state, fleet.currentSystemId(), fleet.location().current()) : null;
        var body = orbital == null ? parking == null ? null : parking.body()
                : OrbitalBody.find(state, fleet.currentSystemId(), orbital.itinerary().frame().centerBodyId());
        var system = state.solarSystems().stream().filter(item -> item.id().equals(fleet.currentSystemId())).findFirst().orElse(null);
        if (body == null || system == null || fleet.location().current().kind() == FleetLocation.Kind.SURFACE) return null;
        double mu = PlanetaryTransferComparison.GRAVITATIONAL_CONSTANT * body.mass();
        var gravity = new TwoBodyFlight.Gravity(mu, body.diameter() * 500, body.frame(), body.sphereKm(system) * 1000);
        if (orbital != null) {
            if (!body.frame().equals(orbital.itinerary().frame())
                    || Math.abs(orbital.itinerary().coast().gravitationalParameter() / mu - 1) > 1e-9) return null;
            var p = orbital.position();
            return new OrbitalRecovery.InitialState(new LocalSiteGeometry.Point(p.xMeters(), p.yMeters(), 0),
                    new LocalSiteGeometry.Point(p.vxMps(), p.vyMps(), 0), gravity);
        }
        var motion = fleet.location().current().kind() == FleetLocation.Kind.DOCKED
                ? StationRendezvous.at(state, fleet.location().current().entityId(), state.turn())
                : StationRendezvous.at(body, parking.altitudeKm(), state.turn());
        return motion == null ? null : new OrbitalRecovery.InitialState(motion.position(), motion.velocity(), gravity);
    }

    static double horizon(Fleet target) {
        if (!target.location().inTransit() && target.location().current().kind() == FleetLocation.Kind.ORBIT) return 7 * 86400;
        var orbital = target.location().orbitalFlight();
        if (orbital != null) {
            if (!orbital.itinerary().bodyCentered() || orbital.nextManeuver() != 1 || orbital.elapsedSeconds() < orbital.itinerary().waitSeconds()) return 0;
            if (orbital.status() == OrbitalFlight.Status.MISSED) return 7 * 86400;
            return orbital.status() == OrbitalFlight.Status.COAST ? Math.min(7 * 86400,
                    orbital.itinerary().waitSeconds() + orbital.itinerary().coastDurationSeconds() - orbital.elapsedSeconds()) : 0;
        }
        var local = target.location().localFlight();
        if (local == null || local.vectorFlight() == null || local.vectorFlight().trajectory().gravity() == null) return 0;
        if (local.interrupted()) return 7 * 86400;
        var vector = local.vectorFlight(); var trajectory = vector.trajectory();
        return vector.elapsedSeconds() >= trajectory.burnSeconds() ? Math.min(7 * 86400,
                trajectory.burnSeconds() + trajectory.coastSeconds() - vector.elapsedSeconds()) : 0;
    }

    static StationRendezvous.Motion predicted(GameState state, Fleet target, double seconds) {
        if (!target.location().inTransit()) {
            var parking = OrbitalTravel.parking(state, target.currentSystemId(), target.location().current());
            return StationRendezvous.at(parking.body(), parking.altitudeKm(), state.turn() + seconds / 86400);
        }
        var orbital = target.location().orbitalFlight();
        if (orbital != null) {
            var p = orbital.itinerary().coast().at(orbital.elapsedSeconds() - orbital.itinerary().waitSeconds() + seconds);
            return new StationRendezvous.Motion(new LocalSiteGeometry.Point(p.xMeters(), p.yMeters(), 0),
                    new LocalSiteGeometry.Point(p.vxMps(), p.vyMps(), 0));
        }
        var local = target.location().localFlight();
        var p = local.vectorFlight().advance(seconds, local.interrupted());
        return new StationRendezvous.Motion(p.position(), p.velocity());
    }

    public static LocalTravel.Plan plan(GameState state, Fleet hunter, Fleet target) {
        if (state == null || hunter == null || target == null || hunter.ships().isEmpty()
                || !hunter.currentSystemId().equals(target.currentSystemId()) || hunter.hasInterstellarOrder() || hunter.isInWarp()
                || hunter.location().inTransit() && !ballisticAvailable(hunter)) return null;
        double horizon = horizon(target);
        var destination = target.location().inTransit() ? target.location().destination() : target.location().current();
        var actual = initial(state, hunter); var observed = initial(state, target);
        if (horizon < 86400 || actual == null || observed == null
                || !actual.gravity().frame().equals(observed.gravity().frame())
                || actual.gravity().frame().kind() == OrbitalFlight.CenterKind.STELLAR) return null;
        var capabilities = new HashMap<String, LocalSpaceTravel.Capability>();
        double acceleration = Double.POSITIVE_INFINITY, deltaV = Double.POSITIVE_INFINITY;
        for (var ship : hunter.ships()) {
            var capability = LocalSpaceTravel.capability(state, hunter, ship, destination, hunter.fuelPolicy().reserveKg(state, hunter, ship));
            if (capability == null || ship.currentHullHealth() <= 0) return null;
            capabilities.put(ship.id(), capability);
            acceleration = Math.min(acceleration, capability.accelerationMps2()); deltaV = Math.min(deltaV, capability.deltaVMps());
        }
        double radius = norm(actual.position()), period = 2 * Math.PI * Math.sqrt(radius / actual.gravity().gravitationalParameter()) * radius;
        if (radius <= actual.gravity().minimumRadiusMeters() || radius >= actual.gravity().maximumRadiusMeters()) return null;
        for (int candidate = 0; candidate < 48; candidate++) {
            double seconds = period / 1024 * Math.pow(1.15, candidate), coordinated = Math.ceil(seconds / 86400) * 86400;
            if (coordinated > horizon) break;
            try {
                var goal = predicted(state, target, seconds);
                var trajectory = OrbitalRendezvousPlanner.shoot(actual.position(), actual.velocity(), goal.position(), goal.velocity(),
                        seconds, actual.gravity(), acceleration, deltaV);
                if (trajectory == null) continue;
                var end = trajectory.at(coordinated); var coordinatedGoal = predicted(state, target, coordinated);
                if (norm(subtract(end.position(), coordinatedGoal.position())) > .5 || norm(subtract(end.velocity(), coordinatedGoal.velocity())) > .0005) continue;
                var geometry = new LocalSiteGeometry.Leg(actual.position(), goal.position(), Math.max(1, norm(subtract(goal.position(), actual.position()))));
                var physical = VectorInterceptPlanner.budget(state, hunter, geometry, trajectory, capabilities);
                var plan = new LocalTravel.Plan(physical.days(), physical.propellantKg(), physical.reactorFuelKg(), physical);
                if (ShipPowerForecast.ready(ShipPowerForecast.departure(state, hunter, destination, plan, null))) return plan;
            } catch (IllegalArgumentException outsideDomain) { /* No frame transition or collision is invented. */ }
        }
        if (!target.location().inTransit() && destination.kind() == FleetLocation.Kind.ORBIT) {
            var parking = OrbitalTravel.parking(state, target.currentSystemId(), destination);
            var plan = OrbitalPhasing.plan(state, hunter, destination, parking, actual, capabilities, acceleration, deltaV);
            if (plan != null) {
                double coordinated = Math.ceil(plan.days()) * 86400;
                var end = plan.physical().vectorTrajectory().at(coordinated); var goal = predicted(state, target, coordinated);
                if (norm(subtract(end.position(), goal.position())) <= .5 && norm(subtract(end.velocity(), goal.velocity())) <= .0005) return plan;
            }
        }
        return null;
    }

    public static boolean matches(GameState state, Fleet hunter, Fleet target) {
        var first = initial(state, hunter); var last = initial(state, target);
        return first != null && last != null && first.gravity().frame().kind() != OrbitalFlight.CenterKind.STELLAR
                && first.gravity().frame().equals(last.gravity().frame())
                && norm(subtract(first.position(), last.position())) <= 1 && norm(subtract(first.velocity(), last.velocity())) <= .001;
    }
}
