package com.spaceconquest.engine;

/**
 * Represents a governor assigned to administer a solar system.
 *
 * @param id                   unique identifier
 * @param name                 display name of the governor
 * @param solarSystemId        identifier of the solar system under jurisdiction
 * @param professionId         background profession ID providing localized synergy
 * @param efficiencyBonus      industrial/economic efficiency bonus multiplier
 * @param crimeReductionBonus  crime reduction bonus applied across all celestial bodies in the system
 */
public record SystemGovernor(
        String id,
        String name,
        String solarSystemId,
        String professionId,
        double efficiencyBonus,
        double crimeReductionBonus
) {}
