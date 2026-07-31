package com.spaceconquest.engine;

import java.util.List;

public record TechnicalApplication(
    String id,
    String name,
    String description,
    List<String> requiredTechnologies,
    List<String> affectedFactors,
    double costToBuildPerUnit,
    List<String> requiredMaterials,
    int complexity
) {}
