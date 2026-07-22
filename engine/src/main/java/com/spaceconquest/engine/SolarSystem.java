package com.spaceconquest.engine;

import java.util.List;

public record SolarSystem(
    String id,
    String name,
    String description,
    double sunMass,
    double sunDiameter,
    List<Planet> planets,
    List<AsteroidBelt> asteroidBelts
) {}
