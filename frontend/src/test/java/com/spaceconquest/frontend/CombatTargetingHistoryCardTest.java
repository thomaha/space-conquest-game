package com.spaceconquest.frontend;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.combat.FleetEngagementRecord;
import com.spaceconquest.engine.ship.Fleet;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatTargetingHistoryCardTest {
    private Fleet fleet() { return new Fleet("fleet", "Fleet", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of()); }
    private FleetEngagementRecord battle(long turn, String owner, List<FleetEngagementRecord.TargetingDecision> decisions) {
        return new FleetEngagementRecord(turn, "sol", "ORBIT", "earth", "fleet", owner,
                "foe", "enemy", "owner", List.of(), 10, null, decisions);
    }
    @Test void ownedHistoryShowsSavedReasonsWhileLegacyAndForeignEntriesRemainExplicit() {
        var decisions = List.of(new FleetEngagementRecord.TargetingDecision(2, "dangerous", "Ion prioritizes dangerous: funded firepower 180; estimates"));
        var state = GameState.builder().fleetEngagements(List.of(battle(1, "owner", List.of()),
                battle(2, "foreign", decisions), battle(3, "owner", decisions))).build();
        var rows = CombatTargetingHistoryCard.rows(state, fleet(), "owner");
        assertTrue(rows.stream().anyMatch(text -> text.contains("funded firepower 180")));
        assertTrue(rows.stream().anyMatch(text -> text.contains("not recorded")));
        assertFalse(rows.stream().anyMatch(text -> text.contains("Turn 2:")));
        assertEquals(List.of("Combat targeting history unavailable."), CombatTargetingHistoryCard.rows(state, fleet(), "foreign"));
        assertThrows(UnsupportedOperationException.class, () -> rows.add("mutate"));
    }
    @Test void savedAndDisplayedDecisionsAreBoundedWithoutReconstructingOldTargets() {
        var decisions = new ArrayList<FleetEngagementRecord.TargetingDecision>();
        for (int i = 0; i < 100; i++) decisions.add(new FleetEngagementRecord.TargetingDecision(i, "target", "decision " + i));
        var entry = battle(4, "owner", decisions);
        assertEquals(64, entry.targetingDecisions().size()); assertEquals(36, entry.targetingDecisions().getFirst().round());
        decisions.clear(); assertEquals(64, entry.targetingDecisions().size());
        assertThrows(UnsupportedOperationException.class, () -> entry.targetingDecisions().clear());
        var state = GameState.builder().fleetEngagements(List.of(battle(1, "owner", List.of()), battle(2, "owner", List.of()),
                battle(3, "owner", List.of()), entry)).build();
        var rows = CombatTargetingHistoryCard.rows(state, fleet(), "owner");
        assertEquals(12, rows.stream().filter(text -> text.startsWith("Round ")).count());
        assertFalse(rows.stream().anyMatch(text -> text.startsWith("Turn 1:")));
        assertTrue(rows.stream().anyMatch(text -> text.equals("Round 99: decision 99")));
    }
}
