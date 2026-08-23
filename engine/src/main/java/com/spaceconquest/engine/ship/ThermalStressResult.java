package com.spaceconquest.engine.ship;

/**
 * Result of evaluating atmospheric entry corridor thermal stress against hull structural material limits.
 *
 * @param entryTemperatureK       peak ambient friction temperature reached during descent in Kelvin
 * @param materialMeltingPointK   melting point of the hull structural material in Kelvin
 * @param isHeatShieldEquipped    true if specialized ablative or ceramic thermal tiles are fitted
 * @param suffersThermalFailure   true if hull material melted or suffered catastrophic structural degradation
 * @param hullDamagePercentage    percentage of structural hull damage sustained (0.0 to 100.0)
 */
public record ThermalStressResult(
        double entryTemperatureK,
        double materialMeltingPointK,
        boolean isHeatShieldEquipped,
        boolean suffersThermalFailure,
        double hullDamagePercentage
) {}
