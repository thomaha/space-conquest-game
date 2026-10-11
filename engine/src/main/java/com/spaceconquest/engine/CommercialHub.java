package com.spaceconquest.engine;

import java.util.Map;
import com.spaceconquest.engine.market.HubStockTarget;
import com.spaceconquest.engine.market.HubStockHistory;
import com.spaceconquest.engine.logistics.ReservedTradeFuel;

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
 * @param stockTargets           optional proactive inventory settings per material
 * @param stockHistory           observed daily gross withdrawals, deliveries and stockout flags
 * @param reservedTradeFuel      paid private fuel keyed by ship ID, included in physical storage weight
 */
public record CommercialHub(
        String id,
        String entityId,
        double transactionTariffRate,
        double storageCapacityKg,
        double currentStoredWeightKg,
        double logisticsRangeUnits,
        Map<String, MarketOrder> activeOrders,
        Map<String, HubStockTarget> stockTargets,
        HubStockHistory stockHistory,
        Map<String, ReservedTradeFuel> reservedTradeFuel
) {
    public CommercialHub(String id, String entityId, double transactionTariffRate,
                         double storageCapacityKg, double currentStoredWeightKg,
                         double logisticsRangeUnits, Map<String, MarketOrder> activeOrders,
                         Map<String, HubStockTarget> stockTargets, HubStockHistory stockHistory) {
        this(id, entityId, transactionTariffRate, storageCapacityKg, currentStoredWeightKg,
                logisticsRangeUnits, activeOrders, stockTargets, stockHistory, Map.of());
    }
    public CommercialHub(String id, String entityId, double transactionTariffRate,
                         double storageCapacityKg, double currentStoredWeightKg,
                         double logisticsRangeUnits, Map<String, MarketOrder> activeOrders,
                         Map<String, HubStockTarget> stockTargets) {
        this(id, entityId, transactionTariffRate, storageCapacityKg, currentStoredWeightKg,
                logisticsRangeUnits, activeOrders, stockTargets, HubStockHistory.empty());
    }
    public CommercialHub(String id, String entityId, double transactionTariffRate,
                         double storageCapacityKg, double currentStoredWeightKg,
                         double logisticsRangeUnits, Map<String, MarketOrder> activeOrders) {
        this(id, entityId, transactionTariffRate, storageCapacityKg, currentStoredWeightKg,
                logisticsRangeUnits, activeOrders, Map.of());
    }

    public CommercialHub {
        activeOrders = activeOrders == null ? Map.of() : Map.copyOf(activeOrders);
        stockTargets = stockTargets == null ? Map.of() : Map.copyOf(stockTargets);
        stockHistory = stockHistory == null ? HubStockHistory.empty() : stockHistory;
        reservedTradeFuel = reservedTradeFuel == null ? Map.of() : Map.copyOf(reservedTradeFuel);
    }

    public CommercialHub withStockTargets(Map<String, HubStockTarget> targets) {
        return new CommercialHub(id, entityId, transactionTariffRate, storageCapacityKg,
                currentStoredWeightKg, logisticsRangeUnits, activeOrders, targets, stockHistory, reservedTradeFuel);
    }

    public CommercialHub observeDay(long turn) {
        return new CommercialHub(id, entityId, transactionTariffRate, storageCapacityKg,
                currentStoredWeightKg, logisticsRangeUnits, activeOrders, stockTargets, stockHistory.observe(turn, activeOrders), reservedTradeFuel);
    }

    public CommercialHub withInventory(long turn, double storedWeightKg, Map<String, MarketOrder> orders) {
        return new CommercialHub(id, entityId, transactionTariffRate, storageCapacityKg,
                storedWeightKg, logisticsRangeUnits, orders, stockTargets, stockHistory.change(turn, activeOrders, orders), reservedTradeFuel);
    }

    public CommercialHub withReservedTradeFuel(double storedWeightKg, Map<String, ReservedTradeFuel> fuel) {
        return new CommercialHub(id, entityId, transactionTariffRate, storageCapacityKg,
                storedWeightKg, logisticsRangeUnits, activeOrders, stockTargets, stockHistory, fuel);
    }
}
