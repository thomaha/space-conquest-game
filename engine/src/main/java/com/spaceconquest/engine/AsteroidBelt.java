package com.spaceconquest.engine;

import java.util.List;

public record AsteroidBelt(
    String id,
    String name,
    String description,
    List<String> resources
) {}
