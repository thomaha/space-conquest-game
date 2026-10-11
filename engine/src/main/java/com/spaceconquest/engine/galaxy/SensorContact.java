package com.spaceconquest.engine.galaxy;

import com.spaceconquest.engine.ship.LocalSiteGeometry;

/** An observation, never a reference to the hidden fleet's current state or blueprint. */
public record SensorContact(String fleetId, String ownerId, String systemId,
                            LocalSiteGeometry.Point position, long observedTurn, double observedStrength) {
    public static final long RETENTION_DAYS = 3;
    public static final double UNCERTAINTY_METERS_PER_DAY = 86_400_000;

    public SensorContact {
        if (fleetId == null || ownerId == null || systemId == null || position == null
                || observedTurn < 0 || !Double.isFinite(observedStrength) || observedStrength < 0)
            throw new IllegalArgumentException("Invalid sensor observation");
    }
    public boolean recent(long turn) { return turn >= observedTurn && turn - observedTurn < RETENTION_DAYS; }
    public double uncertaintyMeters(long turn) {
        return Math.max(0, turn - observedTurn) * UNCERTAINTY_METERS_PER_DAY;
    }
}
