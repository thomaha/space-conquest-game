package com.spaceconquest.engine.macrostructure;

/**
 * Planetary surface-to-geostationary orbit macro-structure tether.
 * Drastically cuts planetary launch costs to near-zero for material transit.
 *
 * @param id                               unique space elevator identifier
 * @param planetId                         host planet identifier
 * @param ownerEntityId                    empire or corporation owner identifier
 * @param transitThroughputCapacityKgPerTurn maximum payload capacity per turn in kg
 * @param surfaceToOrbitCostDiscount       discount fraction applied to surface-to-orbit transit (e.g. 0.95 = 95% off)
 * @param structuralIntegrityPercent       current structural health (0.0 to 100.0)
 * @param isOperational                    true if tether is functional
 */
public record SpaceElevator(
        String id,
        String planetId,
        String ownerEntityId,
        double transitThroughputCapacityKgPerTurn,
        double surfaceToOrbitCostDiscount,
        double structuralIntegrityPercent,
        boolean isOperational
) {
    public SpaceElevator {
        if (surfaceToOrbitCostDiscount <= 0.0) surfaceToOrbitCostDiscount = 0.95;
    }
}
