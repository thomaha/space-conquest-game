package com.spaceconquest.engine;

/**
 * A profession a group of population can be trained for.
 *
 * @param id                  unique identifier
 * @param name                display name
 * @param description         short description of the profession
 * @param type                what the profession is for (soldier, farmer, miner, scientist, ...)
 * @param minimumIntelligence minimum intelligence required for the profession
 * @param minimumStrength     minimum physical strength required for the profession
 * @param complexity          how complex the profession is, influencing training speed and experience needed
 * @param retirementAge       retirement age percentage/modifier for the profession, applied to race baseline retirement age
 */
public record Profession(
        String id,
        String name,
        String description,
        String type,
        int minimumIntelligence,
        int minimumStrength,
        int complexity,
        double retirementAge
) {
    /**
     * Calculates the effective retirement age for a given baseline retirement age.
     *
     * @param baselineRetirementAge the race's baseline retirement age (potentially modified by technologies)
     * @return the calculated retirement age for this profession
     */
    public int calculateRetirementAge(int baselineRetirementAge) {
        double factor = retirementAge > 2.0 ? retirementAge / 100.0 : retirementAge;
        return (int) Math.round(baselineRetirementAge * factor);
    }

    /**
     * Calculates the effective retirement age for a given race based on its baseline retirement age.
     *
     * @param race the race
     * @return the calculated retirement age for this profession and race
     */
    public int calculateRetirementAge(Race race) {
        return calculateRetirementAge(race.retirementAge());
    }
}
