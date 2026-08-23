package com.spaceconquest.engine.governance;

/**
 * Represents a ratified bilateral treaty or diplomatic accord active between two sovereign empires.
 *
 * @param id                unique pact identifier
 * @param initiatorEmpireId empire initiating the treaty
 * @param receiverEmpireId  empire co-signing the treaty
 * @param pactType          treaty classification (MUTUAL_TRADE_AGREEMENT, RESEARCH_SHARING_ACCORD, DEFENSIVE_PACT, MILITARY_ACCESS, VASSALIZATION_TREATY)
 * @param durationTurns     remaining active duration in turns
 * @param isActive          true if treaty is ratified and currently in effect
 */
public record DiplomaticPact(
        String id,
        String initiatorEmpireId,
        String receiverEmpireId,
        String pactType,
        double durationTurns,
        boolean isActive
) {
    public static final String MUTUAL_TRADE_AGREEMENT = "MUTUAL_TRADE_AGREEMENT";
    public static final String RESEARCH_SHARING_ACCORD = "RESEARCH_SHARING_ACCORD";
    public static final String DEFENSIVE_PACT = "DEFENSIVE_PACT";
    public static final String MILITARY_ACCESS = "MILITARY_ACCESS";
    public static final String VASSALIZATION_TREATY = "VASSALIZATION_TREATY";

    public boolean involves(String empireId) {
        return (initiatorEmpireId != null && initiatorEmpireId.equals(empireId))
                || (receiverEmpireId != null && receiverEmpireId.equals(empireId));
    }
}
