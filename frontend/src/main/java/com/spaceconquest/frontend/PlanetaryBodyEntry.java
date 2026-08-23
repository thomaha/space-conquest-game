package com.spaceconquest.frontend;

import com.spaceconquest.engine.Moon;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.SolarSystem;

import java.util.List;

/**
 * Unified adapter model representing a planetary body (either a Planet or a Moon)
 * within a star system, providing calculated colonization state, population aggregates,
 * habitability metrics and sorting/filtering properties.
 */
public record PlanetaryBodyEntry(
        String id,
        String name,
        String systemId,
        String systemName,
        boolean isMoon,
        String parentPlanetName,
        Planet planet,
        Moon moon,
        long totalPopulation,
        boolean isColonized,
        boolean isColonizable,
        double gravity,
        double diameter,
        int resourceCount
) {
    public static PlanetaryBodyEntry fromPlanet(Planet p, SolarSystem system) {
        if (p == null) return null;
        long pop = calculatePopulation(p.populations());
        boolean colonized = pop > 0;
        int resCount = p.resources() != null ? p.resources().size() : 0;
        // Colonizable: uncolonized, solid surface (not gas/ice giant), gravity within survivable range (0.5 m/s² - 30.0 m/s²)
        boolean isGas = p.type() != null && (p.type().equalsIgnoreCase("GAS_GIANT") || p.type().equalsIgnoreCase("ICE_GIANT"));
        boolean colonizable = !colonized && !isGas && p.gravity() >= 0.5 && p.gravity() <= 30.0;

        return new PlanetaryBodyEntry(
                p.id(),
                p.name(),
                system != null ? system.id() : "",
                system != null ? system.name() : "",
                false,
                null,
                p,
                null,
                pop,
                colonized,
                colonizable,
                p.gravity(),
                p.diameter(),
                resCount
        );
    }

    public static PlanetaryBodyEntry fromMoon(Moon m, Planet parentPlanet, SolarSystem system) {
        if (m == null) return null;
        long pop = calculatePopulation(m.populations());
        boolean colonized = pop > 0;
        int resCount = m.resources() != null ? m.resources().size() : 0;
        boolean colonizable = !colonized && m.gravity() >= 0.5 && m.gravity() <= 30.0;

        return new PlanetaryBodyEntry(
                m.id(),
                m.name(),
                system != null ? system.id() : "",
                system != null ? system.name() : "",
                true,
                parentPlanet != null ? parentPlanet.name() : "",
                null,
                m,
                pop,
                colonized,
                colonizable,
                m.gravity(),
                m.diameter(),
                resCount
        );
    }

    private static long calculatePopulation(List<Population> populations) {
        if (populations == null || populations.isEmpty()) {
            return 0L;
        }
        long total = 0L;
        for (Population pop : populations) {
            if (pop.ageGroups() != null) {
                for (Long count : pop.ageGroups().values()) {
                    if (count != null) {
                        total += count;
                    }
                }
            }
        }
        return total;
    }

    public String getAtmosphere() {
        if (planet != null) return planet.atmosphere() != null ? planet.atmosphere() : "None";
        if (moon != null) return moon.atmosphere() != null ? moon.atmosphere() : "None";
        return "None";
    }

    public boolean hasLiquidWater() {
        if (planet != null) return planet.hasLiquidWater();
        if (moon != null) return moon.hasLiquidWater();
        return false;
    }

    public String getBodyType() {
        if (planet != null) return planet.type() != null ? planet.type() : "Planetary body";
        return "Moon";
    }

    public List<String> getResources() {
        if (planet != null && planet.resources() != null) return planet.resources();
        if (moon != null && moon.resources() != null) return moon.resources();
        return List.of();
    }
}
