package com.spaceconquest.engine;

import java.util.Map;

public record Population(
    String raceId,
    Map<Integer, Long> ageGroups
) {
    public long totalCount() {
        if (ageGroups == null) return 0L;
        return ageGroups.values().stream().mapToLong(Long::longValue).sum();
    }
}
