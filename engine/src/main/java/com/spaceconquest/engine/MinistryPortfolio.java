package com.spaceconquest.engine;

import java.util.List;

/**
 * Definition of an imperial ministry portfolio archetype loaded from configuration.
 *
 * @param id                         unique identifier
 * @param name                       display name
 * @param description                description of ministry responsibilities
 * @param optimalProfessionIds       professions granting the expertise synergy bonus
 * @param baseEfficiencyModifier     baseline efficiency multiplier (e.g. 1.10 for bureaucrats)
 * @param synergyEfficiencyModifier  synergy efficiency multiplier (e.g. 1.25 for matching backgrounds)
 * @param targetModifiers            list of gameplay modifier keys affected
 */
public record MinistryPortfolio(
        String id,
        String name,
        String description,
        List<String> optimalProfessionIds,
        double baseEfficiencyModifier,
        double synergyEfficiencyModifier,
        List<String> targetModifiers
) {
    public MinistryPortfolio {
        if (optimalProfessionIds == null) optimalProfessionIds = List.of();
        if (targetModifiers == null) targetModifiers = List.of();
    }
}
