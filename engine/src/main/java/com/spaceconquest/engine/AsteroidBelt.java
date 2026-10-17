package com.spaceconquest.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record AsteroidBelt(
    String id,
    String name,
    String description,
    List<String> resources,
    List<Population> populations,
    Double distanceKm
) {
    public AsteroidBelt {
        if (distanceKm != null && (!Double.isFinite(distanceKm) || distanceKm <= 0)) throw new IllegalArgumentException("Invalid belt orbital distance");
        resources = immutableList(resources);
        populations = immutableList(populations);
    }
    public AsteroidBelt(String id, String name, String description, List<String> resources, List<Population> populations) {
        this(id, name, description, resources, populations, null);
    }

    private static <T> List<T> immutableList(List<T> values) {
        return values == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(values));
    }
}
