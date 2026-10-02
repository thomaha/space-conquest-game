package com.spaceconquest.engine.combat;

import java.util.List;

/** Compact campaign history entry for a fleet battle resolved during a daily turn. */
public record FleetEngagementRecord(
        long turn,
        String systemId,
        String siteKind,
        String siteEntityId,
        String attackerFleetId,
        String attackerEmpireId,
        String defenderFleetId,
        String defenderEmpireId,
        String winnerEmpireId,
        List<String> destroyedShipIds,
        int rounds
) {
    public FleetEngagementRecord {
        destroyedShipIds = destroyedShipIds == null ? List.of() : List.copyOf(destroyedShipIds);
    }
}
