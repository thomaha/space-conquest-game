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
        int rounds,
        SurfaceCombatSummary surfaceCombat,
        List<TargetingDecision> targetingDecisions
) {
    public record TargetingDecision(int round, String targetId, String explanation) {}
    public FleetEngagementRecord(long turn, String system, String kind, String site, String attacker, String attackerOwner,
            String defender, String defenderOwner, String winner, List<String> destroyed, int rounds, SurfaceCombatSummary surface) {
        this(turn, system, kind, site, attacker, attackerOwner, defender, defenderOwner, winner, destroyed, rounds, surface, List.of());
    }
    public FleetEngagementRecord(long turn, String system, String kind, String site, String attacker, String attackerOwner,
            String defender, String defenderOwner, String winner, List<String> destroyed, int rounds) {
        this(turn, system, kind, site, attacker, attackerOwner, defender, defenderOwner, winner, destroyed, rounds, null);
    }
    public FleetEngagementRecord {
        destroyedShipIds = destroyedShipIds == null ? List.of() : List.copyOf(destroyedShipIds);
        targetingDecisions = targetingDecisions == null ? List.of() : List.copyOf(targetingDecisions);
        if (targetingDecisions.size() > 64) targetingDecisions = List.copyOf(targetingDecisions.subList(targetingDecisions.size() - 64, targetingDecisions.size()));
    }
    public static List<TargetingDecision> targeting(TacticalCombatProcessor.CombatEngagementResult result) {
        var decisions = new java.util.ArrayDeque<TargetingDecision>();
        for (var round : result.roundReports()) for (var action : round.detailedActions()) {
            if (!"TARGET_SELECTION".equals(action.phase())) continue;
            if (decisions.size() == 64) decisions.removeFirst();
            decisions.addLast(new TargetingDecision(round.roundNumber(), action.targetEntityId(), action.description()));
        }
        return List.copyOf(decisions);
    }
}
