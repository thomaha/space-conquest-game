package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.PropulsionCatalog;
import java.util.*;
import static com.spaceconquest.engine.logistics.LunarRoundTripAuditTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** Separates ingredient imbalance, committed cargo and physically unavailable upstream stock. */
final class HydroloxCoverageDiagnostics {
    private static final Map<String, Double> FRACTIONS = PropulsionCatalog.drive("mod_hydrolox_rocket").propellantMaterials(1);
    private final Map<String, Integer> emptyByLimitingIngredient = new TreeMap<>();
    private final List<String> snapshots = new ArrayList<>();
    private final List<String> suppliers = new ArrayList<>();
    private boolean wasEmpty;
    private int emptyWithSourceUnavailable;
    private int emptyWithSourceAvailable;
    private int emptyWithInboundCoverage;
    private double maximumUnpairedKg;
    private long firstEmpty = -1;
    private long firstRecovery = -1;

    void observe(GameState before, GameState after) {
        var depot = hub(after, "depot-market");
        double usable = usable(after, "depot-market", false);
        double position = usable(after, "depot-market", true);
        double physical = FRACTIONS.keySet().stream().mapToDouble(id -> depot.activeOrders().get(id).supplyKg()).sum();
        assertTrue(usable <= physical + 1e-4);
        assertTrue(position + 1e-4 >= usable);
        maximumUnpairedKg = Math.max(maximumUnpairedKg, physical - usable);
        boolean empty = usable < 1e-4;
        if (empty) {
            if (firstEmpty < 0) firstEmpty = after.turn();
            if (usable(after, "low-market", false) < 1e-4) emptyWithSourceUnavailable++;
            else emptyWithSourceAvailable++;
            if (position >= 1e-4) emptyWithInboundCoverage++;
            String limiting = String.join(" + ", FRACTIONS.keySet().stream().sorted().filter(id ->
                    depot.activeOrders().get(id).supplyKg() / FRACTIONS.get(id) < 1e-4).toList());
            emptyByLimitingIngredient.merge(limiting, 1, Integer::sum);
        } else if (wasEmpty && firstRecovery < 0) firstRecovery = after.turn();
        boolean delivered = after.tradeRoutes().stream().anyMatch(next ->
                next.totalVolumeMovedKg() > route(before, next.id()).totalVolumeMovedKg() + 1e-6
                        && route(before, next.id()).destinationEntityId().equals("depot-market")
                        && route(before, next.id()).cargoManifest().keySet().stream().anyMatch(FRACTIONS::containsKey));
        if (empty != wasEmpty || after.turn() % 30 == 0 || after.turn() == 31 || after.turn() == 101
                || delivered && after.turn() <= 110) snapshot(after, empty != wasEmpty ? empty ? "Became empty" : "Recovered"
                : delivered ? "Depot ingredient delivery" : "Inventory checkpoint", empty != wasEmpty || after.turn() == 101);
        wasEmpty = empty;
    }

    private double usable(GameState state, String hubId, boolean includeInbound) {
        var market = hub(state, hubId);
        return FRACTIONS.entrySet().stream().mapToDouble(item -> (market.activeOrders().get(item.getKey()).supplyKg()
                + (includeInbound ? HubReplenishment.inboundKg(state, market, item.getKey(), null) : 0)) / item.getValue()).min().orElse(0);
    }

    private void snapshot(GameState state, String event, boolean includeSuppliers) {
        var depot = hub(state, "depot-market");
        var source = hub(state, "low-market");
        snapshots.add(String.format(Locale.ROOT,
                "| %d | %s | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f |",
                state.turn(), event, depot.activeOrders().get("liquid_hydrogen").supplyKg(),
                depot.activeOrders().get("liquid_oxygen").supplyKg(), usable(state, "depot-market", false),
                HubReplenishment.inboundKg(state, depot, "liquid_hydrogen", null),
                HubReplenishment.inboundKg(state, depot, "liquid_oxygen", null), usable(state, "depot-market", true),
                HubReplenishment.requestedKg(state, depot, "liquid_hydrogen", null),
                HubReplenishment.requestedKg(state, depot, "liquid_oxygen", null),
                source.activeOrders().get("liquid_hydrogen").supplyKg(), source.activeOrders().get("liquid_oxygen").supplyKg()));
        if (includeSuppliers) for (String id : List.of("depot-supply", "handling")) {
            var route = route(state, id);
            var carrier = fleet(state, route.assignedFreighterIds().getFirst());
            suppliers.add(String.format(Locale.ROOT, "| %d | %s | %s | %s | %s | %s%s | %.3f | %s |",
                    state.turn(), id, route.phase(), route.originEntityId(), route.destinationEntityId(),
                    carrier.location().current().entityId(), carrier.location().inTransit() ? " (in transit)" : "",
                    carrier.ships().getFirst().currentFuelKg(), route.cargoManifest()));
        }
    }

    void verify(int expectedEmptyDays) {
        assertEquals(expectedEmptyDays, emptyWithSourceUnavailable + emptyWithSourceAvailable);
        assertEquals(expectedEmptyDays, emptyByLimitingIngredient.values().stream().mapToInt(Integer::intValue).sum());
    }

    String report() {
        return "## Depot hydrolox ingredient coverage\n\nFirst empty observation: " + firstEmpty
                + ". First recovery: " + firstRecovery + ". Empty days by missing ingredient combination: " + emptyByLimitingIngredient
                + ". Empty days with upstream mixture unavailable: " + emptyWithSourceUnavailable
                + ". Empty days with upstream mixture available: " + emptyWithSourceAvailable
                + ". Empty days with positive usable committed inbound: " + emptyWithInboundCoverage
                + String.format(Locale.ROOT, ". Maximum physical ingredient mass beyond usable hydrolox mixture: %.3f kg.\n\n", maximumUnpairedKg)
                + "Usable mixture is the minimum ingredient stock divided by its catalog fraction. Inventory position also includes actual paid inbound cargo. Positive upstream mixture alone does not prove an affordable safe dispatch.\n\n"
                + "| Day | Event | Depot LH kg | Depot LO kg | Usable kg | LH inbound kg | LO inbound kg | Position usable kg | LH request kg | LO request kg | Source LH kg | Source LO kg |\n"
                + "|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n" + String.join("\n", snapshots)
                + "\n\n| Day | Supplier | Phase | Origin | Destination | Site | Working fuel kg | Paid manifest |\n"
                + "|---:|---|---|---|---|---|---:|---|\n" + String.join("\n", suppliers) + "\n\n";
    }
}
