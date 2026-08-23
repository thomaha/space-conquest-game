package com.spaceconquest.engine.industry.refinement;

import java.util.Map;

/**
 * Standard recipe definition for chemical and metallurgical material refinement and consumer goods.
 *
 * @param id                    unique recipe identifier
 * @param name                  human-readable recipe name
 * @param category              recipe category (PYROMETALLURGY, ZERO_G, ALLOYING, SYNTHETIC_FUEL, CONSUMER_GOODS)
 * @param operationalEnvironment execution constraint (UNIVERSAL, GRAVITY_LOCKED, MICROGRAVITY_NATIVE)
 * @param minGravityG           minimum gravitational force required if GRAVITY_LOCKED
 * @param maxGravityG           maximum gravitational force allowed if MICROGRAVITY_NATIVE
 * @param inputMaterialsKg      required input resources in kg
 * @param powerDrawKw           turn-based electrical power draw in kW
 * @param outputMaterialsKg     primary output products in kg
 * @param byproductMaterialsKg  secondary recovered byproducts in kg
 * @param minFacilityTier       minimum facility tier required
 * @param primaryProfessionId   supervising profession
 */
public record RefinementRecipe(
        String id,
        String name,
        String category,
        String operationalEnvironment,
        double minGravityG,
        double maxGravityG,
        Map<String, Double> inputMaterialsKg,
        double powerDrawKw,
        Map<String, Double> outputMaterialsKg,
        Map<String, Double> byproductMaterialsKg,
        int minFacilityTier,
        String primaryProfessionId
) {
    public static final String ENV_UNIVERSAL = "UNIVERSAL";
    public static final String ENV_GRAVITY_LOCKED = "GRAVITY_LOCKED";
    public static final String ENV_MICROGRAVITY_NATIVE = "MICROGRAVITY_NATIVE";

    public RefinementRecipe {
        if (inputMaterialsKg == null) inputMaterialsKg = Map.of();
        if (outputMaterialsKg == null) outputMaterialsKg = Map.of();
        if (byproductMaterialsKg == null) byproductMaterialsKg = Map.of();
        if (operationalEnvironment == null) operationalEnvironment = ENV_UNIVERSAL;
    }
}
