package com.spaceconquest.engine;

import java.util.List;

public record Technology(
    String id,
    String name,
    String description,
    List<String> requiredTechnologies,
    List<TechnicalApplication> applications
) {}
