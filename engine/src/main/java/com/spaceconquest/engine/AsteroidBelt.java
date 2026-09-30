package com.spaceconquest.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record AsteroidBelt(
    String id,
    String name,
    String description,
    List<String> resources,
    List<Population> populations
) {
    public AsteroidBelt {
        resources = immutableList(resources);
        populations = immutableList(populations);
    }

    private static <T> List<T> immutableList(List<T> values) {
        return values == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(values));
    }
}
