package com.spaceconquest.engine.habitation;

import java.util.Map;

/**
 * Results of in-transit passenger life support processing for a spaceship.
 *
 * @param shipId             ship identifier
 * @param passengerCount     total passengers aboard
 * @param transitMode        transit mode (CONSCIOUS or CRYOGENIC_STASIS)
 * @param consumedSupplies   map of resource IDs to amounts consumed during the transit turn
 * @param lifeSupportDeficit true if conscious passengers lacked required food/oxygen
 * @param casualtyCount      number of passenger casualties if life support failed
 * @param passengerHappiness happiness level of passengers (0.0 to 1.0)
 */
public record PassengerLogisticsResult(
        String shipId,
        int passengerCount,
        String transitMode,
        Map<String, Double> consumedSupplies,
        boolean lifeSupportDeficit,
        int casualtyCount,
        double passengerHappiness
) {
    public static final String CONSCIOUS = "CONSCIOUS";
    public static final String CRYOGENIC_STASIS = "CRYOGENIC_STASIS";

    public PassengerLogisticsResult {
        if (consumedSupplies == null) consumedSupplies = Map.of();
    }
}
