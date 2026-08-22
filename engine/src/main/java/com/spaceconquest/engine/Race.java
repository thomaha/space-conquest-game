package com.spaceconquest.engine;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a biological, crystalline, gaseous or synthetic race archetype.
 *
 * @param id                        unique identifier of the race
 * @param name                      display name of the race
 * @param description               detailed narrative description of the race
 * @param intelligence              intelligence modifier (standard baseline 1.0)
 * @param physicalStrength          physical strength modifier (standard baseline 1.0)
 * @param societyStructure          societal structure (Individualist, Collectivist, Hive mind)
 * @param preferredGForce           optimal gravitational constant in standard Gs
 * @param preferredTemperature      optimal ambient environment temperature in Kelvin
 * @param chemicalComposition       primary chemical element scaffolding
 * @param breathingAtmosphere       required respiration or atmospheric gas compound
 * @param fertileAgeStart           start of biological fertile reproduction age window
 * @param fertileAgeEnd             end of biological fertile reproduction age window
 * @param nutrientType              nutrient resource classification consumed (Organic, Rock, Metal, Gas, Electricity)
 * @param nutrientSpreadRequirement nutrient diet diversity requirement (Simple, Diverse)
 * @param naturalLifespan           baseline natural life expectancy in years before tech augmentation
 */
public record Race(
    String id,
    String name,
    String description,
    double intelligence,
    double physicalStrength,
    String societyStructure,
    double preferredGForce,
    double preferredTemperature,
    String chemicalComposition,
    String breathingAtmosphere,
    int fertileAgeStart,
    int fertileAgeEnd,
    String nutrientType,
    String nutrientSpreadRequirement,
    @JsonProperty("naturalLifespan")
    @JsonAlias({"retirementAge", "naturalLifeSpan"})
    int naturalLifespan
) {
    /**
     * Backward-compatible alias for naturalLifespan.
     *
     * @return the natural lifespan in years
     */
    public int retirementAge() {
        return naturalLifespan;
    }

    /**
     * Calculates the species actual intelligence score against the standardized human base value (5).
     * Formula: Species Actual Intelligence Score = Human Base Value (5) * Race Intelligence Float
     *
     * @return the calculated actual intelligence score
     */
    public double calculateActualIntelligenceScore() {
        return 5.0 * intelligence;
    }

    /**
     * Calculates the species actual physical strength score against the standardized human base value (5).
     * Formula: Species Actual Physical Strength Score = Human Base Value (5) * Race Physical Strength Float
     *
     * @return the calculated actual physical strength score
     */
    public double calculateActualStrengthScore() {
        return 5.0 * physicalStrength;
    }
}
