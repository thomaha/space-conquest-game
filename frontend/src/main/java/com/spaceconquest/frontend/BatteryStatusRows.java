package com.spaceconquest.frontend;

import com.spaceconquest.engine.combat.BatteryStatus;
import java.util.*;

/** Formats immutable engine diagnostics inside the existing background auxiliary inspection. */
final class BatteryStatusRows {
    private BatteryStatusRows() {}
    static List<AuxiliarySupplyCard.Row> rows(BatteryStatus status) {
        var firing = status.firing(); var repair = status.repair(); var rows = new ArrayList<AuxiliarySupplyCard.Row>();
        rows.add(row(status.id() + "-readiness", String.format(Locale.ROOT,
                "Firing: %s; %d / %d shots funded. Range %.0f km; upkeep %.2f kWh and full salvo %.2f kWh.",
                firing.status(), firing.fundedShots(), firing.fullSalvoShots(), firing.rangeMeters() / 1000, firing.upkeepKwh(), firing.salvoKwh())));
        rows.add(row(status.id() + "-targeting", "Supply preview at zero separation. Combat also requires live sensor contact and a target within range."));
        rows.add(row(status.id() + "-repair", String.format(Locale.ROOT,
                "Repair: %s%s. Preview restores %.2f electronics points and %.2f hull; shared yard %.2f hours/day.",
                repair.status(), repair.firstJob().isBlank() ? "" : " (first job: " + repair.firstJob().toLowerCase(Locale.ROOT) + ")",
                repair.projectedMountPoints(), repair.projectedHullPoints(), repair.sharedYardHours())));
        if (!repair.shortages().isEmpty()) rows.add(row(status.id() + "-shortages", "First-job material shortages: "
                + new TreeMap<>(repair.shortages()).entrySet().stream().map(e -> String.format(Locale.ROOT, "%s %.2f kg", e.getKey().replace('_', ' '), e.getValue()))
                        .collect(java.util.stream.Collectors.joining("; "))));
        rows.add(row(status.id() + "-repair-note", repair.note()));
        return List.copyOf(rows);
    }
    private static AuxiliarySupplyCard.Row row(String id, String text) { return new AuxiliarySupplyCard.Row(id, text, null, false); }
}
