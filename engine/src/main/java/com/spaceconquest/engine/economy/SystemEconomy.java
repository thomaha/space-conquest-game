package com.spaceconquest.engine.economy;

/**
 * Immutable snapshot of the public economic state and sector allocations for a solar system.
 * Governs Education, Law and order, Health and welfare, Infrastructure and Planetary militias.
 * The signed empire contribution rate is a fraction of the daily public budget.
 */
public record SystemEconomy(
        String systemId,
        String empireId,
        double educationAllocation,
        double lawAndOrderAllocation,
        double healthAndWelfareAllocation,
        double infrastructureAllocation,
        double planetaryMilitiasAllocation,
        double totalBudgetCredits,
        double accumulatedMilitiaInvestment,
        double educationLevel,
        double lawAndOrderLevel,
        double healthAndWelfareLevel,
        double infrastructureLevel,
        double planetaryMilitiaLevel,
        long employedTeachers,
        long employedScientists,
        long employedPolice,
        long employedMedics,
        long employedEngineers,
        long employedTechnicians,
        long employedSoldiers,
        long recruitableSoldiers,
        double taxRate,
        double empireContributionRate
) {
    public SystemEconomy(
            String systemId, String empireId,
            double educationAllocation, double lawAndOrderAllocation, double healthAndWelfareAllocation,
            double infrastructureAllocation, double planetaryMilitiasAllocation, double totalBudgetCredits,
            double accumulatedMilitiaInvestment, double educationLevel, double lawAndOrderLevel,
            double healthAndWelfareLevel, double infrastructureLevel, double planetaryMilitiaLevel,
            long employedTeachers, long employedScientists, long employedPolice, long employedMedics,
            long employedEngineers, long employedTechnicians, long employedSoldiers, long recruitableSoldiers,
            double taxRate
    ) {
        this(systemId, empireId, educationAllocation, lawAndOrderAllocation, healthAndWelfareAllocation,
                infrastructureAllocation, planetaryMilitiasAllocation, totalBudgetCredits,
                accumulatedMilitiaInvestment, educationLevel, lawAndOrderLevel, healthAndWelfareLevel,
                infrastructureLevel, planetaryMilitiaLevel, employedTeachers, employedScientists,
                employedPolice, employedMedics, employedEngineers, employedTechnicians, employedSoldiers,
                recruitableSoldiers, taxRate, 0.0);
    }

    public SystemEconomy withEmpireContributionRate(double rate) {
        return new SystemEconomy(systemId, empireId, educationAllocation, lawAndOrderAllocation,
                healthAndWelfareAllocation, infrastructureAllocation, planetaryMilitiasAllocation,
                totalBudgetCredits, accumulatedMilitiaInvestment, educationLevel, lawAndOrderLevel,
                healthAndWelfareLevel, infrastructureLevel, planetaryMilitiaLevel, employedTeachers,
                employedScientists, employedPolice, employedMedics, employedEngineers,
                employedTechnicians, employedSoldiers, recruitableSoldiers, taxRate, rate);
    }

    public static SystemEconomy createDefault(String systemId, String empireId, long population) {
        double defaultBudget = Math.max(1000.0, population * 0.002);
        return new SystemEconomy(
                systemId,
                empireId,
                0.20, 0.20, 0.20, 0.20, 0.20,
                defaultBudget,
                5000.0,
                1.0, 1.0, 1.0, 1.0, 1.0,
                Math.max(10, Math.round(population * 0.0005)),
                Math.max(10, Math.round(population * 0.0003)),
                Math.max(15, Math.round(population * 0.0008)),
                Math.max(10, Math.round(population * 0.0004)),
                Math.max(20, Math.round(population * 0.0010)),
                Math.max(30, Math.round(population * 0.0015)),
                Math.max(25, Math.round(population * 0.0012)),
                Math.max(100, Math.round(population * 0.0050)),
                0.10
        );
    }
}
