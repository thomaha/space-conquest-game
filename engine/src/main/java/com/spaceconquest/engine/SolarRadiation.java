package com.spaceconquest.engine;

import com.spaceconquest.engine.macrostructure.OrbitalStation;
import java.util.List;
import java.util.Locale;

/** Shared provisional stellar luminosity and inverse-square illumination, relative to Sol at 1 AU. */
public final class SolarRadiation {
    public static final double AU_KM = 149_600_000;
    public static final double SOLAR_MASS_KG = 1.989e30;
    private SolarRadiation() {}

    public static double luminosity(SolarSystem system) {
        if (system == null || !Double.isFinite(system.sunMass()) || system.sunMass() <= 0) return 0;
        String type = system.description() == null ? "" : system.description().toLowerCase(Locale.ROOT);
        if (type.contains("black hole")) return 0;
        double strength = Math.pow(system.sunMass() / SOLAR_MASS_KG, 3.5);
        if (type.contains("white dwarf")) strength *= .01;
        else if (type.contains("giant")) strength *= 100;
        return Double.isFinite(strength) ? strength : 0;
    }

    public static double factor(SolarSystem system, double distanceKm) {
        if (!Double.isFinite(distanceKm) || distanceKm <= 0) return 0;
        double radius = system != null && Double.isFinite(system.sunDiameter()) ? Math.max(0, system.sunDiameter() / 2) : 0;
        double factor = luminosity(system) * Math.pow(AU_KM / Math.max(distanceKm, radius), 2);
        return Double.isFinite(factor) ? factor : 0;
    }

    public static double outputKw(double ratedAtOneAuKw, double factor) {
        double output = ratedAtOneAuKw * factor;
        return Double.isFinite(output) ? Math.max(0, output) : 0;
    }

    /** Moons use their parent planet's stellar distance, not their distance from that planet. */
    public static double bodyDistanceKm(SolarSystem system, String bodyId) {
        if (system == null || bodyId == null) return 0;
        return system.planets().stream().filter(planet -> bodyId.equals(planet.id())
                || planet.moons().stream().anyMatch(moon -> bodyId.equals(moon.id())))
                .mapToDouble(Planet::distance).findFirst().orElse(0);
    }

    /** Abstract system-space rendezvous uses the outermost known orbit, with a 1 AU baseline. */
    public static double systemSpaceDistanceKm(SolarSystem system) {
        return system == null ? 0 : Math.max(AU_KM, system.planets().stream().mapToDouble(Planet::distance)
                .filter(distance -> Double.isFinite(distance) && distance > 0).max().orElse(AU_KM));
    }

    public static double stationFactor(List<SolarSystem> systems, OrbitalStation station) {
        SolarSystem system = systems.stream().filter(item -> item.id().equals(station.systemId())).findFirst().orElse(null);
        double distance = station.planetOrbitId() == null || station.planetOrbitId().isBlank()
                ? systemSpaceDistanceKm(system) : system != null && station.planetOrbitId().equals(system.id() + "_star")
                ? Math.max(1, system.sunDiameter()) : bodyDistanceKm(system, station.planetOrbitId());
        return factor(system, distance);
    }

    public static boolean hasAtmosphere(SolarSystem system, String bodyId) {
        if (system == null) return false;
        for (Planet planet : system.planets()) {
            if (planet.id().equals(bodyId)) return hasAtmosphere(planet.atmosphere());
            for (Moon moon : planet.moons()) if (moon.id().equals(bodyId)) return hasAtmosphere(moon.atmosphere());
        }
        return false;
    }

    private static boolean hasAtmosphere(String atmosphere) {
        return atmosphere != null && !atmosphere.isBlank() && !"none".equalsIgnoreCase(atmosphere)
                && !"vacuum".equalsIgnoreCase(atmosphere);
    }
}
