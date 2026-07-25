package com.spaceconquest.engine;

import java.util.Map;

public record Material(
    String id,
    String name,
    String description,
    boolean foundInNature,
    Map<String, Double> composition,
    double weightPerUnit,
    double strength,
    int complexity
) {}
