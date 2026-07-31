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
 * @param retirementAge       default retirement age for the profession
 */
public record Profession(
        String id,
        String name,
        String description,
        String type,
        int minimumIntelligence,
        int minimumStrength,
        int complexity,
        int retirementAge
) {
}
