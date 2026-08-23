package com.spaceconquest.engine.galaxy;

/**
 * Represents a deep-space anomaly, ancient ruin or cosmic point of interest.
 *
 * @param id              unique anomaly identifier
 * @param systemId        star system location ID
 * @param type            anomaly classification (DERELICT_STARSHIP, ANCIENT_RUINS, UNSTABLE_WORMHOLE, STELLAR_PHENOMENON)
 * @param title           narrative title of the point of interest
 * @param description     detailed sensor description
 * @param scanDifficulty  difficulty rating for scanning sensors (10.0 to 100.0)
 * @param isScanned       true if survey investigation has been completed
 * @param rewardType      reward classification (RESEARCH_POINTS, CREDITS, SALVAGE_MATERIAL, TECH_UNLOCK)
 * @param rewardAmount    numeric quantity of the reward
 * @param unlockedTechId  specific technology unlocked if rewardType is TECH_UNLOCK
 */
public record Anomaly(
        String id,
        String systemId,
        String type,
        String title,
        String description,
        double scanDifficulty,
        boolean isScanned,
        String rewardType,
        double rewardAmount,
        String unlockedTechId
) {
    public static final String TYPE_DERELICT_STARSHIP = "DERELICT_STARSHIP";
    public static final String TYPE_ANCIENT_RUINS = "ANCIENT_RUINS";
    public static final String TYPE_UNSTABLE_WORMHOLE = "UNSTABLE_WORMHOLE";
    public static final String TYPE_STELLAR_PHENOMENON = "STELLAR_PHENOMENON";

    public static final String REWARD_RESEARCH_POINTS = "RESEARCH_POINTS";
    public static final String REWARD_CREDITS = "CREDITS";
    public static final String REWARD_SALVAGE_MATERIAL = "SALVAGE_MATERIAL";
    public static final String REWARD_TECH_UNLOCK = "TECH_UNLOCK";
}
