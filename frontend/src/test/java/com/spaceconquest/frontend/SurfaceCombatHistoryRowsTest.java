package com.spaceconquest.frontend;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.combat.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SurfaceCombatHistoryRowsTest {
    private FleetEngagementRecord event(long turn, String body, String owner, SurfaceCombatSummary summary) {
        return new FleetEngagementRecord(turn, "sol", "SURFACE_BATTERY", body, "fleet", "enemy", "surface-defenses", owner, "DRAW", List.of(), 2, summary);
    }
    @Test void receiptsExplainCostsDamageAndWithdrawalEvenAfterTheBatteryIsDismantled() {
        var battery = new SurfaceCombatSummary.Battery("old-battery", "mass_drivers", 2, Map.of("mass_driver_slug", 40.0), 26, 0, 100, 1000, 0);
        var summary = new SurfaceCombatSummary(List.of(battery), 77, 100, 0, true);
        var state = GameState.builder().fleetEngagements(List.of(event(1, "earth", "own", summary))).build();
        var rows = AuxiliarySupplyCard.inspectSurface(state, "earth", "own");
        assertTrue(rows.stream().anyMatch(r -> r.text().contains("withdrew on a funded course")));
        assertTrue(rows.stream().anyMatch(r -> r.text().contains("old-battery") && r.text().contains("2 paid shots")
                && r.text().contains("40.00 kg mass driver slug") && r.text().contains("26.00 kWh")));
        assertTrue(rows.stream().anyMatch(r -> r.text().contains("component damage added 100.00")));
        assertTrue(rows.stream().filter(r -> r.id().startsWith("surface-history")).allMatch(r -> r.command() == null));
        assertTrue(state.auxiliaryForces().batteries().isEmpty());
    }
    @Test void historyIsBoundedNewestFirstAndIsolatesBodyAndParticipants() {
        var events = new ArrayList<FleetEngagementRecord>();
        for (int i = 0; i < 8; i++) events.add(event(i, "earth", "own", null));
        events.add(event(9, "moon", "own", null)); events.add(event(10, "earth", "foreign", null));
        var state = GameState.builder().fleetEngagements(events).build(); var rows = SurfaceCombatHistoryRows.inspect(state, "earth", "own");
        assertEquals(5, rows.stream().filter(r -> r.text().startsWith("Combat on turn")).count());
        assertTrue(rows.get(1).text().startsWith("Combat on turn 7"));
        assertTrue(rows.stream().noneMatch(r -> r.text().startsWith("Combat on turn 9") || r.text().startsWith("Combat on turn 10")));
        assertTrue(rows.stream().anyMatch(r -> r.text().contains("Older history")));
        assertThrows(UnsupportedOperationException.class, () -> rows.clear());
    }
}
