package com.spaceconquest.engine;

public record StarProperty(
    String spectralType,
    String color,
    double minMassSolar,
    double maxMassSolar,
    double probability,
    double hasPlanetsProbability,
    double isBinaryProbability
) {}
