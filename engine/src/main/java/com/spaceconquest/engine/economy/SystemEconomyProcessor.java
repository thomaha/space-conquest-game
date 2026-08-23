package com.spaceconquest.engine.economy;

import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Moon;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.SolarSystem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simulates turn-based system economic public sector spending, efficiency indices,
 * dynamic employee quotas, happiness modifiers, and accumulated militia investments.
 */
public class SystemEconomyProcessor {

    public static final double BASELINE_SECTOR_PER_CAPITA = 0.0004;
    public static final double MILITIA_INVESTMENT_DECAY = 0.95;
    public static final double STANDARD_MILITIA_INVESTMENT_CAP = 10000.0;

    /**
     * Calculates the sector efficiency level index based on per-capita spending.
     * Baseline is 1.0 when funded at standard rate (0.0004 credits per citizen per sector).
     *
     * @param totalBudgetCredits total system budget in credits
     * @param sectorAllocation fraction of budget allocated to this sector (0.0 to 1.0)
     * @param systemPopulation total population across all colonies in the system
     * @return efficiency index clamped between 0.0 and 3.0
     */
    public double calculateSectorEfficiency(double totalBudgetCredits, double sectorAllocation, long systemPopulation) {
        if (systemPopulation <= 0) {
            return 1.0;
        }
        double sectorBudget = Math.max(0.0, totalBudgetCredits) * Math.max(0.0, sectorAllocation);
        double perCapita = sectorBudget / (double) systemPopulation;
        double index = perCapita / BASELINE_SECTOR_PER_CAPITA;
        return Math.min(3.0, Math.max(0.0, index));
    }

    /**
     * Calculates dynamic militia conscription efficiency (0.30 to 0.90) from accumulated militia investment.
     *
     * @param accumulatedMilitiaInvestment historical militia funding with decay
     * @return militia combat power factor (0.30 to 0.90)
     */
    public double calculateMilitiaCombatEfficiency(double accumulatedMilitiaInvestment) {
        double ratio = Math.min(1.0, Math.max(0.0, accumulatedMilitiaInvestment / STANDARD_MILITIA_INVESTMENT_CAP));
        return 0.30 + (ratio * 0.60);
    }

    /**
     * Updates a single system economy snapshot for a turn.
     *
     * @param current current system economy record
     * @param systemPopulation total population in the system
     * @param isHiveMind whether the empire is a Hive Mind (immune to currency, fixed 1.0 baseline)
     * @return updated system economy record
     */
    public SystemEconomy processSystemEconomy(SystemEconomy current, long systemPopulation, boolean isHiveMind) {
        if (current == null) {
            return null;
        }

        if (isHiveMind) {
            return new SystemEconomy(
                    current.systemId(),
                    current.empireId(),
                    0.20, 0.20, 0.20, 0.20, 0.20,
                    0.0,
                    5000.0,
                    1.0, 1.0, 1.0, 1.0, 1.0,
                    Math.max(10, (long) (systemPopulation * 0.0005)),
                    Math.max(10, (long) (systemPopulation * 0.0003)),
                    Math.max(15, (long) (systemPopulation * 0.0008)),
                    Math.max(10, (long) (systemPopulation * 0.0004)),
                    Math.max(20, (long) (systemPopulation * 0.0010)),
                    Math.max(30, (long) (systemPopulation * 0.0015)),
                    Math.max(25, (long) (systemPopulation * 0.0012)),
                    Math.max(100, (long) (systemPopulation * 0.0050))
            );
        }

        double totalBudget = current.totalBudgetCredits();
        double eduIndex = calculateSectorEfficiency(totalBudget, current.educationAllocation(), systemPopulation);
        double lawIndex = calculateSectorEfficiency(totalBudget, current.lawAndOrderAllocation(), systemPopulation);
        double healthIndex = calculateSectorEfficiency(totalBudget, current.healthAndWelfareAllocation(), systemPopulation);
        double infraIndex = calculateSectorEfficiency(totalBudget, current.infrastructureAllocation(), systemPopulation);
        double militiaIndex = calculateSectorEfficiency(totalBudget, current.planetaryMilitiasAllocation(), systemPopulation);

        double turnMilitiaSpending = Math.max(0.0, totalBudget * current.planetaryMilitiasAllocation());
        double updatedMilitiaInvestment = (current.accumulatedMilitiaInvestment() * MILITIA_INVESTMENT_DECAY) + turnMilitiaSpending;

        long teachers = Math.max(10, Math.round(systemPopulation * 0.0005 * eduIndex));
        long scientists = Math.max(10, Math.round(systemPopulation * 0.0003 * eduIndex));
        long police = Math.max(15, Math.round(systemPopulation * 0.0008 * lawIndex));
        long medics = Math.max(10, Math.round(systemPopulation * 0.0004 * healthIndex));
        long engineers = Math.max(20, Math.round(systemPopulation * 0.0010 * infraIndex));
        long technicians = Math.max(30, Math.round(systemPopulation * 0.0015 * infraIndex));
        long soldiers = Math.max(25, Math.round(systemPopulation * 0.0012 * militiaIndex));
        long recruitable = Math.max(100, Math.round(systemPopulation * 0.0050 * militiaIndex));

        return new SystemEconomy(
                current.systemId(),
                current.empireId(),
                current.educationAllocation(),
                current.lawAndOrderAllocation(),
                current.healthAndWelfareAllocation(),
                current.infrastructureAllocation(),
                current.planetaryMilitiasAllocation(),
                current.totalBudgetCredits(),
                updatedMilitiaInvestment,
                eduIndex,
                lawIndex,
                healthIndex,
                infraIndex,
                militiaIndex,
                teachers,
                scientists,
                police,
                medics,
                engineers,
                technicians,
                soldiers,
                recruitable
        );
    }

    /**
     * Computes the net happiness modifier generated by the 5 public sectors in a solar system.
     *
     * @param economy system economy record
     * @return net happiness modifier (e.g. +0.05 per well-funded sector, negative for austerity)
     */
    public double calculateHappinessModifier(SystemEconomy economy) {
        if (economy == null) return 0.0;
        double sum = (economy.educationLevel() - 1.0)
                + (economy.lawAndOrderLevel() - 1.0)
                + (economy.healthAndWelfareLevel() - 1.0)
                + (economy.infrastructureLevel() - 1.0)
                + (economy.planetaryMilitiaLevel() - 1.0);
        return sum * 0.05;
    }

    /**
     * Processes all system economies in the current game state.
     *
     * @param state simulation game state
     * @return result containing updated system economies and empire treasury deductions
     */
    public SystemEconomyTurnResult processSystemEconomies(GameState state) {
        if (state == null) {
            return new SystemEconomyTurnResult(List.of(), Map.of(), List.of());
        }

        Map<String, Long> systemPopulations = new HashMap<>();
        for (SolarSystem sys : state.solarSystems()) {
            long total = 0;
            if (sys.planets() != null) {
                for (Planet p : sys.planets()) {
                    if (p.populations() != null) {
                        for (Population pop : p.populations()) {
                            total += pop.totalCount();
                        }
                    }
                    if (p.moons() != null) {
                        for (Moon m : p.moons()) {
                            if (m.populations() != null) {
                                for (Population pop : m.populations()) {
                                    total += pop.totalCount();
                                }
                            }
                        }
                    }
                }
            }
            systemPopulations.put(sys.id(), total);
        }

        Map<String, Empire> empireMap = new HashMap<>();
        for (Empire emp : state.empires()) {
            empireMap.put(emp.id(), emp);
        }

        List<SystemEconomy> updatedEconomies = new ArrayList<>();
        Map<String, Double> budgetDeductionsByEmpire = new HashMap<>();
        Map<String, Double> happinessBySystem = new HashMap<>();

        for (SystemEconomy economy : state.systemEconomies()) {
            long pop = systemPopulations.getOrDefault(economy.systemId(), 0L);
            Empire emp = empireMap.get(economy.empireId());
            boolean isHiveMind = emp != null && ("Hive Mind".equalsIgnoreCase(emp.societyStructure()) || "Hive mind".equalsIgnoreCase(emp.societyStructure()));

            SystemEconomy updated = processSystemEconomy(economy, pop, isHiveMind);
            updatedEconomies.add(updated);

            if (!isHiveMind && updated.totalBudgetCredits() > 0) {
                budgetDeductionsByEmpire.merge(updated.empireId(), updated.totalBudgetCredits(), Double::sum);
            }

            happinessBySystem.put(updated.systemId(), calculateHappinessModifier(updated));
        }

        // Apply treasury deductions to empires
        List<Empire> updatedEmpires = new ArrayList<>();
        for (Empire emp : state.empires()) {
            double deduction = budgetDeductionsByEmpire.getOrDefault(emp.id(), 0.0);
            if (deduction > 0) {
                double newTreasury = Math.max(0.0, emp.treasuryCredits() - deduction);
                updatedEmpires.add(new Empire(
                        emp.id(),
                        emp.name(),
                        emp.raceId(),
                        emp.societyStructure(),
                        newTreasury,
                        emp.corporateTaxRate(),
                        emp.controlledSystemIds(),
                        emp.ministries(),
                        emp.systemGovernorAssignments(),
                        emp.unlockedTechIds(),
                        emp.activeShipDesignIds()
                ));
            } else {
                updatedEmpires.add(emp);
            }
        }

        return new SystemEconomyTurnResult(updatedEconomies, happinessBySystem, updatedEmpires);
    }

    public record SystemEconomyTurnResult(
            List<SystemEconomy> updatedEconomies,
            Map<String, Double> happinessModifiersBySystem,
            List<Empire> updatedEmpires
    ) {}
}
