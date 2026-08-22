package com.spaceconquest.engine;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A profession a group of population can be trained for.
 *
 * @param id                           unique identifier
 * @param name                         display name
 * @param description                  short description of the profession
 * @param type                         what the profession is for (soldier, farmer, miner, scientist, ...)
 * @param minimumIntelligence          minimum intelligence required for the profession
 * @param minimumStrength              minimum physical strength required for the profession
 * @param complexity                   how complex the profession is, influencing training speed and experience needed
 * @param retirementLifespanPercentage retirement lifespan percentage modifier, applied to race natural lifespan
 */
public record Profession(
        String id,
        String name,
        String description,
        String type,
        int minimumIntelligence,
        int minimumStrength,
        int complexity,
        @JsonProperty("retirementLifespanPercentage")
        @JsonAlias({"retirementAge", "retirementPercentage", "retirementLifespanPercentage"})
        double retirementLifespanPercentage
) {
    /**
     * Backward-compatible alias for retirementLifespanPercentage.
     *
     * @return the retirement lifespan percentage
     */
    public double retirementAge() {
        return retirementLifespanPercentage;
    }

    /**
     * Calculates the exact floating-point chronological retirement age for a given natural lifespan.
     * Formula: Chronological Retirement Age = Natural Lifespan * Retirement Lifespan Percentage
     *
     * @param naturalLifespan the race's natural lifespan
     * @return the exact calculated retirement age
     */
    public double calculateExactRetirementAge(double naturalLifespan) {
        double factor = retirementLifespanPercentage > 2.0 ? retirementLifespanPercentage / 100.0 : retirementLifespanPercentage;
        return naturalLifespan * factor;
    }

    /**
     * Calculates the effective chronological retirement age for a given baseline natural lifespan.
     * Formula: Chronological Retirement Age = Natural Lifespan * Retirement Lifespan Percentage
     *
     * @param naturalLifespan the race's baseline natural lifespan (potentially modified by technologies)
     * @return the calculated retirement age for this profession
     */
    public int calculateRetirementAge(int naturalLifespan) {
        return (int) Math.round(calculateExactRetirementAge(naturalLifespan));
    }

    /**
     * Calculates the effective chronological retirement age for a given race based on its natural lifespan.
     *
     * @param race the race
     * @return the calculated retirement age for this profession and race
     */
    public int calculateRetirementAge(Race race) {
        return calculateRetirementAge(race.naturalLifespan());
    }

    /**
     * Checks if a race meets the minimum intelligence and strength requirements for this profession.
     *
     * @param race the race to evaluate
     * @return true if the race meets both requirements, false otherwise
     */
    public boolean qualifies(Race race) {
        return race.calculateActualIntelligenceScore() >= minimumIntelligence
                && race.calculateActualStrengthScore() >= minimumStrength;
    }
}
