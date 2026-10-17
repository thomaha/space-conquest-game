package com.spaceconquest.engine;

import com.spaceconquest.engine.technology.ResearchVarianceResult;

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
 * @param combatModule         optional application-defined installed combat hardware
 * @param effectStats          operational capabilities scaled by researched performance modifiers
 * @param productionRecipe     optional application-defined industrial input and output recipe
 */
public record TechnicalApplication(
    String id,
    String name,
    String description,
    List<String> requiredTechnologies,
    List<String> affectedFactors,
    double costToBuildPerUnit,
    List<String> requiredMaterials,
    int complexity,
    com.spaceconquest.engine.ship.ShipModule combatModule,
    List<String> effectStats,
    com.spaceconquest.engine.industry.IndustryRecipeCatalog.Recipe productionRecipe,
    com.spaceconquest.engine.combat.AuxiliaryHardware auxiliaryHardware,
    Double researchPoints,
    Double refinementResearchPoints,
    com.spaceconquest.engine.industry.PowerPlantCatalog.Plant powerPlant,
    com.spaceconquest.engine.industry.OperatingReservePolicy operatingReserve,
    com.spaceconquest.engine.ship.ShipyardTechnology shipyardTechnology,
    com.spaceconquest.engine.ship.HullTechnology hullTechnology,
    com.spaceconquest.engine.ship.ShipModule miningModule,
    com.spaceconquest.engine.logistics.InfrastructureService infrastructureService,
    com.spaceconquest.engine.industry.SurveyCapability surveyCapability
) {
    public TechnicalApplication(String id, String name, String description, List<String> requiredTechnologies,
            List<String> affectedFactors, double costToBuildPerUnit, List<String> requiredMaterials, int complexity,
            com.spaceconquest.engine.ship.ShipModule combatModule, List<String> effectStats,
            com.spaceconquest.engine.industry.IndustryRecipeCatalog.Recipe productionRecipe,
            com.spaceconquest.engine.combat.AuxiliaryHardware auxiliaryHardware, Double researchPoints, Double refinementResearchPoints,
            com.spaceconquest.engine.industry.PowerPlantCatalog.Plant powerPlant,
            com.spaceconquest.engine.industry.OperatingReservePolicy operatingReserve,
            com.spaceconquest.engine.ship.ShipyardTechnology shipyardTechnology,
            com.spaceconquest.engine.ship.HullTechnology hullTechnology, com.spaceconquest.engine.ship.ShipModule miningModule,
            com.spaceconquest.engine.logistics.InfrastructureService infrastructureService) {
        this(id, name, description, requiredTechnologies, affectedFactors, costToBuildPerUnit, requiredMaterials, complexity,
                combatModule, effectStats, productionRecipe, auxiliaryHardware, researchPoints, refinementResearchPoints,
                powerPlant, operatingReserve, shipyardTechnology, hullTechnology, miningModule, infrastructureService, null);
    }
    public TechnicalApplication(String id, String name, String description, List<String> requiredTechnologies,
            List<String> affectedFactors, double costToBuildPerUnit, List<String> requiredMaterials, int complexity,
            com.spaceconquest.engine.ship.ShipModule combatModule, List<String> effectStats,
            com.spaceconquest.engine.industry.IndustryRecipeCatalog.Recipe productionRecipe,
            com.spaceconquest.engine.combat.AuxiliaryHardware auxiliaryHardware, Double researchPoints, Double refinementResearchPoints,
            com.spaceconquest.engine.industry.PowerPlantCatalog.Plant powerPlant,
            com.spaceconquest.engine.industry.OperatingReservePolicy operatingReserve,
            com.spaceconquest.engine.ship.ShipyardTechnology shipyardTechnology,
            com.spaceconquest.engine.ship.HullTechnology hullTechnology, com.spaceconquest.engine.ship.ShipModule miningModule) {
        this(id, name, description, requiredTechnologies, affectedFactors, costToBuildPerUnit, requiredMaterials, complexity,
                combatModule, effectStats, productionRecipe, auxiliaryHardware, researchPoints, refinementResearchPoints,
                powerPlant, operatingReserve, shipyardTechnology, hullTechnology, miningModule, null);
    }
    public TechnicalApplication(String id, String name, String description, List<String> requiredTechnologies,
            List<String> affectedFactors, double costToBuildPerUnit, List<String> requiredMaterials, int complexity,
            com.spaceconquest.engine.ship.ShipModule combatModule, List<String> effectStats,
            com.spaceconquest.engine.industry.IndustryRecipeCatalog.Recipe productionRecipe,
            com.spaceconquest.engine.combat.AuxiliaryHardware auxiliaryHardware, Double researchPoints, Double refinementResearchPoints,
            com.spaceconquest.engine.industry.PowerPlantCatalog.Plant powerPlant,
            com.spaceconquest.engine.industry.OperatingReservePolicy operatingReserve,
            com.spaceconquest.engine.ship.ShipyardTechnology shipyardTechnology, com.spaceconquest.engine.ship.HullTechnology hullTechnology) {
        this(id, name, description, requiredTechnologies, affectedFactors, costToBuildPerUnit, requiredMaterials, complexity,
                combatModule, effectStats, productionRecipe, auxiliaryHardware, researchPoints, refinementResearchPoints, powerPlant,
                operatingReserve, shipyardTechnology, hullTechnology, null);
    }
    public TechnicalApplication {
        requiredTechnologies = requiredTechnologies == null ? List.of() : List.copyOf(requiredTechnologies);
        affectedFactors = affectedFactors == null ? List.of() : List.copyOf(affectedFactors);
        requiredMaterials = requiredMaterials == null ? List.of() : List.copyOf(requiredMaterials);
        effectStats = effectStats == null ? List.of() : List.copyOf(effectStats);
        for (Double points : new Double[]{researchPoints, refinementResearchPoints})
            if (points != null && (!Double.isFinite(points) || points <= 0))
                throw new IllegalArgumentException("Invalid application research cost for " + id);
    }
    public TechnicalApplication(String id, String name, String description, List<String> requiredTechnologies,
                                List<String> affectedFactors, double costToBuildPerUnit, List<String> requiredMaterials,
                                int complexity, com.spaceconquest.engine.ship.ShipModule combatModule, List<String> effectStats,
                                com.spaceconquest.engine.industry.IndustryRecipeCatalog.Recipe productionRecipe,
                                com.spaceconquest.engine.combat.AuxiliaryHardware auxiliaryHardware, Double researchPoints, Double refinementResearchPoints,
                                com.spaceconquest.engine.industry.PowerPlantCatalog.Plant powerPlant,
                                com.spaceconquest.engine.industry.OperatingReservePolicy operatingReserve,
                                com.spaceconquest.engine.ship.ShipyardTechnology shipyardTechnology) {
        this(id, name, description, requiredTechnologies, affectedFactors, costToBuildPerUnit, requiredMaterials, complexity,
                combatModule, effectStats, productionRecipe, auxiliaryHardware, researchPoints, refinementResearchPoints, powerPlant, operatingReserve, shipyardTechnology, null);
    }
    public TechnicalApplication(String id, String name, String description, List<String> requiredTechnologies,
                                List<String> affectedFactors, double costToBuildPerUnit, List<String> requiredMaterials,
                                int complexity, com.spaceconquest.engine.ship.ShipModule combatModule, List<String> effectStats,
                                com.spaceconquest.engine.industry.IndustryRecipeCatalog.Recipe productionRecipe,
                                com.spaceconquest.engine.combat.AuxiliaryHardware auxiliaryHardware,
                                Double researchPoints, Double refinementResearchPoints,
                                com.spaceconquest.engine.industry.PowerPlantCatalog.Plant powerPlant,
                                com.spaceconquest.engine.industry.OperatingReservePolicy operatingReserve) {
        this(id, name, description, requiredTechnologies, affectedFactors, costToBuildPerUnit, requiredMaterials,
                complexity, combatModule, effectStats, productionRecipe, auxiliaryHardware, researchPoints,
                refinementResearchPoints, powerPlant, operatingReserve, null);
    }
    public TechnicalApplication(String id, String name, String description, List<String> requiredTechnologies,
                                List<String> affectedFactors, double costToBuildPerUnit, List<String> requiredMaterials,
                                int complexity, com.spaceconquest.engine.ship.ShipModule combatModule, List<String> effectStats,
                                com.spaceconquest.engine.industry.IndustryRecipeCatalog.Recipe productionRecipe,
                                com.spaceconquest.engine.combat.AuxiliaryHardware auxiliaryHardware,
                                Double researchPoints, Double refinementResearchPoints,
                                com.spaceconquest.engine.industry.PowerPlantCatalog.Plant powerPlant) {
        this(id, name, description, requiredTechnologies, affectedFactors, costToBuildPerUnit, requiredMaterials,
                complexity, combatModule, effectStats, productionRecipe, auxiliaryHardware, researchPoints, refinementResearchPoints, powerPlant, null);
    }
    public TechnicalApplication(String id, String name, String description, List<String> requiredTechnologies,
                                List<String> affectedFactors, double costToBuildPerUnit, List<String> requiredMaterials,
                                int complexity, com.spaceconquest.engine.ship.ShipModule combatModule, List<String> effectStats,
                                com.spaceconquest.engine.industry.IndustryRecipeCatalog.Recipe productionRecipe,
                                com.spaceconquest.engine.combat.AuxiliaryHardware auxiliaryHardware,
                                Double researchPoints, Double refinementResearchPoints) {
        this(id, name, description, requiredTechnologies, affectedFactors, costToBuildPerUnit, requiredMaterials,
                complexity, combatModule, effectStats, productionRecipe, auxiliaryHardware, researchPoints, refinementResearchPoints, null);
    }
    public TechnicalApplication(String id, String name, String description, List<String> requiredTechnologies,
                                List<String> affectedFactors, double costToBuildPerUnit, List<String> requiredMaterials,
                                int complexity, com.spaceconquest.engine.ship.ShipModule combatModule, List<String> effectStats,
                                com.spaceconquest.engine.industry.IndustryRecipeCatalog.Recipe productionRecipe,
                                com.spaceconquest.engine.combat.AuxiliaryHardware auxiliaryHardware) {
        this(id, name, description, requiredTechnologies, affectedFactors, costToBuildPerUnit, requiredMaterials,
                complexity, combatModule, effectStats, productionRecipe, auxiliaryHardware, null, null);
    }
    public TechnicalApplication(String id, String name, String description, List<String> requiredTechnologies,
                                List<String> affectedFactors, double costToBuildPerUnit, List<String> requiredMaterials,
                                int complexity, com.spaceconquest.engine.ship.ShipModule combatModule, List<String> effectStats,
                                com.spaceconquest.engine.industry.IndustryRecipeCatalog.Recipe productionRecipe) {
        this(id, name, description, requiredTechnologies, affectedFactors, costToBuildPerUnit, requiredMaterials,
                complexity, combatModule, effectStats, productionRecipe, null);
    }
    public TechnicalApplication(String id, String name, String description, List<String> requiredTechnologies,
                                List<String> affectedFactors, double costToBuildPerUnit,
                                List<String> requiredMaterials, int complexity) {
        this(id, name, description, requiredTechnologies, affectedFactors, costToBuildPerUnit, requiredMaterials,
                complexity, null, List.of(), null);
    }
    public TechnicalApplication(String id, String name, String description, List<String> requiredTechnologies,
                                List<String> affectedFactors, double costToBuildPerUnit, List<String> requiredMaterials,
                                int complexity, com.spaceconquest.engine.ship.ShipModule combatModule, List<String> effectStats) {
        this(id, name, description, requiredTechnologies, affectedFactors, costToBuildPerUnit, requiredMaterials,
                complexity, combatModule, effectStats, null);
    }
    /** Work hours per unit after applying the selected development path. */
    public double calculateOptimizedUnitCost(ResearchVarianceResult modifiers) {
        return costToBuildPerUnit * modifiers.costMultiplier();
    }

    /** Manufacturing complexity after applying the path, with a minimum of one. */
    public int calculateOptimizedComplexity(ResearchVarianceResult modifiers) {
        return Math.max(1, complexity + modifiers.complexityShift());
    }

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
