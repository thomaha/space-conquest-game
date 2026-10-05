package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.SolarRadiation;
import com.spaceconquest.engine.SolarSystem;

/** Stable representative geometry until the model has actual orbital phases or ephemerides. */
public final class LocalSiteGeometry {
    private static final double ORBIT_ALTITUDE_KM = 500;
    private static final double DOCKING_SEPARATION_METERS = 1000;

    public record Point(double xMeters, double yMeters, double zMeters) {
        public Point {
            if (!Double.isFinite(xMeters) || !Double.isFinite(yMeters) || !Double.isFinite(zMeters))
                throw new IllegalArgumentException("Invalid local position");
        }
        Point add(Point other) {
            return point(xMeters + other.xMeters, yMeters + other.yMeters, zMeters + other.zMeters);
        }
    }

    public record Leg(Point origin, Point destination, double distanceMeters) {}
    private record Body(Point center, double diameterKm, double inclinationDegrees) {}

    private LocalSiteGeometry() {}

    public static Leg resolve(GameState state, String systemId, FleetLocation.Site from, FleetLocation.Site to) {
        if (from == null || to == null || from.equals(to)) return null;
        Point first = position(state, systemId, from), last = position(state, systemId, to);
        if (first == null || last == null) return null;
        double distance = Math.hypot(Math.hypot(first.xMeters() - last.xMeters(), first.yMeters() - last.yMeters()),
                first.zMeters() - last.zMeters());
        return Double.isFinite(distance) ? new Leg(first, last, Math.max(DOCKING_SEPARATION_METERS, distance)) : null;
    }

    public static Point position(GameState state, String systemId, FleetLocation.Site site) {
        if (state == null || systemId == null || site == null || site.kind() == FleetLocation.Kind.SURFACE) return null;
        SolarSystem system = state.solarSystems().stream().filter(item -> systemId.equals(item.id())).findFirst().orElse(null);
        if (system == null) return null;
        if (site.kind() == FleetLocation.Kind.DOCKED) {
            var station = state.orbitalStations().stream().filter(item -> site.entityId().equals(item.id())
                    && systemId.equals(item.systemId())).findFirst().orElse(null);
            if (station == null) return null;
            if (station.planetOrbitId() == null || station.planetOrbitId().isBlank()) {
                Point rendezvous = rendezvous(system);
                Point offset = circle(ORBIT_ALTITUDE_KM, "station:" + station.id(), 0);
                return rendezvous == null || offset == null ? null : rendezvous.add(offset);
            }
            return orbit(body(system, station.planetOrbitId()), "station:" + station.id(), station.effectiveParkingAltitudeKm());
        }
        if (site.kind() == FleetLocation.Kind.DEEP_SPACE) {
            if (site.equals(FleetLocation.Site.deepSpace())) return rendezvous(system);
            if (site.entityId().equals(systemId + "_star"))
                return circle(system.sunDiameter(), "star:" + systemId, 0);
        }
        return orbit(body(system, site.entityId()), "orbit:" + site.entityId(), site.parkingAltitudeKm() == null ? 500 : site.parkingAltitudeKm());
    }

    private static Point rendezvous(SolarSystem system) {
        return circle(SolarRadiation.systemSpaceDistanceKm(system) + .1 * SolarRadiation.AU_KM,
                "rendezvous:" + system.id(), 0);
    }

    private static Body body(SolarSystem system, String id) {
        for (Planet planet : system.planets()) {
            if (id.equals(planet.id())) return planet(planet);
            for (var moon : planet.moons()) {
                if (!id.equals(moon.id())) continue;
                Body parent = planet(planet);
                Point relative = circle(moon.distance(), "moon:" + moon.id(), planet.inclination());
                Point center = parent == null || relative == null ? null : parent.center().add(relative);
                return center == null || !validDiameter(moon.diameter()) ? null
                        : new Body(center, moon.diameter(), planet.inclination());
            }
        }
        return null;
    }

    private static Body planet(Planet planet) {
        Point center = circle(planet.distance(), "planet:" + planet.id(), planet.inclination());
        return center == null || !validDiameter(planet.diameter()) ? null
                : new Body(center, planet.diameter(), planet.inclination());
    }

    private static boolean validDiameter(double diameter) { return Double.isFinite(diameter) && diameter > 0; }

    private static Point orbit(Body body, String phaseId, double altitudeKm) {
        if (body == null) return null;
        Point offset = circle(body.diameterKm() / 2 + altitudeKm, phaseId, body.inclinationDegrees());
        return offset == null ? null : body.center().add(offset);
    }

    private static Point circle(double radiusKm, String phaseId, double inclinationDegrees) {
        if (!Double.isFinite(radiusKm) || radiusKm <= 0 || !Double.isFinite(inclinationDegrees)) return null;
        int hash = phaseId.hashCode();
        hash ^= hash >>> 16; hash *= 0x7feb352d;
        hash ^= hash >>> 15; hash *= 0x846ca68b; hash ^= hash >>> 16;
        double phase = Integer.toUnsignedLong(hash) / 4294967296.0 * 2 * Math.PI;
        double inclination = Math.toRadians(inclinationDegrees % 360), radius = radiusKm * 1000;
        return point(radius * Math.cos(phase), radius * Math.sin(phase) * Math.cos(inclination),
                radius * Math.sin(phase) * Math.sin(inclination));
    }

    private static Point point(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) ? new Point(x, y, z) : null;
    }
}
