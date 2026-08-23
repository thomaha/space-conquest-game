package com.spaceconquest.engine;

/**
 * Represents an individual minister assignment to a government portfolio.
 *
 * @param portfolioId                    identifier of the ministry portfolio
 * @param assignedCitizenProfessionId    profession ID of the citizen filling the post
 * @param calculatedEfficiencyModifier   calculated efficiency bonus (e.g. 1.10 baseline, 1.25 synergy)
 */
public record MinistryAssignment(
        String portfolioId,
        String assignedCitizenProfessionId,
        double calculatedEfficiencyModifier
) {}
