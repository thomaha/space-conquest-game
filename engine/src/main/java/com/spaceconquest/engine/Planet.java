package com.spaceconquest.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record Planet(
    String id,
    String name,
    String description,
    double mass,
    double gravity,
    double distance,
    double inclination,
    double diameter,
    String type,
    String atmosphere,
    boolean hasLiquidWater,
    double waterLevel,
    List<String> resources,
    List<Moon> moons,
    List<Population> populations
) {
    public Planet {
        resources = immutableList(resources);
        moons = immutableList(moons);
        populations = immutableList(populations);
    }

    private static <T> List<T> immutableList(List<T> values) {
        return values == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(values));
    }
}
