package com.spaceconquest.engine.governance;

/**
 * Persisted outcome of a formal declaration of war.
 */
public record WarDeclarationRecord(
        long turn,
        String initiatorEmpireId,
        String targetEmpireId,
        String casusBelliId,
        boolean justified,
        double civilianHappinessPenalty,
        double corporateTrustPenalty,
        String justificationSummary
) {}
