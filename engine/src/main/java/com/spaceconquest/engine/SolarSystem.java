package com.spaceconquest.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record SolarSystem(
    String id,
    String name,
    String description,
    double x,
    double y,
    double z,
    double sunMass,
    double sunDiameter,
    String sunColor,
    List<Planet> planets,
    List<AsteroidBelt> asteroidBelts
) {
    public SolarSystem {
        planets = immutableList(planets);
        asteroidBelts = immutableList(asteroidBelts);
    }

    private static <T> List<T> immutableList(List<T> values) {
        return values == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(values));
    }
}
