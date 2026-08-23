package com.spaceconquest.engine.terraforming;

import java.util.Map;

/**
 * Represents the atmospheric gas composition, surface pressure and environmental metrics of a celestial body.
 *
 * @param planetId               target celestial body ID
 * @param gasRatios              fractional distribution of atmospheric gases (e.g., oxygen_gas, nitrogen_gas, carbon_dioxide)
 * @param surfacePressureAtm     surface atmospheric pressure in Earth atmospheres (1.0 = 1 atm)
 * @param surfaceTemperatureK   mean surface equilibrium temperature in Kelvin
 * @param greenhouseFactor       thermal retention coefficient
 * @param radiationLevelRad      surface cosmic and stellar radiation in rads
 * @param biomeType              current ecological classification (BARREN, TOXIC, FROZEN, GREENHOUSE, BREATHABLE_TERRESTRIAL)
 * @param isBreathable           true if current gas ratios and pressure support biological respiration
 */
public record AtmosphericComposition(
        String planetId,
        Map<String, Double> gasRatios,
        double surfacePressureAtm,
        double surfaceTemperatureK,
        double greenhouseFactor,
        double radiationLevelRad,
        String biomeType,
        boolean isBreathable
) {
    public static final String BIOME_BARREN = "BARREN";
    public static final String BIOME_TOXIC = "TOXIC";
    public static final String BIOME_FROZEN = "FROZEN";
    public static final String BIOME_GREENHOUSE = "GREENHOUSE";
    public static final String BIOME_BREATHABLE_TERRESTRIAL = "BREATHABLE_TERRESTRIAL";
    public static final String BIOME_OCEANIC = "OCEANIC";
    public static final String BIOME_ARID = "ARID";

    public AtmosphericComposition {
        if (gasRatios == null) gasRatios = Map.of();
        if (biomeType == null) biomeType = BIOME_BARREN;
    }

    public double getGasRatio(String gasKey) {
        return gasRatios.getOrDefault(gasKey, 0.0);
    }
}
