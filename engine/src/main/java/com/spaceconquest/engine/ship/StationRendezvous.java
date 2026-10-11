package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import java.util.HashMap;
import static com.spaceconquest.engine.ship.VectorFlight.*;

/** Funded docking from an actual body-relative coast using the shared gravity solver. */
public final class StationRendezvous {
    public record Motion(LocalSiteGeometry.Point position, LocalSiteGeometry.Point velocity) {}
    private StationRendezvous() {}

    public static Motion at(OrbitalBody body, double altitudeKm, double day) {
        return at(body, altitudeKm, day, Integer.toUnsignedLong((body.id() + ":parking").hashCode()) / 4294967296.0 * 2 * Math.PI);
    }

    private static Motion at(OrbitalBody body, double altitudeKm, double day, double epochPhase) {
        double radius = (body.diameter() / 2 + altitudeKm) * 1000;
        double speed = Math.sqrt(PlanetaryTransferComparison.GRAVITATIONAL_CONSTANT * body.mass() / radius);
        double period = 2 * Math.PI * radius / speed;
        double phase = epochPhase + (day * 86400.0 % period) / period * 2 * Math.PI;
        return new Motion(new LocalSiteGeometry.Point(radius * Math.cos(phase), radius * Math.sin(phase), 0),
                new LocalSiteGeometry.Point(-speed * Math.sin(phase), speed * Math.cos(phase), 0));
    }

    /** Station phase is saved at campaign day zero and shared by sensors and physical docking. */
    public static Motion at(GameState state, String stationId, double day) {
        var station = state.orbitalStations().stream().filter(item -> item.id().equals(stationId)).findFirst().orElse(null);
        if (station == null) return null;
        var body = OrbitalBody.find(state, station.systemId(), station.planetOrbitId());
        return body == null ? null : at(body, station.effectiveParkingAltitudeKm(), day, station.orbitalPhaseRadians());
    }

    public static LocalTravel.Plan plan(GameState state, Fleet fleet, FleetLocation.Site destination) {
        if (state == null || fleet == null || destination == null || destination.kind() != FleetLocation.Kind.DOCKED
                || !RecoveredParkingTravel.ready(fleet)) return null;
        var actual = OrbitalRecovery.initialState(state, fleet);
        var parking = OrbitalTravel.parking(state, fleet.currentSystemId(), destination);
        if (actual == null || parking == null || !parking.body().frame().equals(actual.gravity().frame())
                || FleetSupplySimulation.hasOrders(fleet) || fleet.hasInterstellarOrder() || fleet.isInWarp()
                || fleet.ships().isEmpty()) return null;
        if (state.orbitalStations().stream().noneMatch(station -> station.id().equals(destination.entityId())
                && station.systemId().equals(fleet.currentSystemId()) && station.isOperational())) return null;
        double radius = (parking.body().diameter() / 2 + parking.altitudeKm()) * 1000;
        var gravity = actual.gravity();
        if (radius <= gravity.minimumRadiusMeters() || radius >= gravity.maximumRadiusMeters()) return null;
        double speed = Math.sqrt(gravity.gravitationalParameter() / radius), period = 2 * Math.PI * radius / speed;
        var capabilities = new HashMap<String, LocalSpaceTravel.Capability>();
        double acceleration = Double.POSITIVE_INFINITY, deltaV = Double.POSITIVE_INFINITY;
        for (var ship : fleet.ships()) {
            var capability = LocalSpaceTravel.capability(state, fleet, ship, destination, fleet.fuelPolicy().reserveKg(state, fleet, ship));
            if (capability == null || ship.currentHullHealth() <= 0) return null;
            capabilities.put(ship.id(), capability);
            acceleration = Math.min(acceleration, capability.accelerationMps2()); deltaV = Math.min(deltaV, capability.deltaVMps());
        }
        for (int candidate = 0; candidate < 48; candidate++) {
            double seconds = period / 1024 * Math.pow(1.15, candidate);
            if (seconds > 7 * 86400) break;
            var station = at(state, destination.entityId(), state.turn() + seconds / 86400);
            var goal = station.position(); var velocity = station.velocity();
            var trajectory = OrbitalRendezvousPlanner.shoot(actual.position(), actual.velocity(), goal, velocity,
                    seconds, gravity, acceleration, deltaV);
            if (trajectory == null) continue;
            var geometry = new LocalSiteGeometry.Leg(actual.position(), goal, Math.max(1, norm(subtract(goal, actual.position()))));
            var physical = VectorInterceptPlanner.budget(state, fleet, geometry, trajectory, capabilities);
            var plan = new LocalTravel.Plan(physical.days(), physical.propellantKg(), physical.reactorFuelKg(), physical);
            if (ShipPowerForecast.ready(ShipPowerForecast.departure(state, fleet, destination, plan, null))) return plan;
        }
        return OrbitalPhasing.plan(state, fleet, destination, parking, actual, capabilities, acceleration, deltaV);
    }

    static boolean approach(Fleet fleet) {
        var flight = fleet.location().localFlight();
        return fleet.location().destination().kind() == FleetLocation.Kind.DOCKED && flight != null
                && flight.rendezvousTargetId() == null && flight.vectorFlight() != null
                && flight.vectorFlight().trajectory().gravity() != null;
    }

    static boolean contact(VectorFlight actual, LocalSiteGeometry.Point goal) {
        double radius = norm(goal), speed = Math.sqrt(actual.trajectory().gravity().gravitationalParameter() / radius);
        var velocity = new LocalSiteGeometry.Point(-goal.yMeters() * speed / radius, goal.xMeters() * speed / radius, 0);
        return norm(subtract(actual.position(), goal)) <= 1 && norm(subtract(actual.velocity(), velocity)) <= .001;
    }

    static boolean valid(GameState state, Fleet fleet) {
        var target = fleet.location().destination();
        var parking = OrbitalTravel.parking(state, fleet.currentSystemId(), target);
        var flight = fleet.location().localFlight(); var actual = flight.vectorFlight();
        if (parking == null || !parking.body().frame().equals(actual.trajectory().gravity().frame())
                || state.orbitalStations().stream().noneMatch(station -> station.id().equals(target.entityId())
                && station.systemId().equals(fleet.currentSystemId()) && station.isOperational())) return false;
        double remaining = Math.max(0, actual.trajectory().totalSeconds() - actual.elapsedSeconds());
        var predicted = at(state, target.entityId(), state.turn() + remaining / 86400);
        return norm(subtract(predicted.position(), flight.geometry().destination())) <= 1;
    }
}
