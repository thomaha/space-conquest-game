package com.spaceconquest.engine.industry;

/**
 * Represents a surface-to-orbit magnetic mass driver catapult installation.
 * Bypasses atmospheric gravity taxes by flinging mineral freight directly into low-orbit space stations.
 *
 * @param id                      unique mass driver installation identifier
 * @param planetId                host planetary celestial body ID
 * @param ownerEntityId           sovereign empire or private corporation owning the driver
 * @param maxPayloadTonsPerTurn   maximum cargo throughput capacity in metric tons per turn
 * @param powerDrawKw             electrical power demand required for magnetic coils
 * @param launchCostPerTonCredits operational maintenance cost in credits per metric ton launched
 * @param isActive                true if powered and operational
 */
public record SurfaceMassDriver(
        String id,
        String planetId,
        String ownerEntityId,
        double maxPayloadTonsPerTurn,
        double powerDrawKw,
        double launchCostPerTonCredits,
        boolean isActive
) {}
