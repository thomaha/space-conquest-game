package com.spaceconquest.engine.galaxy;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.ship.*;

/** Bounded read-only conversion into system-centered meters at the snapshot's campaign day. */
public final class SensorPosition {
    private SensorPosition() {}

    public static LocalSiteGeometry.Point at(GameState state, String systemId, FleetLocation location) {
        if (state == null || systemId == null || location == null) return null;
        var system = state.solarSystems().stream().filter(item -> systemId.equals(item.id())).findFirst().orElse(null);
        if (system == null) return null;
        try {
            var orbital = location.orbitalFlight();
            if (orbital != null) {
                if (orbital.atSource() && orbital.itinerary().sourceMotion() == null) return stationary(state, system, location.current());
                var relative = orbital.position();
                var frame = orbital.itinerary().frame();
                if (frame.kind() == OrbitalFlight.CenterKind.STELLAR)
                    return new LocalSiteGeometry.Point(relative.xMeters(), relative.yMeters(), 0);
                var body = OrbitalBody.find(state, systemId, frame.centerBodyId());
                if (body == null || frame.kind() == OrbitalFlight.CenterKind.PLANETARY && body.moon() != null
                        || frame.kind() == OrbitalFlight.CenterKind.LUNAR && body.moon() == null) return null;
                return add(center(system, body, state.turn()), new LocalSiteGeometry.Point(relative.xMeters(), relative.yMeters(), 0));
            }
            var local = location.localFlight();
            if (local != null) {
                if (local.vectorFlight() != null) {
                    var vector = local.vectorFlight(); var gravity = vector.trajectory().gravity();
                    return gravity == null ? vector.position() : inSystemFrame(state, systemId, gravity.frame(), vector.position());
                }
                var from = local.geometry().origin();
                var to = local.geometry().destination();
                double fraction = local.motion().positionMeters() / local.geometry().distanceMeters();
                return new LocalSiteGeometry.Point(from.xMeters() + (to.xMeters() - from.xMeters()) * fraction,
                        from.yMeters() + (to.yMeters() - from.yMeters()) * fraction,
                        from.zMeters() + (to.zMeters() - from.zMeters()) * fraction);
            }
            return location.inTransit() ? null : stationary(state, system, location.current());
        } catch (IllegalArgumentException invalidGeometry) {
            return null;
        }
    }

    public static LocalSiteGeometry.Point inSystemFrame(GameState state, String systemId, OrbitalFlight.Frame frame,
                                                        LocalSiteGeometry.Point relative) {
        if (frame.kind() == OrbitalFlight.CenterKind.STELLAR) return relative;
        var system = state.solarSystems().stream().filter(item -> systemId.equals(item.id())).findFirst().orElse(null);
        var body = OrbitalBody.find(state, systemId, frame.centerBodyId());
        if (system == null || body == null || !body.frame().equals(frame)) return null;
        return add(center(system, body, state.turn()), relative);
    }

    private static LocalSiteGeometry.Point stationary(GameState state, SolarSystem system, FleetLocation.Site site) {
        String bodyId = site.entityId();
        double altitude = site.kind() == FleetLocation.Kind.SURFACE ? 0 : site.parkingAltitudeKm() == null ? 500 : site.parkingAltitudeKm();
        if (site.kind() == FleetLocation.Kind.DOCKED) {
            var station = state.orbitalStations().stream().filter(item -> site.entityId().equals(item.id())
                    && system.id().equals(item.systemId())).findFirst().orElse(null);
            if (station == null) return null;
            bodyId = station.planetOrbitId();
            altitude = station.effectiveParkingAltitudeKm();
            if (bodyId == null || bodyId.isBlank() || system.id().equals(bodyId))
                return LocalSiteGeometry.position(state, system.id(), site);
        } else if (site.kind() == FleetLocation.Kind.DEEP_SPACE) {
            return LocalSiteGeometry.position(state, system.id(), site);
        }
        var body = OrbitalBody.find(state, system.id(), bodyId);
        if (body == null) return null;
        double radius = (body.diameter() / 2 + altitude) * 1000;
        double mu = PlanetaryTransferComparison.GRAVITATIONAL_CONSTANT * body.mass();
        if (radius <= 0 || mu <= 0) return null;
        var motion = site.kind() == FleetLocation.Kind.DOCKED ? StationRendezvous.at(state, site.entityId(), state.turn())
                : StationRendezvous.at(body, altitude, state.turn());
        return add(center(system, body, state.turn()), motion.position());
    }

    private static LocalSiteGeometry.Point center(SolarSystem system, OrbitalBody body, double day) {
        var planet = CircularOrbitalEphemeris.at(system, body.planet(), day);
        var center = new LocalSiteGeometry.Point(planet.xMeters(), planet.yMeters(), 0);
        return body.moon() == null ? center : add(center, circle(body.moon().distance() * 1000,
                LunarTransferComparison.moonPhase(system, body.planet(), body.moon(), day)));
    }

    private static LocalSiteGeometry.Point circle(double radius, double phase) {
        return new LocalSiteGeometry.Point(radius * Math.cos(phase), radius * Math.sin(phase), 0);
    }
    private static LocalSiteGeometry.Point add(LocalSiteGeometry.Point first, LocalSiteGeometry.Point second) {
        return new LocalSiteGeometry.Point(first.xMeters() + second.xMeters(), first.yMeters() + second.yMeters(), first.zMeters() + second.zMeters());
    }
}
