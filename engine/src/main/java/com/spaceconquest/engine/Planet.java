package com.spaceconquest.engine;

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
) {}
