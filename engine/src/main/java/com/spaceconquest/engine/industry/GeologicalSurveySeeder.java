package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.*;
import java.util.*;

/** New-game geology is finite and generated before any survey, never as a reward for an empty world. */
public final class GeologicalSurveySeeder {
    private GeologicalSurveySeeder() {}
    public static GameState seed(GameState state) {
        var natural = new HashSet<String>();
        try { DataModelLoader.loadMaterials().stream().filter(Material::foundInNature).map(Material::id).forEach(natural::add); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        var deposits = new ArrayList<>(state.geologicalDeposits());
        var existing = new HashSet<String>(); deposits.forEach(d -> existing.add(d.id()));
        for (var system : state.solarSystems()) for (var planet : system.planets()) {
            add(planet.id(), planet.diameter(), planet.resources(), natural, existing, deposits);
            for (var moon : planet.moons()) add(moon.id(), moon.diameter(), moon.resources(), natural, existing, deposits);
        }
        return state.toBuilder().geologicalDeposits(deposits).build();
    }
    private static void add(String body, double diameter, List<String> resources, Set<String> natural,
                            Set<String> existing, List<GeologicalDeposit> deposits) {
        for (String material : resources.stream().distinct().sorted().toList()) {
            String id = "hidden:" + body + ":" + material;
            if (!natural.contains(material) || !existing.add(id)) continue;
            long hash = Integer.toUnsignedLong(id.hashCode());
            double variation = .5 + hash % 1000 / 1000.0;
            // Provisional reserve scale; it does not depend on empire knowledge or economic demand.
            double reserve = Math.max(100000, Math.pow(Math.clamp(diameter, 1, 100000), 2) * 10 * variation);
            deposits.add(new GeologicalDeposit(id, body, material, reserve, reserve, variation, false, ""));
        }
    }
}
