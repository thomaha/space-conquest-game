package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.Moon;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.SolarSystem;

/** Read-only patched two-body planet–moon comparison. A passing benchmark does not authorize travel. */
public final class LunarTransferComparison {
    public record Result(String parentId, String moonId, boolean outbound, double parentAltitudeKm, double moonAltitudeKm,
                         double parentSphereKm, double moonSphereKm, HohmannCoast coast, PlanetaryTransferComparison.Orbit orbit) {}
    private LunarTransferComparison() {}

    public static Result compare(SolarSystem system, Planet parent, Moon moon, double epochDays,
                                 double parentAltitudeKm, double moonAltitudeKm, boolean outbound) {
        if (system == null || parent == null || moon == null || !system.planets().contains(parent) || !parent.moons().contains(moon))
            throw new IllegalArgumentException("A known moon and its actual parent planet are required");
        if (!Double.isFinite(epochDays) || epochDays < 0) throw new IllegalArgumentException("A finite nonnegative epoch is required");
        CircularOrbitalEphemeris.positive(parent.mass()); CircularOrbitalEphemeris.positive(parent.diameter());
        CircularOrbitalEphemeris.positive(parent.distance()); CircularOrbitalEphemeris.positive(system.sunMass());
        CircularOrbitalEphemeris.positive(moon.mass()); CircularOrbitalEphemeris.positive(moon.diameter());
        CircularOrbitalEphemeris.positive(moon.distance());
        CircularOrbitalEphemeris.positive(parentAltitudeKm); CircularOrbitalEphemeris.positive(moonAltitudeKm);
        double parentSphereKm = parent.distance() * Math.pow(parent.mass() / system.sunMass(), .4);
        double moonSphereKm = moon.distance() * Math.pow(moon.mass() / parent.mass(), .4);
        double parentRadius = (parent.diameter() / 2 + parentAltitudeKm) * 1000;
        double lunarRadius = (moon.diameter() / 2 + moonAltitudeKm) * 1000;
        double moonOrbit = moon.distance() * 1000;
        if (!Double.isFinite(parentSphereKm) || !Double.isFinite(moonSphereKm) || parentRadius >= parentSphereKm * 1000
                || lunarRadius >= moonSphereKm * 1000 || moonOrbit >= parentSphereKm * 1000
                || parentRadius >= moonOrbit - moonSphereKm * 1000)
            throw new IllegalArgumentException("Parking or lunar encounter lies outside the supported sphere-of-influence geometry");
        double mu = PlanetaryTransferComparison.GRAVITATIONAL_CONSTANT * parent.mass();
        double moonMu = PlanetaryTransferComparison.GRAVITATIONAL_CONSTANT * moon.mass();
        double sourceRadius = outbound ? parentRadius : moonOrbit, targetRadius = outbound ? moonOrbit : parentRadius;
        var seed = new HohmannCoast(mu, sourceRadius, targetRadius, 0);
        double sourceRate = Math.sqrt(mu / sourceRadius) / sourceRadius;
        double targetRate = Math.sqrt(mu / targetRadius) / targetRadius;
        double parentPhase = parkingPhase(parent, parentAltitudeKm, epochDays);
        double moonPhase = moonPhase(system, parent, moon, epochDays);
        double sourcePhase = outbound ? parentPhase : moonPhase;
        double relative = CircularOrbitalEphemeris.angle((outbound ? moonPhase : parentPhase) - sourcePhase);
        double required = CircularOrbitalEphemeris.angle(Math.PI - targetRate * seed.coastSeconds());
        double rate = targetRate - sourceRate;
        double gap = CircularOrbitalEphemeris.angle(rate > 0 ? required - relative : relative - required);
        if (gap < 1e-12 || 2 * Math.PI - gap < 1e-12) gap = 0;
        double wait = gap / Math.abs(rate);
        var coast = new HohmannCoast(mu, sourceRadius, targetRadius, sourcePhase + sourceRate * wait);
        double earthBurn = Math.abs(Math.sqrt(mu * (2 / parentRadius - 1 / coast.axisMeters())) - Math.sqrt(mu / parentRadius));
        double moonExcess = Math.abs(Math.sqrt(mu / moonOrbit) - Math.sqrt(mu * (2 / moonOrbit - 1 / coast.axisMeters())));
        double lunarBurn = Math.sqrt(moonExcess * moonExcess + 2 * moonMu / lunarRadius) - Math.sqrt(moonMu / lunarRadius);
        double parentPeriod = period(mu, parentRadius), lunarPeriod = period(moonMu, lunarRadius);
        var orbit = new PlanetaryTransferComparison.Orbit(outbound ? moon.id() : parent.id(), coast.coastSeconds() / 86400,
                wait / 86400, required, outbound ? earthBurn : moonExcess, outbound ? moonExcess : earthBurn,
                outbound ? earthBurn : lunarBurn, outbound ? lunarBurn : earthBurn,
                outbound ? parentPeriod : lunarPeriod, outbound ? lunarPeriod : parentPeriod);
        return new Result(parent.id(), moon.id(), outbound, parentAltitudeKm, moonAltitudeKm, parentSphereKm, moonSphereKm, coast, orbit);
    }

    public static double moonPhase(SolarSystem system, Planet parent, Moon moon, double epochDays) {
        if (system == null || parent == null || moon == null || !system.planets().contains(parent) || !parent.moons().contains(moon)
                || !Double.isFinite(epochDays) || epochDays < 0) throw new IllegalArgumentException("A known lunar orbit and epoch are required");
        double mu = PlanetaryTransferComparison.GRAVITATIONAL_CONSTANT * parent.mass(), radius = moon.distance() * 1000;
        double period = period(mu, radius);
        double initial = Integer.toUnsignedLong((system.id() + ":planet:" + parent.id() + ":moon:" + moon.id()).hashCode())
                / 4294967296.0 * 2 * Math.PI;
        return CircularOrbitalEphemeris.angle(initial + (epochDays * 86400 % period) / period * 2 * Math.PI);
    }

    public static double parkingPhase(Planet parent, double altitudeKm, double epochDays) {
        if (parent == null || !Double.isFinite(epochDays) || epochDays < 0) throw new IllegalArgumentException("A planet and epoch are required");
        CircularOrbitalEphemeris.positive(altitudeKm);
        double radius = (parent.diameter() / 2 + altitudeKm) * 1000;
        double period = period(PlanetaryTransferComparison.GRAVITATIONAL_CONSTANT * parent.mass(), radius);
        double initial = Integer.toUnsignedLong((parent.id() + ":parking").hashCode()) / 4294967296.0 * 2 * Math.PI;
        return CircularOrbitalEphemeris.angle(initial + (epochDays * 86400 % period) / period * 2 * Math.PI);
    }

    private static double period(double mu, double radius) {
        CircularOrbitalEphemeris.positive(mu); CircularOrbitalEphemeris.positive(radius);
        double period = 2 * Math.PI * Math.sqrt(radius / mu) * radius;
        CircularOrbitalEphemeris.positive(period);
        return period;
    }
}
