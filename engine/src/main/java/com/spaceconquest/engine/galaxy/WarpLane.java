package com.spaceconquest.engine.galaxy;

/**
 * Represents a traversable hyperlane / spacetime warp corridor connecting two star systems.
 *
 * @param id                 unique warp lane identifier
 * @param systemIdA          origin or endpoint star system ID
 * @param systemIdB          connected destination star system ID
 * @param distanceLightYears physical spatial distance between star systems in light years
 * @param isStable           true if corridor is continuously open, false if unstable periodic wormhole
 */
public record WarpLane(
        String id,
        String systemIdA,
        String systemIdB,
        double distanceLightYears,
        boolean isStable
) {
    public boolean connects(String systemId) {
        return (systemIdA != null && systemIdA.equals(systemId))
                || (systemIdB != null && systemIdB.equals(systemId));
    }

    public String getOppositeSystemId(String currentSystemId) {
        if (systemIdA != null && systemIdA.equals(currentSystemId)) {
            return systemIdB;
        }
        return systemIdA;
    }
}
