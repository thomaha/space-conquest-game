package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import java.util.Map;
import static com.spaceconquest.engine.ship.VectorFlight.*;

/** Connects completed circular body recovery to the ordinary funded parking planner. */
public final class RecoveredParkingTravel {
    private RecoveredParkingTravel() {}

    public static boolean ready(Fleet fleet) {
        return fleet != null && OrbitalRecovery.applies(fleet) && ready(fleet.location());
    }
    private static boolean ready(FleetLocation location) {
        var flight = location.localFlight();
        if (flight == null || flight.vectorFlight() == null || flight.vectorFlight().trajectory().gravity() == null) return false;
        var actual = flight.vectorFlight(); var gravity = actual.trajectory().gravity();
        if (!flight.interrupted() || gravity.frame().kind() == OrbitalFlight.CenterKind.STELLAR) return false;
        double radius = norm(actual.position());
        if (radius <= gravity.minimumRadiusMeters() || radius >= gravity.maximumRadiusMeters()
                || actual.position().zMeters() != 0 || actual.velocity().zMeters() != 0) return false;
        double speed = Math.sqrt(gravity.gravitationalParameter() / radius);
        var circular = new LocalSiteGeometry.Point(-actual.position().yMeters() * speed / radius, actual.position().xMeters() * speed / radius, 0);
        return norm(subtract(actual.velocity(), circular)) <= .001;
    }
    /** Body recovery frames store the surface radius as their gravity exclusion radius. */
    public static boolean atSite(FleetLocation location, FleetLocation.Site site) {
        if (site == null || site.kind() != FleetLocation.Kind.ORBIT || site.parkingAltitudeKm() == null || !ready(location)) return false;
        var actual = location.localFlight().vectorFlight(); var gravity = actual.trajectory().gravity();
        return site.entityId().equals(gravity.frame().centerBodyId())
                && Math.abs(norm(actual.position()) - gravity.minimumRadiusMeters() - site.parkingAltitudeKm() * 1000) <= .001;
    }

    public static LocalTravel.Plan plan(GameState state, Fleet fleet, FleetLocation.Site target) {
        if (!ready(fleet)) throw new IllegalArgumentException("Complete circular body recovery before planning parking travel");
        var actual = OrbitalRecovery.initialState(state, fleet);
        var to = OrbitalTravel.parking(state, fleet.currentSystemId(), target);
        if (actual == null || to == null || !to.body().frame().equals(actual.gravity().frame()))
            throw new IllegalArgumentException("Recovered parking travel requires a destination around the same body");
        if (target.kind() == FleetLocation.Kind.DOCKED && state.orbitalStations().stream()
                .noneMatch(station -> station.id().equals(target.entityId()) && station.isOperational()))
            throw new IllegalArgumentException("Choose an operational orbital station");
        if (target.kind() == FleetLocation.Kind.DOCKED) {
            var plan = StationRendezvous.plan(state, fleet, target);
            if (plan == null) throw new IllegalArgumentException("No funded station rendezvous from this physical coast");
            return plan;
        }
        double altitude = (norm(actual.position()) - actual.gravity().minimumRadiusMeters()) / 1000;
        if (Math.abs(altitude - to.altitudeKm()) <= .001) altitude = to.altitudeKm();
        var source = FleetLocation.Site.orbit(to.body().id(), altitude);
        var planning = fleet.withLocation(FleetLocation.at(source));
        var from = new OrbitalTravel.Parking(to.body(), altitude, true);
        return ParkingOrbitTravel.plan(state, planning, target, from, to, fleet.location().localFlight().vectorFlight());
    }

    public static Fleet depart(Fleet fleet, FleetLocation.Site target, LocalTravel.Plan plan) {
        var itinerary = plan.orbital();
        return fleet.withLocation(new FleetLocation(FleetLocation.Site.orbit(itinerary.sourceBodyId(), itinerary.sourceAltitudeKm()),
                target, 0, plan.days(), null, new OrbitalFlight(itinerary, 0, 0, OrbitalFlight.Status.WAITING,
                "Funded parking maneuver planned from recovered physical coast")));
    }

    /** Restores physical coast on cancellation or after funded circularization, with no teleport or refund. */
    public static Fleet finish(Fleet fleet, OrbitalFlight.Itinerary itinerary, double elapsedSeconds) {
        var moved = cancel(fleet.withLocation(fleet.location().withOrbitalFlight(new OrbitalFlight(itinerary, elapsedSeconds,
                itinerary.maneuvers().size(), OrbitalFlight.Status.CAPTURED, "Funded parking arrival completed"))));
        var flight = moved.location().localFlight();
        return moved.withLocation(moved.location().withFlight(new LocalFlight(flight.geometry(), flight.motion(), flight.propellantKg(),
                flight.propulsion(), true, true, null, flight.vectorFlight())));
    }

    public static Fleet cancel(Fleet fleet) {
        var orbital = fleet.location().orbitalFlight();
        var saved = orbital.itinerary().sourceMotion();
        if (orbital.atSource()) return coast(fleet, saved);
        var position = orbital.position();
        var point = new LocalSiteGeometry.Point(position.xMeters(), position.yMeters(), 0);
        var velocity = new LocalSiteGeometry.Point(position.vxMps(), position.vyMps(), 0);
        var tiny = new LocalSiteGeometry.Point(1e-30, 0, 0);
        var trajectory = new VectorFlight.Trajectory(point, velocity, tiny, tiny, 1, 0, saved.trajectory().gravity());
        return coast(fleet, new VectorFlight(trajectory, point, velocity, 2));
    }

    private static Fleet coast(Fleet fleet, VectorFlight actual) {
        var end = add(actual.position(), actual.velocity());
        var geometry = new LocalSiteGeometry.Leg(actual.position(), end, Math.max(1, norm(actual.velocity())));
        var flight = new LocalFlight(geometry, VectorFlight.motion(actual, geometry), Map.of(), Map.of(), true, false, null, actual);
        String body = actual.trajectory().gravity().frame().centerBodyId();
        double altitude = (norm(actual.position()) - actual.trajectory().gravity().minimumRadiusMeters()) / 1000;
        return fleet.withLocation(new FleetLocation(FleetLocation.Site.orbit(body), FleetLocation.Site.orbit(body, altitude), 0, 1, flight));
    }
}
