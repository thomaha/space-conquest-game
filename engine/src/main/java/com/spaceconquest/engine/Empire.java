package com.spaceconquest.engine;

import java.util.List;
import java.util.Map;

/**
 * Represents a sovereign empire government.
 *
 * @param id                         unique identifier
 * @param name                       display name of the empire
 * @param raceId                     primary race identifier
 * @param societyStructure           ideological alignment ("Individualist", "Collectivist", "Hive Mind")
 * @param treasuryCredits            liquid public treasury credits
 * @param corporateTaxRate           active tax rate applied to positive realized corporate profit
 * @param controlledSystemIds        list of controlled solar system identifiers
 * @param ministries                 cabinet ministry portfolio assignments
 * @param systemGovernorAssignments  mapping of solar system ID to assigned governor ID
 * @param unlockedTechIds            list of unlocked technology IDs
 * @param activeShipDesignIds        list of active spaceship design IDs
 */
public record Empire(
        String id,
        String name,
        String raceId,
        String societyStructure,
        double treasuryCredits,
        double corporateTaxRate,
        List<String> controlledSystemIds,
        List<MinistryAssignment> ministries,
        Map<String, String> systemGovernorAssignments,
        List<String> unlockedTechIds,
        List<String> activeShipDesignIds
) {
    public Empire {
        if (controlledSystemIds == null) controlledSystemIds = List.of();
        if (ministries == null) ministries = List.of();
        if (systemGovernorAssignments == null) systemGovernorAssignments = Map.of();
        if (unlockedTechIds == null) unlockedTechIds = List.of();
        if (activeShipDesignIds == null) activeShipDesignIds = List.of();
    }
}
