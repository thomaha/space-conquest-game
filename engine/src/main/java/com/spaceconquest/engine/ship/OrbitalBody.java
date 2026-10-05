package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Moon;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.SolarSystem;

/** Immutable parking body with its parent retained for lunar gravity and solar distance. */
public record OrbitalBody(Planet planet, Moon moon) {
    public String id() { return moon == null ? planet.id() : moon.id(); }
    public double mass() { return moon == null ? planet.mass() : moon.mass(); }
    public double diameter() { return moon == null ? planet.diameter() : moon.diameter(); }
    public double distance() { return planet.distance(); }
    public double sphereKm(SolarSystem system) {
        return moon == null ? planet.distance() * Math.pow(planet.mass() / system.sunMass(), .4)
                : moon.distance() * Math.pow(moon.mass() / planet.mass(), .4);
    }
    public OrbitalFlight.Frame frame() {
        return new OrbitalFlight.Frame(id(), moon == null ? OrbitalFlight.CenterKind.PLANETARY : OrbitalFlight.CenterKind.LUNAR);
    }
    public static OrbitalBody find(GameState state, String systemId, String bodyId) {
        for (var system : state.solarSystems()) if (system.id().equals(systemId)) {
            for (var planet : system.planets()) {
                if (planet.id().equals(bodyId)) return new OrbitalBody(planet, null);
                for (var moon : planet.moons()) if (moon.id().equals(bodyId)) return new OrbitalBody(planet, moon);
            }
        }
        return null;
    }
}
