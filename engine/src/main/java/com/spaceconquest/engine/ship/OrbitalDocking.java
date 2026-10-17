package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static com.spaceconquest.engine.ship.VectorFlight.*;

/** Connects funded native capture to a saved, separately funded station rendezvous. */
public final class OrbitalDocking {
    public record Approach(double departureSeconds, LocalSpaceTravel.Plan physical, ShipSolarEnvironment environment) {
        public Approach {
            if (!Double.isFinite(departureSeconds) || departureSeconds < 0 || physical == null || environment == null
                    || physical.vectorTrajectory() == null || physical.vectorTrajectory().gravity() == null)
                throw new IllegalArgumentException("Invalid saved station approach");
        }
        public Approach retain(List<ShipInstance> ships) { return new Approach(departureSeconds, physical.retain(ships), environment); }
        public Approach join(Approach other) {
            if (departureSeconds != other.departureSeconds || !environment.equals(other.environment))
                throw new IllegalArgumentException("Station approach timing or environment differs");
            return new Approach(departureSeconds, physical.join(other.physical()), environment);
        }
    }
    private OrbitalDocking() {}

    static LocalTravel.Plan stationary(GameState state, Fleet fleet, FleetLocation.Site target, OrbitalTravel.Parking from) {
        return stationary(state, fleet, target, from, false);
    }
    static LocalTravel.Plan stationary(GameState state, Fleet fleet, FleetLocation.Site target, OrbitalTravel.Parking from, boolean economical) {
        var motion = fleet.location().current().kind() == FleetLocation.Kind.DOCKED
                ? StationRendezvous.at(state, fleet.location().current().entityId(), state.turn())
                : StationRendezvous.at(from.body(), from.altitudeKm(), state.turn());
        return StationRendezvous.plan(state, coast(state, fleet, from.body(), motion.position(), motion.velocity(), false), target, economical);
    }

    static LocalTravel.Plan plan(GameState state, Fleet fleet, FleetLocation.Site target, LocalTravel.Plan nativePlan) {
        return plan(state, fleet, target, nativePlan, false);
    }
    static LocalTravel.Plan plan(GameState state, Fleet fleet, FleetLocation.Site target, LocalTravel.Plan nativePlan, boolean economical) {
        var original = nativePlan.orbital();
        double departure = Math.max(86400, Math.ceil(original.maneuvers().get(1).seconds() / 86400) * 86400);
        var projected = fleet.withShips(fleet.ships().stream().map(ship -> OrbitalPowerAccounting.captureProjection(ship,
                FleetSupplySimulation.design(state, ship), original, departure).ship()).toList());
        var parked = captured(state, projected, original, departure, false);
        var future = state.withTurn(state.turn() + Math.round(departure / 86400));
        var approach = StationRendezvous.plan(future, parked, target, economical);
        if (approach == null) throw new IllegalArgumentException("No funded station rendezvous after orbital capture");
        var itinerary = original.withDocking(new Approach(departure, approach.physical(), ShipSolarEnvironment.journey(future, parked, target)));
        var fuel = new HashMap<String, Double>();
        var reactor = new HashMap<String, InterstellarTravel.ReactorFuelUse>();
        for (var event : itinerary.maneuvers().subList(0, 2)) event.burns().forEach((id, burn) -> {
            fuel.merge(id, burn.propellantKg(), Double::sum);
            if (burn.reactorMaterialId() != null) addFeed(reactor, id, burn.reactorMaterialId(), burn.propellantKg() * burn.reactorKgPerPropellantKg());
        });
        approach.propellantKg().forEach((id, kg) -> fuel.merge(id, kg, Double::sum));
        approach.reactorFuelKg().forEach((id, feed) -> addFeed(reactor, id, feed.materialId(), feed.quantityKg()));
        return new LocalTravel.Plan(itinerary.totalSeconds() / 86400, fuel, reactor, null, itinerary);
    }

    private static void addFeed(Map<String, InterstellarTravel.ReactorFuelUse> feeds, String id, String material, double kg) {
        var prior = feeds.get(id);
        if (prior != null && !prior.materialId().equals(material))
            throw new IllegalArgumentException("Native capture and station approach require the same drive feed");
        feeds.put(id, new InterstellarTravel.ReactorFuelUse(material, kg + (prior == null ? 0 : prior.quantityKg())));
    }

    /** Native capture fixes a circular orbit; its local phase is independent of station phase. */
    static Fleet captured(GameState state, Fleet fleet, OrbitalFlight.Itinerary itinerary, double seconds, boolean advanced) {
        var body = OrbitalBody.find(state, fleet.currentSystemId(), itinerary.targetBodyId());
        double radius = (body.diameter() / 2 + itinerary.targetAltitudeKm()) * 1000;
        double speed = Math.sqrt(PlanetaryTransferComparison.GRAVITATIONAL_CONSTANT * body.mass() / radius);
        double capture = itinerary.maneuvers().get(1).seconds();
        double phase;
        if (itinerary.parkingTransfer()) phase = itinerary.coast().departureAngleRadians() + Math.PI;
        else {
            var incoming = itinerary.coast().at(itinerary.coastDurationSeconds());
            phase = Math.atan2(incoming.vyMps(), incoming.vxMps()) - Math.PI / 2;
        }
        phase += speed / radius * (seconds - capture);
        var point = new LocalSiteGeometry.Point(radius * Math.cos(phase), radius * Math.sin(phase), 0);
        var velocity = new LocalSiteGeometry.Point(-speed * Math.sin(phase), speed * Math.cos(phase), 0);
        return coast(state, fleet, body, point, velocity, advanced);
    }

    private static Fleet coast(GameState state, Fleet fleet, OrbitalBody body, LocalSiteGeometry.Point point,
                               LocalSiteGeometry.Point velocity, boolean advanced) {
        var system = state.solarSystems().stream().filter(item -> item.id().equals(fleet.currentSystemId())).findFirst().orElseThrow();
        var gravity = new TwoBodyFlight.Gravity(PlanetaryTransferComparison.GRAVITATIONAL_CONSTANT * body.mass(),
                body.diameter() * 500, body.frame(), body.sphereKm(system) * 1000);
        return coast(fleet, point, velocity, gravity, advanced);
    }

    private static Fleet coast(Fleet fleet, LocalSiteGeometry.Point point, LocalSiteGeometry.Point velocity,
                               TwoBodyFlight.Gravity gravity, boolean advanced) {
        var tiny = new LocalSiteGeometry.Point(1e-30, 0, 0);
        var vector = new VectorFlight(new VectorFlight.Trajectory(point, velocity, tiny, tiny, 1, 0, gravity), point, velocity, 2);
        var geometry = new LocalSiteGeometry.Leg(point, add(point, velocity), Math.max(1, norm(velocity)));
        var flight = new LocalFlight(geometry, VectorFlight.motion(vector, geometry), Map.of(), Map.of(), true, advanced, null, vector);
        var site = FleetLocation.Site.orbit(gravity.frame().centerBodyId(), (norm(point) - gravity.minimumRadiusMeters()) / 1000);
        return fleet.withLocation(new FleetLocation(FleetLocation.Site.orbit(gravity.frame().centerBodyId()), site, 0, 1, flight));
    }

    static Fleet launch(GameState state, Fleet fleet, OrbitalFlight.Itinerary itinerary) {
        var physical = itinerary.docking().physical();
        var saved = physical.vectorTrajectory();
        var parked = coast(fleet, saved.start(), saved.initialVelocity(), saved.gravity(), true);
        var plan = new LocalTravel.Plan(physical.days(), physical.propellantKg(), physical.reactorFuelKg(), physical);
        var destination = fleet.location().destination();
        var future = state.withTurn(state.turn() + 1);
        for (var ship : fleet.ships()) {
            var propulsion = physical.propulsion().get(ship.id());
            var feed = physical.reactorFuelKg().get(ship.id());
            if (propulsion == null || ship.currentFuelKg() + 1e-6 < physical.propellantKg().getOrDefault(ship.id(), 0.0)
                    + propulsion.protectedPropellantKg() || feed != null
                    && ship.storedCargoKg().getOrDefault(feed.materialId(), 0.0) + 1e-6 < feed.quantityKg()) return parked;
        }
        if (!ShipPowerForecast.ready(ShipPowerForecast.departure(future, parked, destination, plan, null))) return parked;
        var moved = LocalTravel.depart(parked, destination, plan);
        if (!StationRendezvous.valid(future, moved)) return parked;
        var flight = moved.location().localFlight();
        return moved.withLocation(moved.location().withFlight(new LocalFlight(flight.geometry(), flight.motion(), flight.propellantKg(),
                flight.propulsion(), false, true, null, flight.vectorFlight())));
    }

    static Fleet cancel(Fleet fleet) {
        var flight = fleet.location().orbitalFlight(); var approach = flight.itinerary().docking();
        var saved = approach.physical().vectorTrajectory(); var p = saved.start(); var v = saved.initialVelocity();
        double radius = norm(p);
        double angle = Math.sqrt(saved.gravity().gravitationalParameter() / radius) / radius
                * (flight.elapsedSeconds() - approach.departureSeconds());
        double c = Math.cos(angle), s = Math.sin(angle);
        return coast(fleet, new LocalSiteGeometry.Point(p.xMeters() * c - p.yMeters() * s, p.xMeters() * s + p.yMeters() * c, 0),
                new LocalSiteGeometry.Point(v.xMeters() * c - v.yMeters() * s, v.xMeters() * s + v.yMeters() * c, 0), saved.gravity(), false);
    }
}
