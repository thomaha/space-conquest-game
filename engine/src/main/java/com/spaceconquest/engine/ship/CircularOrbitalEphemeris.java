package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.SolarSystem;

/** Read-only circular coplanar game ephemeris. Does not replace live local travel geometry. */
public final class CircularOrbitalEphemeris {
    public static final double SECONDS_PER_DAY = 86400;
    private static final double TWO_PI = 2 * Math.PI;

    public record State(double xMeters, double yMeters, double vxMps, double vyMps) {
        public State {
            if (!Double.isFinite(xMeters) || !Double.isFinite(yMeters)
                    || !Double.isFinite(vxMps) || !Double.isFinite(vyMps))
                throw new IllegalArgumentException("Finite orbital position and velocity are required");
        }
        public double radiusMeters() { return Math.hypot(xMeters, yMeters); }
        public double speedMps() { return Math.hypot(vxMps, vyMps); }
    }

    private CircularOrbitalEphemeris() {}

    public static double gravitationalParameter(SolarSystem system) {
        if (system == null) throw new IllegalArgumentException("A known system is required");
        double mu = PlanetaryTransferComparison.GRAVITATIONAL_CONSTANT * system.sunMass();
        positive(mu);
        return mu;
    }

    public static double periodDays(SolarSystem system, Planet body) {
        double radius = radius(system, body);
        double period = TWO_PI * Math.sqrt(radius * radius * radius / gravitationalParameter(system)) / SECONDS_PER_DAY;
        positive(period);
        return period;
    }

    /** Campaign day zero uses an ID-derived synthetic phase, independent of list order or calendar date. */
    public static double phase(SolarSystem system, Planet body, double campaignDays) {
        if (!Double.isFinite(campaignDays) || campaignDays < 0)
            throw new IllegalArgumentException("A finite nonnegative campaign epoch is required");
        double period = periodDays(system, body);
        int hash = (system.id() + ":planet:" + body.id()).hashCode();
        double initial = Integer.toUnsignedLong(hash) / 4294967296.0 * TWO_PI;
        return angle(initial + (campaignDays % period) / period * TWO_PI);
    }

    public static State at(SolarSystem system, Planet body, double campaignDays) {
        double radius = radius(system, body), phase = phase(system, body, campaignDays);
        double speed = Math.sqrt(gravitationalParameter(system) / radius);
        return new State(radius * Math.cos(phase), radius * Math.sin(phase),
                -speed * Math.sin(phase), speed * Math.cos(phase));
    }

    public static PlanetaryTransferComparison.Orbit comparison(SolarSystem system, Planet source, Planet target,
                                                               double campaignDays, double sourceAltitudeKm, double targetAltitudeKm) {
        double relative = angle(phase(system, target, campaignDays) - phase(system, source, campaignDays));
        return PlanetaryTransferComparison.orbit(system, source, target, relative, sourceAltitudeKm, targetAltitudeKm);
    }

    private static double radius(SolarSystem system, Planet body) {
        if (system == null || body == null || !system.planets().contains(body))
            throw new IllegalArgumentException("A planet in the selected system is required");
        double radius = body.distance() * 1000;
        positive(radius);
        return radius;
    }

    static double angle(double radians) { return (radians % TWO_PI + TWO_PI) % TWO_PI; }
    static void positive(double value) {
        if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException("Positive finite orbital values are required");
    }
}
