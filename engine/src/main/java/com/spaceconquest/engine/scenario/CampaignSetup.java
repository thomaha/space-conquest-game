package com.spaceconquest.engine.scenario;

/**
 * Configuration parameters for customizing a new campaign scenario.
 *
 * @param campaignName              custom campaign title
 * @param starSystemCount           number of solar systems in galaxy
 * @param aiEmpireCount             number of AI empires to generate
 * @param nebulaDensity             nebula gas field density factor (0.0 to 1.0)
 * @param aiPersonalityDistribution distribution mode (BALANCED, AGGRESSIVE, ISOLATIONIST, MERCANTILE, SCIENTIFIC)
 * @param startingTechTier          initial technology level (1 to 4)
 * @param victoryConditionType      primary victory condition (DOMINATION, ECONOMIC_MONOPOLY, MEGASTRUCTURE_ASCENSION, DIPLOMATIC_FEDERATION)
 * @param targetVictoryThreshold    numeric goal threshold
 */
public record CampaignSetup(
        String campaignName,
        int starSystemCount,
        int aiEmpireCount,
        double nebulaDensity,
        String aiPersonalityDistribution,
        int startingTechTier,
        String victoryConditionType,
        int targetVictoryThreshold
) {
    public static final String VICTORY_DOMINATION = "DOMINATION";
    public static final String VICTORY_ECONOMIC_MONOPOLY = "ECONOMIC_MONOPOLY";
    public static final String VICTORY_MEGASTRUCTURE_ASCENSION = "MEGASTRUCTURE_ASCENSION";
    public static final String VICTORY_DIPLOMATIC_FEDERATION = "DIPLOMATIC_FEDERATION";

    public static final String AI_BALANCED = "BALANCED";
    public static final String AI_AGGRESSIVE = "AGGRESSIVE";
    public static final String AI_ISOLATIONIST = "ISOLATIONIST";
    public static final String AI_MERCANTILE = "MERCANTILE";
    public static final String AI_SCIENTIFIC = "SCIENTIFIC";

    public CampaignSetup {
        if (campaignName == null || campaignName.isBlank()) campaignName = "Standard Galactic Campaign";
        if (starSystemCount <= 0) starSystemCount = 10;
        if (aiEmpireCount < 0) aiEmpireCount = 0;
        if (nebulaDensity < 0.0) nebulaDensity = 0.2;
        if (aiPersonalityDistribution == null) aiPersonalityDistribution = AI_BALANCED;
        if (startingTechTier <= 0) startingTechTier = 1;
        if (victoryConditionType == null) victoryConditionType = VICTORY_DOMINATION;
        if (targetVictoryThreshold <= 0) targetVictoryThreshold = 100;
    }

    public static CampaignSetup createDefault() {
        return new CampaignSetup("Sol & Alpha Centauri Expansion", 12, 0, 0.25, AI_BALANCED, 1, VICTORY_DOMINATION, 60);
    }
}
