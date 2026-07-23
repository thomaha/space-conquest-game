package com.spaceconquest.engine;

public record Race(
    String id,
    String name,
    String description,
    double intelligence,
    double physicalStrength,
    String societyStructure,
    double preferredGForce,
    double preferredTemperature,
    String chemicalComposition,
    String breathingAtmosphere,
    int fertileAgeStart,
    int fertileAgeEnd,
    String nutrientType,
    String nutrientSpreadRequirement,
    int retirementAge
) {}
