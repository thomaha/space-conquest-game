package com.spaceconquest.engine;

import java.util.Map;

public record Population(
    String raceId,
    Map<Integer, Long> ageGroups
) {}
