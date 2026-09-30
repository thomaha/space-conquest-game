package com.spaceconquest.engine.habitation;

import java.util.Map;

/** People explicitly booked for an offworld destination while aboard one ship. */
public record PassengerManifest(String shipId, String sourceBodyId,
                                String destinationBodyId, String raceId,
                                Map<Integer, Long> ageGroups, boolean combatDeployment) {
    public PassengerManifest(String shipId, String sourceBodyId, String destinationBodyId,
                             String raceId, Map<Integer, Long> ageGroups) {
        this(shipId, sourceBodyId, destinationBodyId, raceId, ageGroups, false);
    }

    public PassengerManifest {
        ageGroups = ageGroups == null ? Map.of() : Map.copyOf(ageGroups);
    }

    public long headcount() {
        return ageGroups.values().stream().mapToLong(Long::longValue).sum();
    }
}
