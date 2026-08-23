package com.spaceconquest.engine.governance;

/**
 * Encapsulates a legally or morally recognized justification for declaring war.
 * Protects democratic societies from public opinion collapse and worker strikes when entering armed conflict.
 *
 * @param id                 unique casus belli identifier
 * @param holderEmpireId     empire holding the grievance justification
 * @param targetEmpireId     aggressor or offending foreign empire
 * @param justificationType  cause (BORDER_FRICTION, TARIFF_DISPUTE, PIRACY_SPONSORSHIP, UNPROVOKED_ATTACK, IDEOLOGICAL_MISMATCH)
 * @param grievanceScore     severity score of the grievance (10.0 to 100.0)
 * @param turnsRemaining     turns before the war justification expires
 */
public record CasusBelli(
        String id,
        String holderEmpireId,
        String targetEmpireId,
        String justificationType,
        double grievanceScore,
        double turnsRemaining
) {
    public static final String BORDER_FRICTION = "BORDER_FRICTION";
    public static final String TARIFF_DISPUTE = "TARIFF_DISPUTE";
    public static final String PIRACY_SPONSORSHIP = "PIRACY_SPONSORSHIP";
    public static final String UNPROVOKED_ATTACK = "UNPROVOKED_ATTACK";
    public static final String IDEOLOGICAL_MISMATCH = "IDEOLOGICAL_MISMATCH";

    public boolean isExpired() {
        return turnsRemaining <= 0.0;
    }
}
