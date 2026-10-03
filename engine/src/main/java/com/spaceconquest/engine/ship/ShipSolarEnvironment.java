package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.SolarRadiation;

/** Conservative illumination envelopes until local orbital geometry is modeled. Distances are in km. */
public record ShipSolarEnvironment(double fluxRelativeToEarth, double lightHours, double darkHours,
                                   String description) {
    public static final double AU_KM = SolarRadiation.AU_KM;
    public static final ShipSolarEnvironment DARK = new ShipSolarEnvironment(0, 24, 0,
            "No guaranteed sunlight at an unknown location or during interstellar transit");

    public ShipSolarEnvironment {
        if (!Double.isFinite(fluxRelativeToEarth) || fluxRelativeToEarth < 0
                || lightHours <= 0 || darkHours < 0 || !Double.isFinite(lightHours + darkHours))
            throw new IllegalArgumentException("Invalid illumination envelope");
    }

    public static ShipSolarEnvironment at(GameState state, Fleet fleet, FleetLocation.Site site) {
        if (fleet.isInterstellarTransit()) return DARK;
        SolarSystem system = state.solarSystems().stream().filter(item -> item.id().equals(fleet.currentSystemId()))
                .findFirst().orElse(null);
        if (system == null) return DARK;
        if (site.kind() == FleetLocation.Kind.DEEP_SPACE) {
            double bodyDistance = SolarRadiation.bodyDistanceKm(system, site.entityId());
            double distance = site.entityId().equals(system.id() + "_star") ? Math.max(1, system.sunDiameter())
                    : bodyDistance > 0 ? bodyDistance : SolarRadiation.systemSpaceDistanceKm(system);
            return new ShipSolarEnvironment(SolarRadiation.factor(system, distance), 24, 0,
                    "Unobstructed system-space sunlight at an approximate rendezvous distance");
        }
        String body = site.kind() == FleetLocation.Kind.DOCKED ? state.orbitalStations().stream()
                .filter(station -> station.id().equals(site.entityId())).map(station -> station.planetOrbitId())
                .findFirst().orElse("") : site.entityId();
        double distance = SolarRadiation.bodyDistanceKm(system, body);
        if (!Double.isFinite(distance) || distance <= 0) return DARK;
        double flux = SolarRadiation.factor(system, distance);
        return site.kind() == FleetLocation.Kind.SURFACE
                ? new ShipSolarEnvironment(flux, 12, 12, "Approximate 12-hour surface night; weather is not modeled")
                : new ShipSolarEnvironment(flux, 10, 2, "Approximate 2-hour eclipse in each 12-hour orbit");
    }

    public static ShipSolarEnvironment journey(GameState state, Fleet fleet, FleetLocation.Site destination) {
        if (atmosphericTravel(state, fleet, destination)) return new ShipSolarEnvironment(0, 24, 0,
                "Solar arrays unavailable during atmospheric travel");
        ShipSolarEnvironment first = at(state, fleet, fleet.location().current());
        ShipSolarEnvironment last = at(state, fleet, destination);
        double dark = Math.max(first.darkHours(), last.darkHours());
        return new ShipSolarEnvironment(Math.min(first.fluxRelativeToEarth(), last.fluxRelativeToEarth()),
                dark >= 12 ? 12 : dark > 0 ? 10 : 24, dark,
                "Conservative local transfer illumination envelope");
    }

    public static boolean atmosphericTravel(GameState state, Fleet fleet, FleetLocation.Site destination) {
        SolarSystem system = state.solarSystems().stream().filter(item -> item.id().equals(fleet.currentSystemId()))
                .findFirst().orElse(null);
        var origin = fleet.location().current();
        return origin.kind() == FleetLocation.Kind.SURFACE && SolarRadiation.hasAtmosphere(system, origin.entityId())
                || destination != null && destination.kind() == FleetLocation.Kind.SURFACE
                && SolarRadiation.hasAtmosphere(system, destination.entityId());
    }
}
