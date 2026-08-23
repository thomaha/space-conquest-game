package com.spaceconquest.engine.market;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.MarketOrder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes market pricing, shortcoming scores (S_m), transaction tariffs,
 * and planetary gravity export taxes across commercial hubs.
 */
public class MarketProcessor {

    private static final double EPSILON = 0.001;
    private static final double DEFAULT_BASE_PRICE = 10.0;

    /**
     * Calculates the spot price per kilogram for a commodity based on local supply and demand.
     *
     * @param basePrice baseline resource price
     * @param supplyKg  available local supply in kg
     * @param demandKg  local demand in kg
     * @return calculated spot price per kg
     */
    public double calculateSpotPrice(double basePrice, double supplyKg, double demandKg) {
        if (basePrice <= 0) {
            basePrice = DEFAULT_BASE_PRICE;
        }
        double ratio = (demandKg - supplyKg) / (supplyKg + demandKg + 1.0);
        double multiplier = Math.max(0.1, 1.0 + ratio);
        return Math.round(basePrice * multiplier * 100.0) / 100.0;
    }

    /**
     * Calculates the Shortcoming Score (S_m) for a resource.
     * Formula: S_m = ((Local Demand - Local Supply) / (Local Supply + epsilon)) * Market Price Modifier
     *
     * @param supplyKg           local supply in kg
     * @param demandKg           local demand in kg
     * @param marketPriceModifier market price multiplier
     * @return calculated shortcoming score S_m
     */
    public double calculateShortcomingScore(double supplyKg, double demandKg, double marketPriceModifier) {
        if (demandKg <= supplyKg) {
            return 0.0;
        }
        double deficitRatio = (demandKg - supplyKg) / (supplyKg + EPSILON);
        return Math.max(0.0, deficitRatio * marketPriceModifier);
    }

    /**
     * Calculates the planetary blast-off gravity tax for exporting cargo from a celestial body.
     * Formula: Launch Cost = (Dry Mass + Stored Cargo Mass) * Gravity * (1 + Atmospheric Pressure)
     *
     * @param dryMassKg           dry structural mass of the vessel in kg
     * @param cargoMassKg         mass of loaded cargo in kg
     * @param surfaceGravity      surface gravity in standard Gs
     * @param atmosphericPressure atmospheric pressure in atmospheres
     * @return launch tax in credits
     */
    public double calculateGravityLaunchTax(
            double dryMassKg,
            double cargoMassKg,
            double surfaceGravity,
            double atmosphericPressure
    ) {
        double effectiveGravity = Math.max(0.0, surfaceGravity);
        double effectiveAtmosphere = Math.max(0.0, atmosphericPressure);
        return (dryMassKg + cargoMassKg) * effectiveGravity * (1.0 + effectiveAtmosphere);
    }

    /**
     * Calculates the transaction tariff skimmed by the sovereign state.
     * Formula: Tariff = Gross Transaction Value * Transaction Tariff Rate
     *
     * @param grossTransactionValue gross credit value of the trade
     * @param tariffRate            tariff rate (0.0 to 1.0)
     * @return collected tariff credits
     */
    public double calculateTariff(double grossTransactionValue, double tariffRate) {
        return Math.max(0.0, grossTransactionValue * Math.max(0.0, tariffRate));
    }

    /**
     * Refreshes active market orders and recalculates spot prices and shortcoming scores for all hubs.
     *
     * @param hubs list of commercial hubs to process
     * @return updated list of commercial hubs
     */
    public List<CommercialHub> updateCommercialHubs(List<CommercialHub> hubs) {
        if (hubs == null) {
            return List.of();
        }
        return hubs.stream().map(this::updateHub).toList();
    }

    /**
     * Updates an individual commercial hub's market orders.
     *
     * @param hub commercial hub to update
     * @return updated commercial hub
     */
    public CommercialHub updateHub(CommercialHub hub) {
        Map<String, MarketOrder> updatedOrders = new HashMap<>();
        for (Map.Entry<String, MarketOrder> entry : hub.activeOrders().entrySet()) {
            String resourceId = entry.getKey();
            MarketOrder order = entry.getValue();
            double spotPrice = calculateSpotPrice(DEFAULT_BASE_PRICE, order.supplyKg(), order.demandKg());
            double priceModifier = spotPrice / DEFAULT_BASE_PRICE;
            double shortcoming = calculateShortcomingScore(order.supplyKg(), order.demandKg(), priceModifier);

            updatedOrders.put(resourceId, new MarketOrder(
                    resourceId,
                    order.supplyKg(),
                    order.demandKg(),
                    spotPrice,
                    shortcoming
            ));
        }

        return new CommercialHub(
                hub.id(),
                hub.entityId(),
                hub.transactionTariffRate(),
                hub.storageCapacityKg(),
                hub.currentStoredWeightKg(),
                hub.logisticsRangeUnits(),
                updatedOrders
        );
    }
}
