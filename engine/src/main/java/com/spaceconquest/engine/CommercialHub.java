package com.spaceconquest.engine;

import java.util.Map;

/**
 * Represents a commercial trade hub situated on a planet, moon, or space station.
 *
 * @param id                     unique identifier
 * @param entityId               astronomical or station entity hosting this hub
 * @param transactionTariffRate  tariff rate charged on transactions
 * @param storageCapacityKg      total storage capacity in kilograms
 * @param currentStoredWeightKg  currently stored inventory weight in kilograms
 * @param logisticsRangeUnits    effective logistics scanning and routing radius
 * @param activeOrders           mapping of resource ID to current market order data
 */
public record CommercialHub(
        String id,
        String entityId,
        double transactionTariffRate,
        double storageCapacityKg,
        double currentStoredWeightKg,
        double logisticsRangeUnits,
        Map<String, MarketOrder> activeOrders
) {
    public CommercialHub {
        if (activeOrders == null) activeOrders = Map.of();
    }
}
