package com.spaceconquest.engine.technology;

import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.MinistryAssignment;
import com.spaceconquest.engine.Race;

import java.util.List;
import java.util.Random;

/**
 * Simulates imperial scientific progression, non-deterministic breakthrough rolls,
 * dual-path optimization and salvage reverse-engineering.
 */
public class ResearchProcessor {

    private final Random random;

    public ResearchProcessor() {
        this(new Random());
    }

    public ResearchProcessor(Random random) {
        this.random = random;
    }

    /**
     * Calculates research points accumulated in a single turn for a project.
     * Formula: sum(Assigned Scientists * Training Level) * Species Intelligence Modifier * Ministry Efficiency * Speed Modifier
     */
    public double calculateTurnResearchPoints(
            int assignedScientists,
            double trainingLevel,
            Race race,
            Empire empire,
            double projectSpeedModifier
    ) {
        if (assignedScientists <= 0) {
            return 0.0;
        }

        double baseScientistOutput = assignedScientists * Math.max(0.1, trainingLevel);

        double intelligenceMod = 1.0;
        if (race != null && race.intelligence() > 0) {
            intelligenceMod = race.intelligence() >= 10.0 ? race.intelligence() / 100.0 : race.intelligence();
        }

        double ministryEfficiency = 1.0;
        if (empire != null && empire.ministries() != null) {
            for (MinistryAssignment assignment : empire.ministries()) {
                if ("ministry_technology_application".equalsIgnoreCase(assignment.portfolioId())
                        || "ministry_technology".equalsIgnoreCase(assignment.portfolioId())) {
                    ministryEfficiency = Math.max(0.5, assignment.calculatedEfficiencyModifier());
                    break;
                }
            }
        }

        return baseScientistOutput * intelligenceMod * ministryEfficiency * Math.max(0.1, projectSpeedModifier);
    }

    /**
     * Advances a research project by one turn.
     */
    public ResearchProject advanceProject(
            ResearchProject project,
            Race race,
            Empire empire,
            List<TechnologyExchangeRoute> exchangeRoutes
    ) {
        if (project == null || project.isComplete()) {
            return project;
        }

        double routeBonus = 0.0;
        if (exchangeRoutes != null) {
            for (TechnologyExchangeRoute route : exchangeRoutes) {
                if (route.receiverEmpireId().equals(project.empireId())
                        && route.technologyId().equals(project.targetTechOrAppId())) {
                    routeBonus += route.trainingSpeedBonus();
                }
            }
        }

        double effectiveSpeedModifier = project.speedModifier() * (1.0 + routeBonus);
        double turnPoints = calculateTurnResearchPoints(
                project.assignedScientists(),
                1.0,
                race,
                empire,
                effectiveSpeedModifier
        );

        double newAccumulated = project.accumulatedPoints() + turnPoints;
        return new ResearchProject(
                project.id(),
                project.empireId(),
                project.targetTechOrAppId(),
                project.isApplication(),
                newAccumulated,
                project.requiredPoints(),
                project.assignedScientists(),
                project.speedModifier()
        );
    }

    /**
     * Rolls against the weighted variance table to determine research breakthrough outcome.
     * Probabilities:
     * - Critical breakthrough: 5% (0.00 - 0.05)
     * - Optimized success: 45% (0.05 - 0.50)
     * - Incremental gain: 40% (0.50 - 0.90)
     * - Flawed setback: 10% (0.90 - 1.00)
     */
    public ResearchVarianceResult rollBreakthrough() {
        return rollBreakthrough(this.random.nextDouble());
    }

    public ResearchVarianceResult rollBreakthrough(double roll) {
        if (roll < 0.05) {
            return new ResearchVarianceResult(
                    ResearchVarianceResult.CRITICAL_BREAKTHROUGH,
                    1.20,
                    0.85,
                    0
            );
        } else if (roll < 0.50) {
            return new ResearchVarianceResult(
                    ResearchVarianceResult.OPTIMIZED_SUCCESS,
                    1.00,
                    1.00,
                    0
            );
        } else if (roll < 0.90) {
            return new ResearchVarianceResult(
                    ResearchVarianceResult.INCREMENTAL_GAIN,
                    1.10,
                    1.05,
                    1
            );
        } else {
            return new ResearchVarianceResult(
                    ResearchVarianceResult.FLAWED_SETBACK,
                    1.05,
                    1.25,
                    2
            );
        }
    }

    /**
     * Resolves optimization path selection for an OPTIMIZED_SUCCESS roll or active refinement.
     * Path A (Performance up-scaling): +15% output, +20% cost, +1 complexity
     * Path B (Efficiency down-scaling / miniaturization): 1.0x output, -15% cost, -1 complexity (min 1)
     */
    public ResearchVarianceResult evaluateOptimizationPath(String pathChoice, double varianceFactor) {
        double factor = (varianceFactor <= 0.0) ? 1.0 : varianceFactor;
        if ("PATH_A".equalsIgnoreCase(pathChoice) || "PERFORMANCE".equalsIgnoreCase(pathChoice)) {
            return new ResearchVarianceResult(
                    ResearchVarianceResult.OPTIMIZED_SUCCESS,
                    1.15 * factor,
                    1.20 * factor,
                    (int) Math.round(1.0 * factor)
            );
        } else {
            // Default to Path B (miniaturization)
            return new ResearchVarianceResult(
                    ResearchVarianceResult.OPTIMIZED_SUCCESS,
                    1.00,
                    0.85 * factor,
                    -(int) Math.round(1.0 * factor)
            );
        }
    }

    /**
     * Calculates research progress vectors injected from foreign salvage or captured debris.
     */
    public double calculateSalvageProgressPoints(
            boolean isIntact,
            int componentCount,
            Race analyzingRace,
            Race sourceRace,
            double baseRequiredPoints
    ) {
        if (componentCount <= 0 || baseRequiredPoints <= 0.0) {
            return 0.0;
        }

        double baseYieldFraction = isIntact ? 0.35 : 0.10;
        double volumeMultiplier = Math.min(2.5, 1.0 + 0.15 * (componentCount - 1));

        double translationModifier = 1.0;
        if (analyzingRace != null && sourceRace != null) {
            String compA = analyzingRace.chemicalComposition();
            String compB = sourceRace.chemicalComposition();
            if (compA != null && compB != null) {
                if (compA.equalsIgnoreCase(compB)) {
                    translationModifier = 1.0;
                } else if (compA.toLowerCase().contains("carbon") && compB.toLowerCase().contains("carbon")) {
                    translationModifier = 1.0;
                } else if ((compA.toLowerCase().contains("silicon") && compB.toLowerCase().contains("carbon"))
                        || (compA.toLowerCase().contains("carbon") && compB.toLowerCase().contains("silicon"))) {
                    translationModifier = 0.50;
                } else {
                    translationModifier = 0.30;
                }
            }
        }

        return baseRequiredPoints * baseYieldFraction * volumeMultiplier * translationModifier;
    }
}
