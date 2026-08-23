package com.spaceconquest.engine.scenario;

import java.util.List;

/**
 * Defines a positive or negative species genetic/biological trait modifying empire capabilities.
 */
public record SpeciesTrait(
        String id,
        String name,
        String description,
        int pointCost,
        double researchModifier,
        double productionModifier,
        double growthModifier,
        double defenseModifier,
        double habitabilityModifier
) {
    public static final String TRAIT_INTELLIGENT = "trait_intelligent";
    public static final String TRAIT_INDUSTRIOUS = "trait_industrious";
    public static final String TRAIT_RESILIENT = "trait_resilient";
    public static final String TRAIT_RAPID_BREEDERS = "trait_rapid_breeders";
    public static final String TRAIT_ADAPTIVE = "trait_adaptive";
    public static final String TRAIT_FRAIL = "trait_frail";
    public static final String TRAIT_SEDENTARY = "trait_sedentary";
    public static final String TRAIT_WASTEFUL = "trait_wasteful";
    public static final String TRAIT_QUARRELSOME = "trait_quarrelsome";

    public static List<SpeciesTrait> getStandardTraits() {
        return List.of(
                new SpeciesTrait(TRAIT_INTELLIGENT, "Intelligent", "+20% Research points generation across all scientific disciplines", 2, 0.20, 0.0, 0.0, 0.0, 0.0),
                new SpeciesTrait(TRAIT_INDUSTRIOUS, "Industrious", "+20% Mining and industrial facility throughput", 2, 0.0, 0.20, 0.0, 0.0, 0.0),
                new SpeciesTrait(TRAIT_RESILIENT, "Resilient", "+25% Ground combat defense and planetary siege resistance", 1, 0.0, 0.0, 0.0, 0.25, 0.0),
                new SpeciesTrait(TRAIT_RAPID_BREEDERS, "Rapid Breeders", "+30% Demographic population reproduction rate", 2, 0.0, 0.0, 0.30, 0.0, 0.0),
                new SpeciesTrait(TRAIT_ADAPTIVE, "Extremophile Adaptive", "+20% Environmental habitability across harsh biomes", 2, 0.0, 0.0, 0.0, 0.0, 0.20),
                new SpeciesTrait(TRAIT_FRAIL, "Frail", "-15% Ground combat strength and worker physical endurance (+1 Trait Point)", -1, 0.0, -0.10, 0.0, -0.15, 0.0),
                new SpeciesTrait(TRAIT_SEDENTARY, "Sedentary", "-20% Interstellar migration and passenger movement speed (+1 Trait Point)", -1, 0.0, 0.0, -0.20, 0.0, 0.0),
                new SpeciesTrait(TRAIT_WASTEFUL, "Wasteful", "+25% Consumer goods demand and resource maintenance (+1 Trait Point)", -1, 0.0, -0.15, 0.0, 0.0, 0.0),
                new SpeciesTrait(TRAIT_QUARRELSOME, "Quarrelsome", "-15% Political stability and higher unrest risk (+1 Trait Point)", -1, -0.10, 0.0, 0.0, -0.10, 0.0)
        );
    }
}
