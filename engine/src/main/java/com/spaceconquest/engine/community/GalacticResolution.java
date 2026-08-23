package com.spaceconquest.engine.community;

import java.util.Map;

/**
 * Represents a legislative resolution proposed or passed in the Galactic Senate.
 *
 * @param id               unique resolution identifier
 * @param title            display title
 * @param type             resolution classification (MUTUAL_DEFENSE_CHARTER, ANTI_PIRACY_CHARTER, ENVIRONMENTAL_ACCORD, PAN_GALACTIC_FREE_TRADE, SANCTION_TRADE_EMBARGO, SANCTION_ASSET_FREEZE, MILITARY_INTERVENTION)
 * @param proposerEmpireId empire that introduced the resolution
 * @param targetEmpireId   targeted empire (for targeted sanctions or interventions)
 * @param sessionTurnsLeft turns remaining before vote tally
 * @param status           current state (PROPOSED, PASSED, REJECTED, REPEALED)
 * @param votes            map of empireId to vote choice (AYE, NAY, ABSTAIN)
 */
public record GalacticResolution(
        String id,
        String title,
        String type,
        String proposerEmpireId,
        String targetEmpireId,
        int sessionTurnsLeft,
        String status,
        Map<String, String> votes
) {
    public static final String TYPE_MUTUAL_DEFENSE = "MUTUAL_DEFENSE_CHARTER";
    public static final String TYPE_ANTI_PIRACY = "ANTI_PIRACY_CHARTER";
    public static final String TYPE_ENVIRONMENTAL_ACCORD = "ENVIRONMENTAL_ACCORD";
    public static final String TYPE_FREE_TRADE = "PAN_GALACTIC_FREE_TRADE";
    public static final String TYPE_SANCTION_EMBARGO = "SANCTION_TRADE_EMBARGO";
    public static final String TYPE_SANCTION_FREEZE = "SANCTION_ASSET_FREEZE";
    public static final String TYPE_MILITARY_INTERVENTION = "MILITARY_INTERVENTION";

    public static final String STATUS_PROPOSED = "PROPOSED";
    public static final String STATUS_PASSED = "PASSED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_REPEALED = "REPEALED";

    public static final String VOTE_AYE = "AYE";
    public static final String VOTE_NAY = "NAY";
    public static final String VOTE_ABSTAIN = "ABSTAIN";

    public GalacticResolution {
        if (votes == null) votes = Map.of();
    }
}
