package com.spaceconquest.frontend;

import com.spaceconquest.engine.GameState;
import java.util.*;

/** Bounded saved history, including wrecked or dismantled battery receipts. */
final class SurfaceCombatHistoryRows {
    private SurfaceCombatHistoryRows() {}
    static List<AuxiliarySupplyCard.Row> inspect(GameState state, String body, String owner) {
        var history = state.fleetEngagements().reversed().stream().filter(e -> "SURFACE_BATTERY".equals(e.siteKind())
                && body.equals(e.siteEntityId()) && (owner.equals(e.attackerEmpireId()) || owner.equals(e.defenderEmpireId()))).limit(5).toList();
        var rows = new ArrayList<AuxiliarySupplyCard.Row>();
        if (!history.isEmpty()) rows.add(row("surface-history-title", "Recent surface combat (up to five encounters)"));
        int index = 0;
        for (var event : history) {
            String key = "surface-history-" + index++; var summary = event.surfaceCombat();
            rows.add(row(key, "Combat on turn " + event.turn() + ": " + event.rounds() + " rounds; fleet " + event.attackerFleetId()
                    + "; winner " + event.winnerEmpireId() + "; " + event.destroyedShipIds().size() + " ships destroyed."));
            if (summary == null) { rows.add(row(key + "-legacy", "Older history: battery receipts and withdrawal outcome were not recorded.")); continue; }
            rows.add(row(key + "-fleet", String.format(Locale.ROOT, "Fleet %s; net hull loss %.2f, shield loss %.2f and component damage added %.2f.",
                    summary.fleetWithdrew() ? "withdrew on a funded course" : "did not withdraw", summary.fleetHullLost(), summary.fleetShieldLost(), summary.fleetComponentDamageAdded())));
            for (var battery : summary.batteries()) rows.add(row(key + "-" + battery.id(), String.format(Locale.ROOT,
                    "Battery %s (%s): %d paid shots; %.2f kWh and %s spent; hull loss %.2f, component damage added %.2f; final hull %.2f, weapon condition %.1f%%.",
                    battery.id(), battery.weaponApplicationId().replace('_', ' '), battery.paidShots(), battery.energySpentKwh(), ammunition(battery.ammunitionSpentKg()),
                    battery.hullLost(), battery.componentDamageAdded(), battery.finalHull(), 100 * battery.finalCondition())));
        }
        return List.copyOf(rows);
    }
    private static AuxiliarySupplyCard.Row row(String id, String text) { return new AuxiliarySupplyCard.Row(id, text, null, false); }
    private static String ammunition(Map<String, Double> spent) {
        return spent.isEmpty() ? "no ammunition" : new TreeMap<>(spent).entrySet().stream()
                .map(e -> String.format(Locale.ROOT, "%.2f kg %s", e.getValue(), e.getKey().replace('_', ' ')))
                .collect(java.util.stream.Collectors.joining("; "));
    }
}
