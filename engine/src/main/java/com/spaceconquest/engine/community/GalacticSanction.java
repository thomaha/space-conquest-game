package com.spaceconquest.engine.community;

/**
 * Represents active interstellar economic sanctions or military mandates against a rogue sovereign entity.
 *
 * @param id                               unique sanction identifier
 * @param targetEmpireId                   sanctioned empire ID
 * @param sanctionType                     sanction category (TRADE_EMBARGO, ASSET_FREEZE, MILITARY_INTERVENTION)
 * @param resolutionId                     associated enabling senate resolution ID
 * @param tradeTariffPenaltyRate           punitive commercial tariff penalty (e.g. 0.50 = 50% tariff surcharge)
 * @param isAssetFreezeActive              true if liquid capital reserves are blocked/confiscated
 * @param isMilitaryInterventionAuthorized true if casus belli is universally granted to member states
 * @param turnsRemaining                   duration of sanction in turns (-1 for indefinite until repealed)
 */
public record GalacticSanction(
        String id,
        String targetEmpireId,
        String sanctionType,
        String resolutionId,
        double tradeTariffPenaltyRate,
        boolean isAssetFreezeActive,
        boolean isMilitaryInterventionAuthorized,
        int turnsRemaining
) {
    public static final String TYPE_TRADE_EMBARGO = "TRADE_EMBARGO";
    public static final String TYPE_ASSET_FREEZE = "ASSET_FREEZE";
    public static final String TYPE_MILITARY_INTERVENTION = "MILITARY_INTERVENTION";
}
