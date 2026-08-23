package com.spaceconquest.engine.technology;

/**
 * Represents a bilateral research sub-route established through technology exchange accords.
 *
 * @param routeId                  unique identifier for the exchange route
 * @param senderEmpireId           empire providing research insights
 * @param receiverEmpireId         empire receiving research progress
 * @param technologyId             technology being shared
 * @param prePopulatedPercentage   percentage of progress initially pre-populated (0.30 - 0.50)
 * @param trainingSpeedBonus       speed bonus applied to resident scientist research
 */
public record TechnologyExchangeRoute(
        String routeId,
        String senderEmpireId,
        String receiverEmpireId,
        String technologyId,
        double prePopulatedPercentage,
        double trainingSpeedBonus
) {}
