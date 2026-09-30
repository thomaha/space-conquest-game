package com.spaceconquest.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record Moon(
    String id,
    String name,
    String description,
    double mass,
    double gravity,
    double distance,
    double diameter,
    String atmosphere,
    boolean hasLiquidWater,
    double waterLevel,
    List<String> resources,
    List<Population> populations
) {
    public Moon {
        resources = immutableList(resources);
        populations = immutableList(populations);
    }

    private static <T> List<T> immutableList(List<T> values) {
        return values == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(values));
    }
}
