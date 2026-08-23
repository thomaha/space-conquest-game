package com.spaceconquest.engine;

/**
 * Represents the bilateral diplomatic relationship between two sovereign empires.
 *
 * @param empireAId              first empire identifier
 * @param empireBId              second empire identifier
 * @param tier                   diplomatic tier ("TOTAL_WAR", "COLD_WAR", "NEUTRAL", "COMMERCIAL_ALLIANCE", "INTEGRATED_FEDERATION")
 * @param mutualTariffDiscount   percentage discount applied to mutual trade tariffs
 */
public record DiplomaticRelation(
        String empireAId,
        String empireBId,
        String tier,
        double mutualTariffDiscount
) {}
