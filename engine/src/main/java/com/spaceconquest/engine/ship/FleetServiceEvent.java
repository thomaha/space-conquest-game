package com.spaceconquest.engine.ship;

import java.util.Map;

/** Committed service receipt; quantities are observed store changes, never a projected purchase. */
public record FleetServiceEvent(long turn, String ownerId, String fleetId, Kind kind, String detail,
                                Map<String, Double> resourcesKg, double gridInputKwh, double netCreditsSpent) {
    public enum Kind { MISSION_PAUSED, SUPPLIES_PURCHASED, SERVICE_DEPARTURE, RETURN_STARTED, MISSION_RESUMED, RECOVERY_COMPLETED }
    public FleetServiceEvent { resourcesKg = Map.copyOf(resourcesKg); }
}
