package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import com.spaceconquest.engine.market.HubStockTarget;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static com.spaceconquest.engine.logistics.LunarRoundTripAuditTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** Finite warm-start inventory, continuous paid suppliers and explicit paid local goods consumption. */
class ThresholdLunarSupplyAuditTest {
    @TempDir Path directory;
    private static final String ALUMINUM = "refined_aluminum";
    private static final List<String> FUELS = List.of("liquid_hydrogen", "liquid_oxygen", "hydrogen_gas", "rp1_kerosene", "refined_uranium");
    private static final List<String> GOODS = List.of("steel", "food_matrix");
    private final LunarRoundTripAuditTest fixture = new LunarRoundTripAuditTest();
    private final List<String> events = new ArrayList<>();
    private final Map<String, Integer> departures = new TreeMap<>();
    private final Map<String, Double> consumedGoods = new TreeMap<>();
    private final Map<String, Long> loadedAt = new HashMap<>();
    private final Map<String, Long> longestDeliveryDays = new TreeMap<>();
    private final Map<String, Double> minimumHydrolox = new TreeMap<>();
    private int unfundedTraderArrivals;
    private int sourceWaitDays;
    private int deliveriesAfterRestoration;

    @Test void activeThresholdSuppliersSupportRepeatedLunarTrade() throws Exception {
        audit(false, .8, false);
    }

    @Test void activeThresholdSuppliersRecoverAfterSourceStockIsRestored() throws Exception {
        audit(true, .8, false);
    }

    @Test void lowThresholdServesSmallerFuelPurchasesWithoutForcingDepotReplenishment() throws Exception {
        audit(false, .2, false);
    }

    @Test void fuelPrioritySustainsBackhaulsWithExpensiveFuel() throws Exception {
        audit(false, .8, true);
    }

    private void audit(boolean shortage, double threshold, boolean expensive) throws Exception {
        fixture.directory = directory;
        var run = opening(threshold, expensive);
        for (int day = 0; day < 300; day++) {
            if (shortage && day == 30) transferSource(run, true);
            if (shortage && day == 100) transferSource(run, false);
            consumeGoods(run);
            var before = run.state;
            fixture.day(run);
            inspectTick(run, before);
            if (day % 30 == 0) run.state = fixture.reload(run.state, "threshold-" + shortage + "-" + day);
        }
        record(run, "Completed 300 continuously active days");
        for (String material : FUELS) assertEquals(stock(run.opening, material) - stock(run.state, material),
                run.consumed.getOrDefault(material, 0.0), 1e-4, material);
        for (String material : GOODS) assertEquals(stock(run.opening, material) - stock(run.state, material),
                consumedGoods.getOrDefault(material, 0.0), 1e-4, material);
        assertEquals(stock(run.opening, ALUMINUM), stock(run.state, ALUMINUM), 1e-4);
        assertEquals(totalCash(run.opening), totalCash(run.state), 1e-4);
        String name = expensive ? "threshold-lunar-price-control-report.md" : threshold < .5 ? "threshold-lunar-low-buffer-report.md"
                : shortage ? "threshold-lunar-shortage-report.md" : "threshold-lunar-supply-report.md";
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target", name), report(run, shortage));
        if (expensive) {
            assertTrue(hub(run.state, "low-market").activeOrders().get(ALUMINUM).supplyKg() >= 2500, report(run, shortage));
            assertTrue(departures.getOrDefault("lunar", 0) >= 2, report(run, shortage));
            assertFalse(TradeRoute.RETURNING.equals(route(run.state, "lunar").phase())
                    && !fleet(run.state, "trader").location().inTransit(), "Unsecured subsequent trades wait before loading.");
        } else if (threshold < .5) {
            assertTrue(unfundedTraderArrivals > 0, report(run, shortage));
            assertTrue(departures.getOrDefault("lunar", 0) >= 2, report(run, shortage));
            assertTrue(minimumHydrolox.get("depot-market") > 450000 * threshold, report(run, shortage));
            assertTrue(minimumHydrolox.get("depot-market") < 120000, report(run, shortage));
            assertEquals(0, departures.getOrDefault("depot-supply", 0), report(run, shortage));
        } else {
            assertTrue(departures.getOrDefault("lunar", 0) >= 6, report(run, shortage));
            assertTrue(hub(run.state, "low-market").activeOrders().get(ALUMINUM).supplyKg() >= 7500, report(run, shortage));
        }
        if (threshold >= .5) assertTrue(departures.getOrDefault("depot-supply", 0) > 0, report(run, shortage));
        assertTrue(departures.getOrDefault("moon-supply", 0) > 0, report(run, shortage));
        if (shortage) {
            assertTrue(sourceWaitDays > 0, report(run, true));
            assertTrue(deliveriesAfterRestoration > 0, report(run, true));
        }
    }

    Run opening(double threshold, boolean expensive) throws Exception {
        var state = fixture.backhaulWorld(2500, expensive ? 8 : 3);
        var mixture = PropulsionCatalog.drive("mod_hydrolox_rocket");
        var depotMixture = mixture.propellantMaterials(450000);
        var moonMixture = mixture.propellantMaterials(300000);
        var sourceMixture = mixture.propellantMaterials(1050000);
        state = state.withCommercialHubs(state.commercialHubs().stream().map(hub -> {
            var orders = new HashMap<>(hub.activeOrders());
            var targets = new HashMap<String, HubStockTarget>();
            for (String id : List.of("liquid_hydrogen", "liquid_oxygen")) {
                double amount = hub.id().equals("low-market") ? sourceMixture.get(id)
                        : hub.id().equals("depot-market") ? depotMixture.get(id) : moonMixture.get(id);
                double price = expensive ? hub.id().equals("low-market") ? .005 : hub.id().equals("depot-market") ? .02 : .04
                        : hub.id().equals("low-market") ? .0001 : hub.id().equals("depot-market") ? .0015 : .005;
                orders.put(id, new MarketOrder(id, amount, 0, price, 0));
                if (!hub.id().equals("low-market")) targets.put(id, new HubStockTarget(amount * threshold, amount));
            }
            if (!expensive) {
                var order = orders.get("rp1_kerosene");
                orders.put("rp1_kerosene", new MarketOrder(order.resourceId(), order.supplyKg(), order.demandKg(), .0001, 0));
            }
            for (String id : GOODS) {
                double amount = hub.id().equals("depot-market") ? 3750 : 0;
                double price = hub.id().equals("moon-market") ? 3 : hub.id().equals("depot-market") ? 1.05 : .8;
                orders.put(id, new MarketOrder(id, amount, 0, price, 0));
                if (hub.id().equals("moon-market")) targets.put(id, new HubStockTarget(1000, 1250));
            }
            if (hub.id().equals("moon-market")) {
                orders.put("hydrogen_gas", new MarketOrder("hydrogen_gas", 160000, 0, .04, 0));
                orders.put("refined_uranium", new MarketOrder("refined_uranium", 600, 0, 3, 0));
                targets.put("hydrogen_gas", new HubStockTarget(120000, 160000));
                targets.put("refined_uranium", new HubStockTarget(400, 600));
            }
            if (hub.id().equals("depot-market")) orders.put(ALUMINUM, new MarketOrder(ALUMINUM, 0, 10000, expensive ? 3.5 : 1.25, 0));
            return new CommercialHub(hub.id(), hub.entityId(), 0, hub.storageCapacityKg(),
                    orders.values().stream().mapToDouble(MarketOrder::supplyKg).sum(), 10, orders, targets);
        }).toList());
        var reserves = new HashMap<String, MarketOrder>();
        for (String material : FUELS) reserves.put(material, new MarketOrder(material, 0, 0, .005, 0));
        for (String material : GOODS) reserves.put(material, new MarketOrder(material, 0, 0, .8, 0));
        reserves.put(ALUMINUM, new MarketOrder(ALUMINUM, 0, 0, .8, 0));
        var hubs = new ArrayList<>(state.commercialHubs());
        hubs.add(new CommercialHub("reserve-market", "private-warehouse", 0, 2e6, 0, 10, reserves));
        state = state.withCommercialHubs(hubs);
        var restocker = new ShipInstance("restocker", "supplier-design", "supplier-owner", 100, 0, 0, Map.of()).withPowerState(ShipPowerState.empty());
        var fleets = new ArrayList<>(state.fleets());
        fleets.add(new Fleet("restocker", "Restocker", "supplier-owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(restocker),
                FleetLocation.at(FleetLocation.Site.docked("low-port"))).withFuelPolicy(new FleetFuelPolicy(.05, .05, .05)));
        var secondTanker = new ShipInstance("tanker-2", "tanker-design", "supplier-owner", 100, 0, 0, Map.of()).withPowerState(ShipPowerState.empty());
        fleets.add(new Fleet("tanker-2", "Second tanker", "supplier-owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(secondTanker),
                FleetLocation.at(FleetLocation.Site.docked("depot-port"))).withFuelPolicy(new FleetFuelPolicy(.05, .05, .05)));
        state = state.withFleets(fleets);
        var routes = new ArrayList<TradeRoute>();
        routes.add(route(state, "lunar").withActive(true));
        routes.add(new TradeRoute("depot-supply", "Depot supply", "supplier-owner", "low-market", "depot-market",
                "liquid_oxygen", 30000, 0, 5e6, List.of("restocker"), 0, true));
        routes.add(route(state, "moon-supply").withActive(true));
        routes.add(new TradeRoute("moon-supply-2", "Second lunar supplier", "supplier-owner", "depot-market", "moon-market",
                "liquid_oxygen", 50000, 0, 5e6, List.of("tanker-2"), 0, true));
        routes.add(route(state, "handling").atNewOrigin("low-market").withActive(true));
        state = state.withTradeRoutes(routes);
        var run = fixture.commissioned(state);
        run.state = ShipBatteryCharging.charge(run.state, "restocker", "low-port", 500 / .9);
        run.state = ShipPowerResupply.buy(run.state, "tanker-2", "depot-port", "uranium", 100);
        record(run, "Warm inventory buffers; all five routes activated once; reorder fraction " + threshold + "; expensive fuel " + expensive);
        return run;
    }

    void consumeGoods(Run run) {
        var hub = hub(run.state, "moon-market");
        var requested = new HashMap<String, Double>();
        for (String material : GOODS) {
            double kg = Math.min(25, hub.activeOrders().get(material).supplyKg());
            if (kg > 0) requested.put(material, kg);
        }
        if (requested.isEmpty()) return;
        var purchase = ConstructionMaterials.buyUpTo(run.state, "moon-port", "provider", requested);
        assertTrue(purchase.covers(requested));
        run.state = purchase.state();
        requested.forEach((id, kg) -> consumedGoods.merge(id, kg, Double::sum));
    }

    void transferSource(Run run, boolean hide) {
        var source = hub(run.state, hide ? "low-market" : "reserve-market");
        var amounts = new HashMap<String, Double>();
        for (String material : List.of("liquid_hydrogen", "liquid_oxygen")) amounts.put(material, source.activeOrders().get(material).supplyKg());
        run.state = run.state.withCommercialHubs(run.state.commercialHubs().stream().map(hub -> {
            if (!List.of("low-market", "reserve-market").contains(hub.id())) return hub;
            var orders = new HashMap<>(hub.activeOrders());
            double delta = 0;
            for (var entry : amounts.entrySet()) {
                double mass = hub.id().equals(source.id()) ? -entry.getValue() : entry.getValue();
                delta += mass;
                var order = orders.get(entry.getKey());
                orders.put(entry.getKey(), new MarketOrder(entry.getKey(), order.supplyKg() + mass, order.demandKg(), order.pricePerKg(), 0));
            }
            return hub.withInventory(run.state.turn(), hub.currentStoredWeightKg() + delta, orders);
        }).toList());
        record(run, hide ? "Source hydrolox transferred to tracked inaccessible reserve" : "Source hydrolox restored from reserve");
    }

    private void inspectTick(Run run, GameState before) {
        for (var route : run.state.tradeRoutes()) {
            assertTrue(route.isActive(), route.id());
            var shipId = route.assignedFreighterIds().getFirst();
            var fleet = fleet(run.state, shipId);
            var previous = fleet(before, shipId);
            var previousRoute = route(before, route.id());
            if (previousRoute.onboardKg() <= 1e-6 && route.onboardKg() > 1e-6 && TradeRoute.DELIVERING.equals(route.phase())) {
                departures.merge(route.id(), 1, Integer::sum);
                loadedAt.put(route.id(), run.state.turn());
                record(run, route.id() + " departed " + route.phase() + " with " + Math.round(route.onboardKg()) + " kg " + route.cargoManifest().keySet());
            }
            if (route.totalVolumeMovedKg() > previousRoute.totalVolumeMovedKg()) {
                longestDeliveryDays.merge(route.id(), run.state.turn() - loadedAt.getOrDefault(route.id(), run.state.turn()), Math::max);
                if (route.id().equals("depot-supply") && run.state.turn() > 100) deliveriesAfterRestoration++;
            }
            if (shipId.equals("trader") && previous.location().inTransit() && !fleet.location().inTransit()) {
                String next = fleet.location().isAt(FleetLocation.Site.docked("moon-port")) ? "depot-market" : "moon-market";
                var prepared = TradeLegReadiness.prepare(run.state, fleet, hub(run.state, next));
                if (!TradeLegReadiness.inspect(prepared, fleet(prepared, shipId), hub(prepared, next)).ready()) unfundedTraderArrivals++;
                record(run, "Trader arrived; next-leg fuel readiness checked");
            }
        }
        var sourceRoute = route(run.state, "depot-supply");
        if (TradeRoute.LOADING.equals(sourceRoute.phase()) && hydrolox(run.state, "low-market") == 0
                && List.of("liquid_hydrogen", "liquid_oxygen").stream().anyMatch(id ->
                HubReplenishment.requestedKg(run.state, hub(run.state, "depot-market"), id, sourceRoute.id()) > 0)) sourceWaitDays++;
        for (String hub : List.of("depot-market", "moon-market")) minimumHydrolox.merge(hub, hydrolox(run.state, hub), Math::min);
        for (var hub : run.state.commercialHubs()) for (var entry : hub.stockTargets().entrySet()) {
            double position = hub.activeOrders().get(entry.getKey()).supplyKg() + HubReplenishment.inboundKg(run.state, hub, entry.getKey(), null);
            assertTrue(position <= entry.getValue().targetStockKg() + 1e-4, hub.id() + "/" + entry.getKey() + " duplicate inbound");
        }
    }

    private double hydrolox(GameState state, String hubId) {
        return PropulsionCatalog.drive("mod_hydrolox_rocket").propellantMaterials(1).entrySet().stream()
                .mapToDouble(item -> hub(state, hubId).activeOrders().get(item.getKey()).supplyKg() / item.getValue()).min().orElse(0);
    }

    private void record(Run run, String event) {
        events.add(String.format(Locale.ROOT, "| %d | %s | %.0f | %.0f | %.0f |", run.state.turn(), event,
                hydrolox(run.state, "depot-market"), hydrolox(run.state, "moon-market"), hub(run.state, "low-market").activeOrders().get(ALUMINUM).supplyKg()));
    }

    private String report(Run run, boolean shortage) {
        return "# Threshold lunar supply audit\n\nWarm finite opening buffers: Earth depot 450,000 kg hydrolox and Moon 300,000 kg. "
                + "Hydrolox reorder fractions are recorded in the opening event, with separate ingredient settings. Moon fission hydrogen reorders at 120,000/160,000 kg and uranium at 400/600 kg. "
                + "Goods consumption is an explicit paid local purchase of up to 25 kg per material per day, tracked as consumption. "
                + "Five routes, including two fission lunar suppliers, stay active without manual dispatch or resets. Prices are fixed test inputs; this is not a profit audit. "
                + "Source interruption: " + shortage + ". Loaded departures: " + departures + ". Unfunded trader arrival previews: " + unfundedTraderArrivals + ". "
                + "Longest loaded delivery days: " + longestDeliveryDays + ". Minimum hydrolox kg: " + minimumHydrolox + ". "
                + "Source-stock wait days: " + sourceWaitDays + "; depot deliveries after day 100: " + deliveriesAfterRestoration + ".\n\n"
                + "Usable hydrolox is limited by the scarcer mixture ingredient. Prices (low Earth / depot / Moon): liquid hydrogen "
                + List.of("low-market", "depot-market", "moon-market").stream().map(id -> Double.toString(hub(run.state, id).activeOrders().get("liquid_hydrogen").pricePerKg())).toList()
                + ", RP-1 " + hub(run.state, "low-market").activeOrders().get("rp1_kerosene").pricePerKg()
                + "; fission hydrogen depot/Moon 0.005/0.04 and uranium 1/3 credits/kg. Lunar aluminum 0.8; depot/final Earth aluminum "
                + hub(run.state, "depot-market").activeOrders().get(ALUMINUM).pricePerKg() + "/" + hub(run.state, "low-market").activeOrders().get(ALUMINUM).pricePerKg() + ".\n\n"
                + "| Day | Event | Depot usable hydrolox kg | Moon usable hydrolox kg | Final Earth aluminum kg |\n|---|---|---:|---:|---:|\n"
                + String.join("\n", events) + "\n\nAll ticks check paid electricity, cargo preservation, maneuver success, active routes and conserved money. "
                + "Fuel, aluminum and explicitly consumed goods reconcile. Initial stock lift, production, capital and overhead remain excluded.\n";
    }
}
