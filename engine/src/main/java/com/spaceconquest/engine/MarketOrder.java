package com.spaceconquest.engine;

/**
 * Represents a market order or commodity price state at a commercial hub.
 *
 * @param resourceId        material or product identifier
 * @param supplyKg          available supply in kilograms
 * @param demandKg          local demand in kilograms
 * @param pricePerKg        calculated spot price per kilogram
 * @param shortcomingScore  shortcoming score (S_m) indicating deficit severity
 */
public record MarketOrder(
        String resourceId,
        double supplyKg,
        double demandKg,
        double pricePerKg,
        double shortcomingScore
) {}
