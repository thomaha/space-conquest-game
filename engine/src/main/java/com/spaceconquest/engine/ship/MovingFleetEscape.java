package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Race;
import com.spaceconquest.engine.galaxy.SensorPosition;
import com.spaceconquest.engine.habitation.PassengerTransitProcessor;
import java.util.HashMap;
import java.util.List;
import static com.spaceconquest.engine.ship.VectorFlight.*;

/** Read-only moving escape previews; a new funded plan replaces thrust only when the group commits. */
public final class MovingFleetEscape {
    private MovingFleetEscape() {}

    public static boolean supported(Fleet fleet) {
        var orbital = fleet.location().orbitalFlight();
        return fleet.location().localFlight() != null || orbital != null && orbital.nextManeuver() == 1
                && orbital.elapsedSeconds() >= orbital.itinerary().waitSeconds();
    }

    public static Fleet interrupted(Fleet fleet) {
        if (fleet.location().orbitalFlight() != null || OrbitalRecovery.applies(fleet)) return OrbitalRecovery.cancel(fleet);
        var flight = fleet.location().localFlight();
        return fleet.withLocation(fleet.location().withFlight(new LocalFlight(flight.geometry(), flight.motion(),
                flight.propellantKg(), flight.propulsion(), true, flight.advancedInPowerTick(), null, flight.vectorFlight())));
    }

    public static OrbitalBody body(GameState state, Fleet fleet) {
        if (!fleet.location().inTransit()) {
            var parking = OrbitalTravel.parking(state, fleet.currentSystemId(), fleet.location().current());
            return parking == null ? null : parking.body();
        }
        var actual = supported(fleet) && OrbitalRecovery.applies(interrupted(fleet))
                ? OrbitalRecovery.initialState(state, interrupted(fleet)) : null;
        return actual == null || actual.gravity().frame().kind() == OrbitalFlight.CenterKind.STELLAR ? null
                : OrbitalBody.find(state, fleet.currentSystemId(), actual.gravity().frame().centerBodyId());
    }

    public static boolean together(GameState state, Fleet first, Fleet second) {
        if (first.id().equals(second.id())) return true;
        if (!first.location().inTransit() || !second.location().inTransit())
            return !first.location().inTransit() && !second.location().inTransit()
                    && first.location().current().equals(second.location().current());
        var firstFrame = frame(first); var lastFrame = frame(second);
        if (!java.util.Objects.equals(firstFrame, lastFrame)) return false;
        var a = motion(state, first, firstFrame, 0, true); var b = motion(state, second, firstFrame, 0, true);
        return a != null && b != null && norm(subtract(a.position(), b.position())) <= 1
                && norm(subtract(a.velocity(), b.velocity())) <= .001;
    }

    private static OrbitalFlight.Frame frame(Fleet fleet) {
        var local = fleet.location().localFlight(); var orbital = fleet.location().orbitalFlight();
        return orbital != null ? orbital.itinerary().frame() : local != null && local.vectorFlight() != null
                && local.vectorFlight().trajectory().gravity() != null ? local.vectorFlight().trajectory().gravity().frame() : null;
    }

    private static StationRendezvous.Motion motion(GameState state, Fleet fleet, OrbitalFlight.Frame requested,
                                                   double seconds, boolean controlled) {
        if (!fleet.location().inTransit()) {
            if (requested == null) {
                var p = SensorPosition.at(state, fleet.currentSystemId(), fleet.location());
                return p == null ? null : new StationRendezvous.Motion(p, new LocalSiteGeometry.Point(0, 0, 0));
            }
            var parking = OrbitalTravel.parking(state, fleet.currentSystemId(), fleet.location().current());
            if (parking == null || !parking.body().frame().equals(requested)) return null;
            return fleet.location().current().kind() == FleetLocation.Kind.DOCKED
                    ? StationRendezvous.at(state, fleet.location().current().entityId(), state.turn() + seconds / 86400)
                    : StationRendezvous.at(parking.body(), parking.altitudeKm(), state.turn() + seconds / 86400);
        }
        if (!java.util.Objects.equals(requested, frame(fleet))) return null;
        var orbital = fleet.location().orbitalFlight(); var local = fleet.location().localFlight();
        if (orbital != null && supported(fleet)) {
            var p = orbital.itinerary().coast().at(orbital.elapsedSeconds() - orbital.itinerary().waitSeconds() + seconds);
            return new StationRendezvous.Motion(new LocalSiteGeometry.Point(p.xMeters(), p.yMeters(), 0),
                    new LocalSiteGeometry.Point(p.vxMps(), p.vyMps(), 0));
        }
        if (local == null) return null;
        if (local.vectorFlight() != null) {
            var p = local.vectorFlight().advance(seconds, local.interrupted() || !controlled);
            return new StationRendezvous.Motion(p.position(), p.velocity());
        }
        if (controlled && !local.interrupted()) {
            var predicted = local.motion().trajectory().at(local.motion().elapsedSeconds() + seconds);
            var leg = local.geometry(); var direction = scale(subtract(leg.destination(), leg.origin()), 1 / leg.distanceMeters());
            return new StationRendezvous.Motion(add(leg.origin(), scale(direction, predicted.positionMeters())),
                    scale(direction, predicted.velocityMps()));
        }
        var p = SensorPosition.at(state, fleet.currentSystemId(), fleet.location()); var v = velocity(local);
        return p == null ? null : new StationRendezvous.Motion(add(p, scale(v, seconds)), v);
    }

    public static boolean continuesSafely(GameState state, Fleet fleet, FleetThreatAssessment.Assessment assessment, List<Race> races) {
        if (!supported(fleet)) return false;
        var destination = fleet.location().destination();
        if (destination.kind() == FleetLocation.Kind.DOCKED && state.orbitalStations().stream().noneMatch(station ->
                destination.entityId().equals(station.id()) && fleet.currentSystemId().equals(station.systemId()) && station.isOperational())) return false;
        var local = fleet.location().localFlight(); var orbital = fleet.location().orbitalFlight();
        double seconds = orbital != null ? orbital.itinerary().waitSeconds() + orbital.itinerary().coastDurationSeconds() - orbital.elapsedSeconds()
                : local.motion().trajectory().totalSeconds() - local.motion().elapsedSeconds();
        if (seconds <= 0 || seconds > 30 * 86400 || local != null && local.interrupted()
                || orbital != null && orbital.status() == OrbitalFlight.Status.MISSED) return false;
        try {
            if (!LocalSpacePowerForecast.remainingReady(state, fleet, seconds)
                    || !PassengerTransitProcessor.canSustainJourney(state, fleet, races, seconds / 86400)) return false;
            var start = motion(state, fleet, frame(fleet), 0, true); var end = motion(state, fleet, frame(fleet), seconds, true);
            if (start == null || end == null) return false;
            for (String id : assessment.hostileFleetIds()) {
                var enemy = FleetOrganization.find(state, id);
                var now = motion(state, enemy, frame(fleet), 0, false); var later = motion(state, enemy, frame(fleet), seconds, false);
                if (now == null || later == null || norm(subtract(end.position(), later.position()))
                        <= norm(subtract(start.position(), now.position())) + 1_000_000) return false;
            }
            return true;
        } catch (IllegalArgumentException outsideDomain) { return false; }
    }

    public static LocalTravel.Plan plan(GameState state, Fleet fleet, FleetLocation.Site destination) {
        var canceled = interrupted(fleet);
        var actual = OrbitalRecovery.initialState(state, canceled);
        if (actual == null) return VectorInterceptPlanner.escape(state, canceled, destination);
        var parking = OrbitalTravel.parking(state, fleet.currentSystemId(), destination);
        if (parking == null || !parking.body().frame().equals(actual.gravity().frame())) return null;
        var capabilities = new HashMap<String, LocalSpaceTravel.Capability>();
        double acceleration = Double.POSITIVE_INFINITY, deltaV = Double.POSITIVE_INFINITY;
        for (var ship : fleet.ships()) {
            var capability = LocalSpaceTravel.capability(state, canceled, ship, destination, fleet.fuelPolicy().reserveKg(state, fleet, ship));
            if (capability == null || ship.currentHullHealth() <= 0) return null;
            capabilities.put(ship.id(), capability); acceleration = Math.min(acceleration, capability.accelerationMps2());
            deltaV = Math.min(deltaV, capability.deltaVMps());
        }
        double radius = (parking.body().diameter() / 2 + parking.altitudeKm()) * 1000;
        double period = 2 * Math.PI * Math.sqrt(radius / actual.gravity().gravitationalParameter()) * radius;
        for (int candidate = 0; candidate < 48; candidate++) {
            double seconds = period / 1024 * Math.pow(1.15, candidate);
            if (seconds > 7 * 86400) break;
            try {
                var goal = destination.kind() == FleetLocation.Kind.DOCKED
                        ? StationRendezvous.at(state, destination.entityId(), state.turn() + seconds / 86400)
                        : StationRendezvous.at(parking.body(), parking.altitudeKm(), state.turn() + seconds / 86400);
                if (goal == null) return null;
                var trajectory = OrbitalRendezvousPlanner.shoot(actual.position(), actual.velocity(), goal.position(), goal.velocity(),
                        seconds, actual.gravity(), acceleration, deltaV);
                if (trajectory == null) continue;
                var geometry = new LocalSiteGeometry.Leg(actual.position(), goal.position(), Math.max(1, norm(subtract(goal.position(), actual.position()))));
                var physical = VectorInterceptPlanner.budget(state, canceled, geometry, trajectory, capabilities);
                var plan = new LocalTravel.Plan(physical.days(), physical.propellantKg(), physical.reactorFuelKg(), physical);
                if (ShipPowerForecast.ready(ShipPowerForecast.departure(state, canceled, destination, plan, null))) return plan;
            } catch (IllegalArgumentException outsideDomain) { /* A frame transition is not an escape maneuver. */ }
        }
        return null;
    }

    public static Fleet depart(Fleet fleet, FleetLocation.Site destination, LocalTravel.Plan plan) {
        var canceled = interrupted(fleet);
        if (plan.physical().vectorTrajectory().gravity() != null) return LocalTravel.depart(canceled, destination, plan);
        var planning = canceled.withLocation(new FleetLocation(canceled.location().current(), destination, 0, plan.days(),
                canceled.location().localFlight(), canceled.location().orbitalFlight()));
        return LocalSpaceTravel.resume(planning, plan.physical());
    }

    public static boolean improvesSafety(GameState state, Fleet fleet, LocalTravel.Plan plan,
                                         FleetThreatAssessment.Assessment assessment) {
        var trajectory = plan.physical().vectorTrajectory(); var requested = trajectory.gravity() == null ? null : trajectory.gravity().frame();
        double seconds = trajectory.totalSeconds();
        var start = motion(state, fleet, requested, 0, true);
        if (start == null) return false;
        try {
            var end = trajectory.at(seconds);
            for (String id : assessment.hostileFleetIds()) {
                var enemy = FleetOrganization.find(state, id);
                var now = motion(state, enemy, requested, 0, false); var later = motion(state, enemy, requested, seconds, false);
                if (now == null || later == null || norm(subtract(end.position(), later.position()))
                        <= norm(subtract(start.position(), now.position())) + 1_000_000) return false;
            }
            return true;
        } catch (IllegalArgumentException outsideDomain) { return false; }
    }
}
