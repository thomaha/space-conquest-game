package com.spaceconquest.engine;

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
) {}
