package com.spaceconquest.frontend.empire;

public record ColonyEconomyEntry(
        String bodyId,
        String bodyName,
        String systemName,
        boolean isMoon,
        long population,
        double grossOutputCredits,
        double taxCollectedCredits,
        double localGovernanceCostCredits,
        double netContributionCredits,
        double outstandingDebtCredits
) {}
