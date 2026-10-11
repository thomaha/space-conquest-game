package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import com.spaceconquest.engine.market.HubStockTarget;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static com.spaceconquest.engine.logistics.LunarRoundTripAuditTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual paid traffic establishes history before any history-based targets are applied. */
class MeasuredLunarSupplyAuditTest {
    @TempDir Path directory;
    private static final List<String> FUELS = List.of("liquid_hydrogen", "liquid_oxygen", "hydrogen_gas", "rp1_kerosene", "refined_uranium");
    private static final List<String> GOODS = List.of("steel", "food_matrix");
    private static final List<String> HUBS = List.of("depot-market", "moon-market");
    private static final Set<String> SUPPLIERS = Set.of("depot-supply", "moon-supply", "moon-supply-2");
    private record Policy(String name, boolean growth, int pauseDays, double safetyDays) {}
    private record Plan(long day, String hubId, HubFuelStockSuggestions.Fuel fuel,
                        HubFuelStockSuggestions.Estimate estimate, HubFuelStockSuggestions.Inputs inputs,
                        HubFuelStockSuggestions.Suggestion suggestion) {}
    private record Flow(double withdrawn, double delivered) {}
    private record Result(Policy policy, List<Plan> plans, Map<String, Integer> emptyDays,
                          Map<String, Integer> observedStockouts, Map<String, Double> minima,
                          Map<String, Double> peakStorage, Map<String, Double> peakPosition,
                          Map<String, Double> capacities, Map<String, Long> recovery,
                          double goodsUsed, double unmetGoods, int mixedLoads, int partialLoads,
                          int customerDeliveries, int waitingCustomerReturns, long firstDeliveryAfterResume, double fuelConsumed, String detail) {}

    @Test void compareMeasuredBuffersWithTrafficGrowthAndSupplierInterruptions() throws Exception {
        var results = new ArrayList<Result>();
        for (var policy : List.of(new Policy("steady", false, 0, 2), new Policy("growth", true, 0, 2),
                new Policy("pause", false, 30, 2), new Policy("covered-pause", false, 30, 30),
                new Policy("long-pause", false, 70, 2))) results.add(audit(policy));
        String report = report(results);
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/measured-lunar-supply-report.md"), report);
        assertTrue(results.get(1).goodsUsed() > results.getFirst().goodsUsed(), report);
        assertTrue(results.get(1).plans().stream().anyMatch(plan -> plan.day() == 120), report);
        for (var result : results) {
            assertTrue(result.mixedLoads() > 0, report);
            assertTrue(result.partialLoads() > 0, report);
            assertTrue(result.customerDeliveries() > 0, report);
            assertEquals(0, result.waitingCustomerReturns(), "Protected return fuel must clear customer return waits: " + result.policy().name());
            if (result.policy().pauseDays() > 0) assertTrue(result.firstDeliveryAfterResume() > 0, report);
        }
    }

    private Result audit(Policy policy) throws Exception {
        var original = new ThresholdLunarSupplyAuditTest();
        original.directory = directory;
        var fixture = new LunarRoundTripAuditTest();
        fixture.directory = directory;
        var seeded = original.opening(.8, false);
        // Same finite goods inventory in every run, enough to expose increased demand without production.
        var initial = seeded.state.withCommercialHubs(seeded.state.commercialHubs().stream().map(hub -> {
            var orders = new HashMap<>(hub.activeOrders());
            if (hub.id().equals("depot-market")) for (String id : GOODS) {
                var old = orders.get(id);
                orders.put(id, new MarketOrder(id, 18000, old.demandKg(), old.pricePerKg(), old.shortcomingScore()));
            }
            if (hub.id().equals("low-market")) PropulsionCatalog.drive("mod_hydrolox_rocket").propellantMaterials(3000000).forEach((id, kg) -> {
                var old = orders.get(id);
                orders.put(id, new MarketOrder(id, old.supplyKg() + kg, old.demandKg(), old.pricePerKg(), old.shortcomingScore()));
            });
            return new CommercialHub(hub.id(), hub.entityId(), hub.transactionTariffRate(), hub.storageCapacityKg(),
                    orders.values().stream().mapToDouble(MarketOrder::supplyKg).sum(), hub.logisticsRangeUnits(), orders, hub.stockTargets());
        }).toList());
        var run = new Run(customer(initial));
        var ledger = new Ledger(policy);
        var diagnostics = new LunarSupplierDiagnostics();
        var plans = new ArrayList<Plan>();
        var consumedGoods = new HashMap<String, Double>();
        for (int day = 1; day <= 240; day++) {
            if (day == 61 || policy.growth() && day == 121) {
                var reviewed = plans(run.state, policy.safetyDays());
                plans.addAll(reviewed);
                run.state = apply(run.state, reviewed);
                reviewed.forEach(plan -> ledger.allowances.put(plan.hubId() + "/" + plan.fuel().id(), plan.estimate().departureKg()));
            }
            if (policy.pauseDays() > 0 && (day == 91 || day == 91 + policy.pauseDays())) {
                boolean active = day > 91;
                run.state = run.state.withTradeRoutes(run.state.tradeRoutes().stream()
                        .map(route -> SUPPLIERS.contains(route.id()) ? route.withActive(active) : route).toList());
            }
            advance(run, policy.growth() && day > 90 ? 75 : 25, consumedGoods, ledger, diagnostics);
            if (day % 30 == 0) run.state = fixture.reload(run.state, "measured-" + policy.name() + "-" + day);
        }
        for (String id : FUELS) assertEquals(stock(run.opening, id) - stock(run.state, id), run.consumed.getOrDefault(id, 0.0), 1e-4, id);
        for (String id : GOODS) assertEquals(stock(run.opening, id) - stock(run.state, id), consumedGoods.getOrDefault(id, 0.0), 1e-4, id);
        assertEquals(stock(run.opening, "refined_aluminum"), stock(run.state, "refined_aluminum"), 1e-4);
        assertEquals(totalCash(run.opening), totalCash(run.state), 1e-4);
        assertTrue(run.state.tradeRoutes().stream().allMatch(TradeRoute::isActive));
        diagnostics.verify(run.state);
        return ledger.result(plans, diagnostics, run);
    }

    private GameState customer(GameState state) {
        var original = ship(state, "trader");
        var ship = new ShipInstance("customer", original.designId(), original.ownerEntityId(), 100, 0, 0, Map.of())
                .withPowerState(ShipPowerState.empty());
        var fleets = new ArrayList<>(state.fleets());
        fleets.add(new Fleet("customer", "Customer freighter", original.ownerEntityId(), "sol", "", 0, 0, 0, false,
                "PASSIVE", List.of(ship), FleetLocation.at(FleetLocation.Site.docked("depot-port")))
                .withFuelPolicy(new FleetFuelPolicy(.05, .05, .05)));
        var routes = new ArrayList<>(state.tradeRoutes());
        routes.add(new TradeRoute("customer", "Continuing lunar goods", original.ownerEntityId(), "depot-market", "moon-market",
                "steel", 2500, 0, 5e6, List.of("customer"), 0, true));
        double cash = totalCash(state);
        state = ShipBatteryCharging.charge(state.withFleets(fleets).withTradeRoutes(routes), "customer", "depot-port", 500 / .9);
        assertEquals(500, ship(state, "customer").powerState().batteryChargeKwh(), 1e-5);
        assertEquals(cash, totalCash(state), 1e-4);
        return state;
    }

    private void advance(Run run, double dailyGoods, Map<String, Double> consumedGoods,
                         Ledger ledger, LunarSupplierDiagnostics diagnostics) {
        run.state = observe(run.state.withTurn(run.state.turn() + 1));
        var opening = run.state;
        var purchase = ConstructionMaterials.buyUpTo(run.state, "moon-port", "provider",
                Map.of("steel", dailyGoods, "food_matrix", dailyGoods));
        run.state = purchase.state();
        purchase.acquiredKg().forEach((id, kg) -> consumedGoods.merge(id, kg, Double::sum));
        ledger.goodsUsed += purchase.totalKg();
        ledger.unmetGoods += 2 * dailyGoods - purchase.totalKg();
        var beforePower = run.state;
        var powered = ShipPowerProcessor.advanceDay(beforePower);
        run.state = beforePower.withFleets(new FleetProcessor().processFleetMovements(powered, beforePower.orbitalStations(), List.of()));
        for (String id : FUELS) {
            double burned = stock(beforePower, id) - stock(run.state, id);
            assertTrue(burned >= -1e-5, id);
            run.consumed.merge(id, burned, Double::sum);
        }
        for (var fleet : run.state.fleets()) for (var ship : fleet.ships()) {
            assertEquals(0, ship.powerState().lastUnmetEssentialKwh(), 1e-5, ship.id());
            assertEquals(0, ship.powerState().unmetCargoKwh(), 1e-5, ship.id());
            assertTrue(ship.powerState().cargoPreservation().lostKgToday().values().stream().allMatch(kg -> kg == 0));
            assertFalse(fleet.location().orbitalFlight() != null && fleet.location().orbitalFlight().failed(), fleet.id());
        }
        var beforeTrade = run.state;
        run.state = observe(new LogisticsProcessor().processTradeRoutes(run.state).state());
        diagnostics.observe(beforeTrade, run.state);
        ledger.observe(opening, beforeTrade, run.state);
        assertEquals(totalCash(run.opening), totalCash(run.state), 1e-4);
    }

    private GameState observe(GameState state) {
        return state.withCommercialHubs(state.commercialHubs().stream().map(hub -> hub.observeDay(state.turn())).toList());
    }

    private List<Plan> plans(GameState state, double safety) {
        var result = new ArrayList<Plan>();
        for (String hubId : HUBS) for (String fuelId : hubId.equals("depot-market")
                ? List.of("mod_hydrolox_rocket") : List.of("mod_hydrolox_rocket", "mod_fission_thruster", "refined_uranium")) {
            var fuel = fuel(fuelId);
            var hub = hub(state, hubId);
            var seed = HubFuelStockSuggestions.estimate(state, hub, fuel);
            double measured = 0;
            for (var ingredient : fuel.fractions().entrySet()) {
                var average = hub.stockHistory().average(ingredient.getKey(), state.turn());
                assertEquals(30, average.observedDays(), hubId + "/" + ingredient.getKey());
                measured = Math.max(measured, average.dailyWithdrawalKg() / ingredient.getValue());
            }
            assertTrue(seed.dailyKg() + 1e-6 >= measured);
            // Missing timing is reviewed explicitly. Measured demand is never replaced by a declared rate.
            double cycle = seed.cycleDays() > 0 ? seed.cycleDays() : hubId.equals("depot-market") ? 4 : 14;
            var inputs = seed.departureKg() + seed.dailyKg() > 0
                    ? new HubFuelStockSuggestions.Inputs(seed.departureKg(), seed.dailyKg(), cycle, safety) : null;
            result.add(new Plan(state.turn(), hubId, fuel, seed, inputs,
                    inputs == null ? null : HubFuelStockSuggestions.calculate(fuel, inputs)));
        }
        return List.copyOf(result);
    }

    private GameState apply(GameState state, List<Plan> plans) {
        return state.withCommercialHubs(state.commercialHubs().stream().map(hub -> {
            var targets = new HashMap<>(hub.stockTargets());
            plans.stream().filter(plan -> plan.hubId().equals(hub.id()) && plan.suggestion() != null)
                    .forEach(plan -> targets.putAll(plan.suggestion().targets()));
            assertTrue(targets.values().stream().mapToDouble(HubStockTarget::targetStockKg).sum() <= hub.storageCapacityKg());
            return hub.withStockTargets(targets);
        }).toList());
    }

    private static HubFuelStockSuggestions.Fuel fuel(String id) {
        return HubFuelStockSuggestions.fuels().stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
    }

    private static double usable(GameState state, String hubId, HubFuelStockSuggestions.Fuel fuel) {
        return fuel.fractions().entrySet().stream().mapToDouble(item ->
                hub(state, hubId).activeOrders().get(item.getKey()).supplyKg() / item.getValue()).min().orElse(0);
    }

    private static final class Ledger {
        private final Policy policy;
        private final Map<Long, Map<String, Flow>> flows = new TreeMap<>();
        private final Map<String, Integer> emptyDays = new TreeMap<>(), observedStockouts = new TreeMap<>();
        private final Map<String, Double> minima = new TreeMap<>(), peakStorage = new TreeMap<>();
        private final Map<String, Double> peakPosition = new TreeMap<>(), capacities = new TreeMap<>();
        private final Map<String, Long> recovery = new TreeMap<>();
        private final Map<String, Double> allowances = new TreeMap<>();
        private final Map<String, Integer> belowRefillDays = new TreeMap<>();
        private final Map<String, Long> refillRecovery = new TreeMap<>();
        private final Set<String> depleted = new HashSet<>();
        private final List<String> samples = new ArrayList<>();
        private double goodsUsed, unmetGoods;
        private int partialLoads, customerDeliveries, waitingCustomerReturns;
        private long firstDeliveryAfterResume = -1;
        private Ledger(Policy policy) { this.policy = policy; }

        private void observe(GameState opening, GameState beforeTrade, GameState after) {
            var delivered = deliveries(beforeTrade, after);
            var daily = new TreeMap<String, Flow>();
            for (String hubId : HUBS) {
                var hub = hub(after, hubId);
                for (String material : FUELS) {
                    String key = hubId + "/" + material;
                    double amount = delivered.getOrDefault(key, 0.0);
                    double withdrawal = hub(opening, hubId).activeOrders().get(material).supplyKg() + amount - hub.activeOrders().get(material).supplyKg();
                    assertTrue(withdrawal >= -1e-4, key);
                    var flow = new Flow(Math.max(0, withdrawal), amount);
                    daily.put(key, flow);
                    var observed = hub.stockHistory().days().getLast().materials().get(material);
                    assertEquals(flow.withdrawn(), observed.withdrawnKg(), 1e-4, key + " gross withdrawals");
                    assertEquals(flow.delivered(), observed.deliveredKg(), 1e-4, key + " gross deliveries");
                    if (after.turn() > 60 && observed.stockout() && !material.equals("rp1_kerosene")) observedStockouts.merge(key, 1, Integer::sum);
                    var average = hub.stockHistory().average(material, after.turn());
                    double expected = flows.entrySet().stream().filter(entry -> entry.getKey() >= after.turn() - 30)
                            .mapToDouble(entry -> entry.getValue().get(key).withdrawn()).sum();
                    assertEquals(Math.min(30, after.turn() - 1), average.observedDays(), key);
                    assertEquals(expected, average.withdrawnKg(), 1e-4, key + " rolling withdrawals");
                    double expectedDelivered = flows.entrySet().stream().filter(entry -> entry.getKey() >= after.turn() - 30)
                            .mapToDouble(entry -> entry.getValue().get(key).delivered()).sum();
                    assertEquals(expectedDelivered, average.deliveredKg(), 1e-4, key + " rolling deliveries");
                    assertTrue(hub.stockHistory().days().size() <= 31);
                }
                double inbound = hub.activeOrders().keySet().stream().mapToDouble(id -> HubReplenishment.inboundKg(after, hub, id, null)).sum();
                assertTrue(hub.currentStoredWeightKg() + inbound <= hub.storageCapacityKg() + 1e-4, hubId);
                peakStorage.merge(hubId, hub.currentStoredWeightKg(), Math::max);
                peakPosition.merge(hubId, hub.currentStoredWeightKg() + inbound, Math::max);
                capacities.put(hubId, hub.storageCapacityKg());
                if (after.turn() > 60) coverage(after, hubId);
            }
            flows.put(after.turn(), daily);
            if (after.turn() > 60 && TradeRoute.RETURNING.equals(route(after, "customer").phase())
                    && !fleet(after, "customer").location().inTransit()) waitingCustomerReturns++;
            flows.keySet().removeIf(day -> day < after.turn() - 30);
            if (after.turn() % 30 == 0) samples.add(String.format(Locale.ROOT, "| %d | %.0f | %.0f | %.0f | %.0f | %.0f |",
                    after.turn(), usable(after, "depot-market", fuel("mod_hydrolox_rocket")),
                    usable(after, "moon-market", fuel("mod_hydrolox_rocket")), hub(after, "moon-market").activeOrders().get("hydrogen_gas").supplyKg(), goodsUsed, unmetGoods));
        }

        private Map<String, Double> deliveries(GameState before, GameState after) {
            var delivered = new HashMap<String, Double>();
            for (var old : before.tradeRoutes()) {
                var next = route(after, old.id());
                if (next.totalVolumeMovedKg() > old.totalVolumeMovedKg() + 1e-6) {
                    if (after.turn() > 60 && old.id().equals("customer")) customerDeliveries++;
                    for (var lot : old.cargoManifest().entrySet()) {
                        double physical = ship(before, old.assignedFreighterIds().getFirst()).storedCargoKg().getOrDefault(lot.getKey(), 0.0);
                        double remaining = next.cargoManifest().containsKey(lot.getKey()) ? next.cargoManifest().get(lot.getKey()).massKg() : 0;
                        delivered.merge(old.destinationEntityId() + "/" + lot.getKey(), Math.max(0, Math.min(physical, lot.getValue().massKg()) - remaining), Double::sum);
                    }
                    if (SUPPLIERS.contains(old.id()) && policy.pauseDays() > 0 && after.turn() >= 91 + policy.pauseDays() && firstDeliveryAfterResume < 0)
                        firstDeliveryAfterResume = after.turn();
                }
                if (old.onboardKg() < 1e-6 && next.onboardKg() > 1e-6) {
                    var design = after.shipDesigns().stream().filter(item -> item.id().equals(ship(after, next.assignedFreighterIds().getFirst()).designId())).findFirst().orElseThrow();
                    if (next.onboardKg() + 1e-6 < design.maxCargoMassKg()) partialLoads++;
                }
            }
            return delivered;
        }

        private void coverage(GameState state, String hubId) {
            for (String id : hubId.equals("depot-market") ? List.of("mod_hydrolox_rocket")
                    : List.of("mod_hydrolox_rocket", "mod_fission_thruster", "refined_uranium")) {
                String key = hubId + "/" + id;
                double available = usable(state, hubId, fuel(id));
                minima.merge(key, available, Math::min);
                if (available < 1e-4) { emptyDays.merge(key, 1, Integer::sum); depleted.add(key); }
                else if (policy.pauseDays() > 0 && depleted.contains(key) && state.turn() >= 91 + policy.pauseDays()) recovery.putIfAbsent(key, state.turn());
                double allowance = allowances.getOrDefault(key, 0.0);
                if (available + 1e-4 < allowance) belowRefillDays.merge(key, 1, Integer::sum);
                else if (policy.pauseDays() > 0 && belowRefillDays.containsKey(key) && state.turn() >= 91 + policy.pauseDays())
                    refillRecovery.putIfAbsent(key, state.turn());
            }
        }

        private Result result(List<Plan> plans, LunarSupplierDiagnostics diagnostics, Run run) {
            String detail = "Days below reviewed departure allowance: " + belowRefillDays + ". First allowance recovery day after resume: " + refillRecovery + ".\n\n"
                    + terminal(run.state) + "\n| Day | Depot hydrolox kg | Moon hydrolox kg | Moon hydrogen kg | Goods consumed kg | Unserved goods kg |\n|---:|---:|---:|---:|---:|---:|\n"
                    + String.join("\n", samples) + "\n\n" + diagnostics.report();
            return new Result(policy, List.copyOf(plans), Map.copyOf(emptyDays), Map.copyOf(observedStockouts), Map.copyOf(minima),
                    Map.copyOf(peakStorage), Map.copyOf(peakPosition), Map.copyOf(capacities), Map.copyOf(recovery), goodsUsed, unmetGoods,
                    diagnostics.mixedFuelGoodsLoads(), partialLoads, customerDeliveries, waitingCustomerReturns,
                    firstDeliveryAfterResume, run.consumed.values().stream().mapToDouble(Double::doubleValue).sum(), detail);
        }

        private String terminal(GameState state) {
            var text = new StringBuilder("Terminal physical route state:\n\n| Route | Phase | Main fuel kg | Remaining manifest | Next-leg readiness |\n|---|---|---:|---|---|\n");
            for (var route : state.tradeRoutes()) {
                var carrier = fleet(state, route.assignedFreighterIds().getFirst());
                var destination = hub(state, TradeRoute.RETURNING.equals(route.phase()) ? route.originEntityId() : route.destinationEntityId());
                var prepared = TradeLegReadiness.prepare(state, carrier, destination);
                var readiness = TradeLegReadiness.inspect(prepared, fleet(prepared, carrier.id()), destination);
                text.append(String.format(Locale.ROOT, "| %s | %s | %.6f | %s | %s |%n", route.id(), route.phase(),
                        carrier.ships().getFirst().currentFuelKg(), route.cargoManifest(), readiness.explanation()));
            }
            text.append("\nTerminal Moon market stock kg: ");
            for (String id : FUELS) text.append(id).append("=").append(hub(state, "moon-market").activeOrders().get(id).supplyKg()).append("; ");
            text.append("\nTerminal private fuel kg by hub/ship: ");
            for (var port : state.commercialHubs()) port.reservedTradeFuel().forEach((ship, fuel) ->
                    text.append(port.id()).append('/').append(ship).append('=').append(fuel.massKg()).append("; "));
            text.append("\n\nReadiness is an isolated paid preview, not a committed order or proof that remaining freight can be sold.\n");
            return text.toString();
        }
    }

    private String report(List<Result> results) {
        var text = new StringBuilder("# Measured lunar supply audit\n\nFive 240-day runs use identical finite fuel inventory, 18,000 kg of each depot good, six ships and paid local purchases. "
                + "A sixth hydrolox customer freighter delivers ordinary goods and returns independently of the three suppliers and the finite aluminum backhaul. Its initial battery charge is paid and low-Earth hydrolox includes an additional finite 3,000,000 kg mixture in every scenario. "
                + "Days 1–60 establish history with manual targets and 25 kg/day requested per good. Measured suggestions are reviewed after day 60; growth requests 75 kg/day per good from day 91 and reviews new suggestions after day 120. "
                + "Supplier dispatch pauses start on day 91 and last 30 or 70 days. Paid flights continue; routes resume without resets. Targets use actual rolling withdrawals, not a declared demand rate. "
                + "Missing supplier timing uses explicitly reviewed four depot / fourteen lunar cycle days. Automatic freight secures a funded exit before dispatch. Private prepaid destination fuel remains included in physical stock and storage accounting, outside public market orders. "
                + "No production or cash injection occurs. Initial inventory, hull capital and upstream production costs remain outside this operational audit.\n\n");
        text.append("| Scenario | Safety days | Pause days | Goods consumed kg | Unserved goods kg | Mixed loads | Partial loads | Customer deliveries after review | Customer return wait days | Consumed fuel kg | First delivery after resume |\n|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (var result : results) text.append(String.format(Locale.ROOT, "| %s | %.0f | %d | %.0f | %.0f | %d | %d | %d | %d | %.0f | %s |%n",
                result.policy().name(), result.policy().safetyDays(), result.policy().pauseDays(), result.goodsUsed(), result.unmetGoods(),
                result.mixedLoads(), result.partialLoads(), result.customerDeliveries(), result.waitingCustomerReturns(), result.fuelConsumed(),
                result.firstDeliveryAfterResume() < 0 ? "n/a" : result.firstDeliveryAfterResume() + " (" + (result.firstDeliveryAfterResume() - (91 + result.policy().pauseDays())) + " days)"));
        for (var result : results) {
            text.append("\n## ").append(result.policy().name()).append("\n\n");
            text.append("End-of-day empty fuel days after review: ").append(result.emptyDays()).append(". Observed ingredient stockout days: ").append(result.observedStockouts())
                    .append(". Minimum usable fuel kg: ").append(result.minima()).append(". First nonempty recovery day after resume: ").append(result.recovery())
                    .append(". Peak physical storage kg: ").append(result.peakStorage()).append(". Peak physical plus paid inbound kg: ")
                    .append(result.peakPosition()).append(". Storage capacities kg: ").append(result.capacities()).append(".\n\n");
            text.append("| Review day | Hub/fuel | Departure kg | Measured demand kg/day | Preview cycle days | Used cycle days | Reorder kg | Target kg |\n|---:|---|---:|---:|---:|---:|---:|---:|\n");
            for (var plan : result.plans()) {
                if (plan.inputs() == null) text.append(String.format(Locale.ROOT, "| %d | %s/%s | 0 | 0 | %.1f | n/a | Retained | Retained |%n",
                        plan.day(), plan.hubId(), plan.fuel().id(), plan.estimate().cycleDays()));
                else text.append(String.format(Locale.ROOT, "| %d | %s/%s | %.3f | %.3f | %.1f | %.1f | %.3f | %.3f |%n",
                        plan.day(), plan.hubId(), plan.fuel().id(), plan.inputs().departureKg(), plan.inputs().dailyKg(), plan.estimate().cycleDays(), plan.inputs().cycleDays(),
                        plan.suggestion().reorderMixtureKg(), plan.suggestion().targetMixtureKg()));
            }
            for (var plan : result.plans()) text.append("\nReview ").append(plan.day()).append(" ").append(plan.hubId()).append("/").append(plan.fuel().id()).append(": ").append(plan.estimate().explanation()).append("\n");
            text.append("\n").append(result.detail());
        }
        text.append("\nAll daily history flows reconcile independently against hub stocks and actual delivered manifests. Rolling averages include quiet observed days and exclude the current day. "
                + "Fuel, goods, aluminum and consolidated cash remain conserved; actual flights retain funded maneuvers, electricity and cargo preservation. Physical stock plus committed inbound fits storage. "
                + "End-of-day empty counts use less than 0.0001 kg usable mixture; history stockout flags use at most 0.000001 kg per ingredient at observation boundaries. "
                + "Residual dust and intraday replenishment can therefore make those counts differ. First nonempty recovery does not mean a full refill or a funded departure. "
                + "Measured withdrawals include outbound resale freight. Traffic requests are reported separately from actual consumption, so capacity limits and stockouts cannot be mistaken for fully served demand.\n");
        text.append("\nWhen both the departure allowance and measured demand are zero, no positive suggestion is available and existing targets are retained.\n");
        return text.toString();
    }
}
