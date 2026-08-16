package com.spaceconquest.engine;

import java.util.List;

/**
 * Represents a practical application unlocked and improved by researching technologies.
 *
 * @param id                   unique identifier of the technical application
 * @param name                 display name of the application
 * @param description          detailed description of the application and its function
 * @param requiredTechnologies list of prerequisite technology IDs required for this application
 * @param affectedFactors      list of gameplay factors/metrics affected (e.g. power_production, food_production)
 * @param costToBuildPerUnit   base construction/production cost in work hours per unit
 * @param requiredMaterials    list of material IDs required to manufacture the application
 * @param complexity           how challenging the application is to produce
 */
public record TechnicalApplication(
    String id,
    String name,
    String description,
    List<String> requiredTechnologies,
    List<String> affectedFactors,
    double costToBuildPerUnit,
    List<String> requiredMaterials,
    int complexity
) {
    /**
     * Calculates the optimized unit cost based on optimization level.
     * Formula: Optimized unit cost = Base cost * 0.9^Optimization level
     *
     * @param optimizationLevel the number of optimization levels applied
     * @return the optimized cost per unit in work hours
     */
    public double calculateOptimizedUnitCost(int optimizationLevel) {
        return costToBuildPerUnit * Math.pow(0.9, optimizationLevel);
    }

    /**
     * Calculates the optimized complexity based on optimization level and random factor R.
     * Formula: Optimized complexity = Base complexity - (optimization level * R)
     *
     * @param optimizationLevel the number of optimization levels applied
     * @param r                 random scaling factor
     * @return the optimized complexity rating (minimum 1)
     */
    public int calculateOptimizedComplexity(int optimizationLevel, double r) {
        int optimized = (int) Math.round(complexity - (optimizationLevel * r));
        return Math.max(1, optimized);
    }
}
