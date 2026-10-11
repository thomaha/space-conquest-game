package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.market.HubStockTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static com.spaceconquest.engine.logistics.LunarRoundTripAuditTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** Compares actual suggested settings with identical finite opening inventory and paid competing cargo. */
class SuggestedLunarSupplyAuditTest {
    @TempDir Path directory;
    private static final List<String> MATERIALS = List.of("liquid_hydrogen", "liquid_oxygen", "hydrogen_gas",
            "rp1_kerosene", "refined_uranium");
    private static final Set<String> SUPPLIERS = Set.of("depot-supply", "moon-supply", "moon-supply-2");
    private record Policy(String name, boolean reviewed, boolean pause, boolean shortage, double safetyDays, double depotSafetyDays) {
        Policy(String name, boolean reviewed, boolean pause, boolean shortage, double safetyDays) {
            this(name, reviewed, pause, shortage, safetyDays, safetyDays);
        }
    }
    private record Plan(String hub, HubFuelStockSuggestions.Fuel fuel, HubFuelStockSuggestions.Inputs inputs,
                        HubFuelStockSuggestions.Suggestion suggestion, double snapshotCycle) {
        String key() { return hub + "/" + fuel.id(); }
    }
    private final Map<String, Double> minima = new TreeMap<>();
    private final Map<String, Integer> emptyDays = new TreeMap<>();
    private final Map<String, Integer> belowRefillDays = new TreeMap<>();
    private final Map<String, Double> goodsUsed = new TreeMap<>();
    private final List<String> events = new ArrayList<>();
    private final Map<String, String> returnBlockers = new TreeMap<>();
    private final Map<String, Integer> returnWaitDays = new TreeMap<>();
    private long completed = -1;
    private int afterRestore;
    private double depotBeforeOutage;
    private final LunarSupplierDiagnostics supplierDiagnostics = new LunarSupplierDiagnostics();
    private final HydroloxCoverageDiagnostics hydroloxDiagnostics = new HydroloxCoverageDiagnostics();

    @Test void measureManualTargetsWithTheSameStockCoverageDiagnostics() throws Exception {
        audit(new Policy("manual", false, false, false, 2));
    }

    @Test void auditUntouchedSnapshotDemandWithManualMissingCycle() throws Exception {
        audit(new Policy("snapshot", false, false, false, 2));
    }
    @Test void auditReviewedDemandAgainstCompetingCargo() throws Exception {
        audit(new Policy("reviewed", true, false, false, 2));
    }
    @Test void auditUnplannedThirtyDaySupplierDispatchPause() throws Exception {
        audit(new Policy("delay", true, true, false, 2));
    }
    @Test void auditThirtyDayDelayAllowanceAgainstSupplierDispatchPause() throws Exception {
        audit(new Policy("covered-delay", true, true, false, 30));
    }
    @Test void auditSeventyDaySourceOutageAndRestoredDeliveries() throws Exception {
        audit(new Policy("shortage", true, false, true, 2));
    }
    @Test void auditSeventyDayDepotAllowanceAgainstSourceOutage() throws Exception {
        audit(new Policy("outage-covered", true, false, true, 2, 70));
    }

    private void audit(Policy policy) throws Exception {
        var original = new ThresholdLunarSupplyAuditTest();
        original.directory = directory;
        var fixture = new LunarRoundTripAuditTest();
        fixture.directory = directory;
        var run = original.opening(.8, false);
        var plans = plans(run.state, policy);
        if (!policy.name().equals("manual")) run.state = apply(run.state, plans);
        record(run, policy.name().equals("manual") ? "Manual targets retained; reference suggestions recorded only"
                : "Suggested targets applied; opening stocks unchanged");
        for (int day = 0; day < 300; day++) {
            if (policy.shortage() && day == 30) {
                depotBeforeOutage = usable(run.state, "depot-market", fuel("mod_hydrolox_rocket"));
                original.transferSource(run, true);
            }
            if (policy.shortage() && day == 100) original.transferSource(run, false);
            if (policy.pause() && (day == 10 || day == 40)) {
                boolean active = day == 40;
                run.state = run.state.withTradeRoutes(run.state.tradeRoutes().stream().map(route ->
                        SUPPLIERS.contains(route.id()) ? route.withActive(active) : route).toList());
                record(run, active ? "Supplier dispatch resumed; no route reset" : "Supplier dispatch paused; committed flights still advance");
            }
            var previous = run.state;
            original.consumeGoods(run);
            for (String id : List.of("steel", "food_matrix"))
                goodsUsed.merge(id, stock(previous, id) - stock(run.state, id), Double::sum);
            fixture.day(run, (beforeLogistics, afterLogistics) -> {
                supplierDiagnostics.observe(beforeLogistics, afterLogistics);
                hydroloxDiagnostics.observe(beforeLogistics, afterLogistics);
            });
            inspect(run, previous, plans);
            if (day % 30 == 0) run.state = fixture.reload(run.state, "suggested-" + policy.name() + "-" + day);
        }
        for (String id : MATERIALS) assertEquals(stock(run.opening, id) - stock(run.state, id),
                run.consumed.getOrDefault(id, 0.0), 1e-4, id);
        for (String id : List.of("steel", "food_matrix")) assertEquals(stock(run.opening, id) - stock(run.state, id),
                goodsUsed.getOrDefault(id, 0.0), 1e-4, id);
        assertEquals(stock(run.opening, "refined_aluminum"), stock(run.state, "refined_aluminum"), 1e-4);
        assertEquals(totalCash(run.opening), totalCash(run.state), 1e-4);
        assertTrue(run.state.tradeRoutes().stream().allMatch(TradeRoute::isActive));
        supplierDiagnostics.verify(run.state);
        hydroloxDiagnostics.verify(emptyDays.getOrDefault("depot-market/mod_hydrolox_rocket", 0));
        String report = report(run, policy, plans);
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/suggested-lunar-" + policy.name() + "-report.md"), report);
        assertTrue(completed > 0, report);
        if (policy.reviewed()) assertTrue(supplierDiagnostics.mixedFuelGoodsLoads() > 0, report);
        if (policy.shortage()) {
            if (policy.depotSafetyDays() == 2) {
                assertTrue(afterRestore > 0, report);
                assertTrue(emptyDays.getOrDefault("depot-market/mod_hydrolox_rocket", 0) > 0, report);
            }
            if (policy.depotSafetyDays() == 70) {
                assertTrue(depotBeforeOutage > 450000, "Reserve must build through paid deliveries before the outage\n" + report);
                assertEquals(0, emptyDays.getOrDefault("depot-market/mod_hydrolox_rocket", 0), report);
                assertTrue(minima.get("depot-market/mod_hydrolox_rocket") >= 120000, report);
            }
        }
    }

    private List<Plan> plans(GameState state, Policy policy) {
        var plans = new ArrayList<Plan>();
        for (String hubId : List.of("depot-market", "moon-market")) {
            var fuel = fuel("mod_hydrolox_rocket");
            var seed = HubFuelStockSuggestions.estimate(state, hub(state, hubId), fuel);
            double cycle = seed.cycleDays() > 0 ? seed.cycleDays() : hubId.equals("depot-market") ? 4 : 14;
            double demand = policy.reviewed() ? 120000.0 / 14 : seed.dailyKg();
            var inputs = new HubFuelStockSuggestions.Inputs(seed.departureKg(), demand, cycle,
                    hubId.equals("depot-market") ? policy.depotSafetyDays() : policy.safetyDays());
            plans.add(new Plan(hubId, fuel, inputs, HubFuelStockSuggestions.calculate(fuel, inputs), seed.cycleDays()));
        }
        for (String id : List.of("mod_fission_thruster", "refined_uranium")) {
            var fuel = fuel(id);
            var seed = HubFuelStockSuggestions.estimate(state, hub(state, "moon-market"), fuel);
            double cycle = seed.cycleDays() > 0 ? seed.cycleDays() : 14;
            double demand = policy.reviewed() ? 2 * seed.departureKg() / 14 : seed.dailyKg();
            var inputs = new HubFuelStockSuggestions.Inputs(seed.departureKg(), demand, cycle, policy.safetyDays());
            plans.add(new Plan("moon-market", fuel, inputs, HubFuelStockSuggestions.calculate(fuel, inputs), seed.cycleDays()));
        }
        return List.copyOf(plans);
    }

    private HubFuelStockSuggestions.Fuel fuel(String id) {
        return HubFuelStockSuggestions.fuels().stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
    }

    private GameState apply(GameState state, List<Plan> plans) {
        return state.withCommercialHubs(state.commercialHubs().stream().map(hub -> {
            var targets = new HashMap<>(hub.stockTargets());
            plans.stream().filter(plan -> plan.hub().equals(hub.id())).forEach(plan -> targets.putAll(plan.suggestion().targets()));
            assertTrue(targets.values().stream().mapToDouble(HubStockTarget::targetStockKg).sum() <= hub.storageCapacityKg());
            return hub.withStockTargets(targets);
        }).toList());
    }

    private void inspect(Run run, GameState previous, List<Plan> plans) {
        double aluminum = hub(run.state, "low-market").activeOrders().get("refined_aluminum").supplyKg();
        if (completed < 0 && aluminum >= 7500 - 1e-4) { completed = run.state.turn(); record(run, "All aluminum reached final Earth hub"); }
        if (run.state.turn() > 100 && route(run.state, "depot-supply").totalVolumeMovedKg()
                > route(previous, "depot-supply").totalVolumeMovedKg()) afterRestore++;
        for (String id : List.of("moon-supply", "moon-supply-2")) {
            var supplier = route(run.state, id);
            var fleet = fleet(run.state, supplier.assignedFreighterIds().getFirst());
            if (!TradeRoute.RETURNING.equals(supplier.phase()) || fleet.location().inTransit()) continue;
            var prepared = TradeLegReadiness.prepare(run.state, fleet, hub(run.state, "depot-market"));
            var readiness = TradeLegReadiness.inspect(prepared, fleet(prepared, fleet.ships().getFirst().id()), hub(prepared, "depot-market"));
            if (!readiness.ready()) {
                returnWaitDays.merge(id, 1, Integer::sum);
                returnBlockers.put(id, readiness.explanation());
            }
        }
        for (var plan : plans) {
            double usable = usable(run.state, plan.hub(), plan.fuel());
            minima.merge(plan.key(), usable, Math::min);
            if (usable < 1e-4) emptyDays.merge(plan.key(), 1, Integer::sum);
            if (usable + 1e-4 < plan.inputs().departureKg()) belowRefillDays.merge(plan.key(), 1, Integer::sum);
        }
        for (var hub : run.state.commercialHubs()) for (var setting : hub.stockTargets().entrySet()) {
            double stock = hub.activeOrders().get(setting.getKey()).supplyKg();
            double inbound = HubReplenishment.inboundKg(run.state, hub, setting.getKey(), null);
            assertTrue(stock + inbound <= Math.max(stock, setting.getValue().targetStockKg()) + 1e-4,
                    hub.id() + "/" + setting.getKey() + " inbound exceeds request");
        }
        if (run.state.turn() % 30 == 0) record(run, "Periodic inventory");
    }

    private double usable(GameState state, String hubId, HubFuelStockSuggestions.Fuel fuel) {
        return fuel.fractions().entrySet().stream().mapToDouble(item ->
                hub(state, hubId).activeOrders().get(item.getKey()).supplyKg() / item.getValue()).min().orElse(0);
    }

    private void record(Run run, String event) {
        events.add(String.format(Locale.ROOT, "| %d | %s | %.0f | %.0f | %.0f |", run.state.turn(), event,
                usable(run.state, "depot-market", fuel("mod_hydrolox_rocket")),
                usable(run.state, "moon-market", fuel("mod_hydrolox_rocket")),
                hub(run.state, "low-market").activeOrders().get("refined_aluminum").supplyKg()));
    }

    private String report(Run run, Policy policy, List<Plan> plans) {
        var text = new StringBuilder("# Suggested lunar stock audit\n\nPolicy: " + policy + ". All runs retain the same finite warm inventory, prices, five ships and paid local goods consumption. "
                + "Only stock settings and stated shocks differ. For the manual control the table shows unapplied reference suggestions; all other runs apply them. Missing timing is explicitly supplied as four depot / fourteen lunar cycle days. "
                + "Reviewed demand budgets one 120,000 kg hydrolox refill per fourteen days at each hub and two largest fission/uranium allowances per fourteen days at the Moon. "
                + "These are declared planning assumptions, not measured usage or solver-derived recommendations.\n\n");
        text.append("| Hub/fuel | Departure kg | Demand kg/day | Cycle days | Snapshot cycle days | Safety days | Reorder mixture kg | Target mixture kg |\n|---|---:|---:|---:|---:|---:|---:|---:|\n");
        for (var plan : plans) text.append(String.format(Locale.ROOT, "| %s | %.3f | %.3f | %.1f | %.1f | %.1f | %.3f | %.3f |%n",
                plan.key(), plan.inputs().departureKg(), plan.inputs().dailyKg(), plan.inputs().cycleDays(), plan.snapshotCycle(),
                plan.inputs().safetyDays(), plan.suggestion().reorderMixtureKg(), plan.suggestion().targetMixtureKg()));
        text.append("\nCompletion day: ").append(completed).append(". Minimum usable fuel kg: ").append(minima)
                .append(". Empty-fuel days: ").append(emptyDays).append(". Days below largest full refill allowance: ").append(belowRefillDays)
                .append(". Depot deliveries after day 100: ").append(afterRestore).append(".\n\n")
                .append("Stationary supplier return wait days after isolated paid local resupply preview: ").append(returnWaitDays)
                .append(". Observed return blockers: ").append(returnBlockers).append(".\n\n")
                .append(finalRoutes(run))
                .append(supplierDiagnostics.report())
                .append(hydroloxDiagnostics.report())
                .append("| Day | Event | Depot usable hydrolox kg | Moon usable hydrolox kg | Final Earth aluminum kg |\n|---|---|---:|---:|---:|\n")
                .append(String.join("\n", events)).append("\n\nAll ticks preserve physical fuel, goods, aluminum and money accounting, funded electricity, cargo and maneuvers. "
                        + "Periodic reload retains settings and live route commitments. Below-refill days are stock coverage diagnostics, not measured commercial waits. "
                        + "The dispatch-pause scenario temporarily disables new supplier loads on days 10–39; committed flights and deliveries still run, followed by reactivation without resets. "
                        + "The shortage scenario moves remaining source hydrolox to a tracked inaccessible reserve on day 30 and restores it on day 100. "
                        + "Initial inventory can exceed a smaller suggested target; no new stock is granted. Targets can exceed opening inventory and must then fill through paid deliveries.\n");
        return text.toString();
    }

    private String finalRoutes(Run run) {
        var text = new StringBuilder("| Final route | Phase | Origin | Destination | Fleet site | Working fuel kg | Paid cargo kg | Manifest |\n|---|---|---|---|---|---:|---:|---|\n");
        for (var route : run.state.tradeRoutes()) {
            var fleet = fleet(run.state, route.assignedFreighterIds().getFirst());
            text.append(String.format(Locale.ROOT, "| %s | %s | %s | %s | %s%s | %.3f | %.3f | %s |%n",
                    route.id(), route.phase(), route.originEntityId(), route.destinationEntityId(), fleet.location().current().entityId(),
                    fleet.location().inTransit() ? " (in transit)" : "", fleet.ships().getFirst().currentFuelKg(), route.onboardKg(), route.cargoManifest()));
        }
        return text.append("\n").toString();
    }
}
