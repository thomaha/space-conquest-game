package com.spaceconquest.engine.industry;

/**
 * Dynamic per-turn electrical power grid balance sheet.
 *
 * @param entityId                 planet, moon or station identifier
 * @param totalGenerationKw        total power generated in kilowatts
 * @param totalDemandKw            total power consumed in kilowatts
 * @param netBalanceKw             net electrical balance (generation - demand)
 * @param batteryCapacityKwh       aggregate energy storage capacity
 * @param currentStoredKwh         currently stored electrical energy buffer
 * @param isDeficitBrownoutActive  true if load-shedding and brownouts are active
 */
public record PowerGridState(
        String entityId,
        double totalGenerationKw,
        double totalDemandKw,
        double netBalanceKw,
        double batteryCapacityKwh,
        double currentStoredKwh,
        boolean isDeficitBrownoutActive
) {}
