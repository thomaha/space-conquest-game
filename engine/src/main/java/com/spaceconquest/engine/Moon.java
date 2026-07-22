package com.spaceconquest.engine;

import java.util.List;

public record Moon(
    String id,
    String name,
    String description,
    double mass,
    double gravity,
    double distance,
    double diameter,
    List<String> resources
) {}
