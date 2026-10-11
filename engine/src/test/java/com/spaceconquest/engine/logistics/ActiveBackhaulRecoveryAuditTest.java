package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

import static com.spaceconquest.engine.logistics.LunarRoundTripAuditTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** Continuously active routes with conserved market shocks and physically paid upstream replenishment. */
class ActiveBackhaulRecoveryAuditTest {
    @TempDir Path directory;
    private static final String ALUMINUM = "refined_aluminum";
    private static final Map<String, Double> GOODS = Map.of("steel", 1250.0, "food_matrix", 1250.0);
    private static final List<String> FUELS = List.of("liquid_hydrogen", "liquid_oxygen", "hydrogen_gas", "rp1_kerosene", "refined_uranium");
    private final LunarRoundTripAuditTest fixture = new LunarRoundTripAuditTest();
    private final List<String> events = new ArrayList<>();

    @Test void continuouslyActiveRoutesRecheckAndClearCommittedArrivalsAfterMarketShocks() throws Exception {
        fixture.directory = directory;
        var run = opening();
        waitWithoutPurchases(run, "No lunar market stock", 3);
        release(run, "moon-market", 2500);
        waitWithoutPurchases(run, "Lunar return margin is zero", 3);
        double cash = sequesterBuyerCash(run, "low-market");
        run.state = fixture.withAluminumPrice(run.state, "depot-market", 1.25);
        until(run, state -> at(state, "trader", "depot-port") && hub(state, "depot-market").activeOrders().get(ALUMINUM).supplyKg() == 2500, 15);
        waitWithoutPurchases(run, "Final Earth buyer has no cash", 3);
        run.state = fixture.withAluminumPrice(run.state, "low-market", 1);
        restoreBuyerCash(run, "low-market", cash);
        waitWithoutPurchases(run, "Final Earth price cannot cover handling", 3);
        run.state = fixture.withAluminumPrice(run.state, "low-market", 3);
        until(run, state -> TradeRoute.REPOSITIONING.equals(route(state, "handling").phase()), 5);
        run.state = fixture.reload(run.state, "handler-climbing");
        run.state = fixture.withAluminumPrice(run.state, "low-market", 1);
        until(run, state -> at(state, "supplier", "depot-port") && TradeRoute.LOADING.equals(route(state, "handling").phase()), 5);
        assertEquals(0, hub(run.state, "depot-market").activeOrders().get(ALUMINUM).supplyKg());
        assertEquals(2500, hub(run.state, "low-market").activeOrders().get(ALUMINUM).supplyKg());
        assertEquals(0, route(run.state, "handling").onboardKg());
        assertTrue(at(run.state, "trader", "low-port"));
        record(run, "Protected fuel lets the trader complete the final delivery while the handler stages; the committed load sells despite the price fall");
        waitWithoutPurchases(run, "Live prices reject new loads after both committed flights finish", 120);
        assertEquals(7500, route(run.state, "lunar").totalVolumeMovedKg(), 1e-5);
        assertEquals(0, route(run.state, "handling").totalVolumeMovedKg(), 1e-5);
        assertEquals(5000, hub(run.state, "reserve-market").activeOrders().get(ALUMINUM).supplyKg());
        assertEquals(7500, stock(run.state, ALUMINUM), 1e-5);
        for (String id : FUELS) assertEquals(stock(run.opening, id) - stock(run.state, id), run.consumed.getOrDefault(id, 0.0), 1e-4, id);
        assertEquals(totalCash(run.opening), totalCash(run.state), 1e-4);
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/active-backhaul-recovery-report.md"), report(run));
    }

    private Run opening() throws Exception {
        var state = fixture.backhaulWorld(2500, 3);
        state = state.withTradeRoutes(state.tradeRoutes().stream().filter(item -> !item.id().equals("backhaul"))
                .map(item -> item.id().equals("depot-supply")
                        ? new TradeRoute(item.id(), item.name(), item.ownerEntityId(), item.originEntityId(), item.destinationEntityId(),
                        item.materialId(), 30000, 0, 5e6, List.of("restocker"), 0, false)
                        : item.id().equals("handling") ? item.atNewOrigin("low-market") : item).toList());
        var restocker = new ShipInstance("restocker", "supplier-design", "supplier-owner", 100, 0, 0, Map.of()).withPowerState(ShipPowerState.empty());
        var fleets = new ArrayList<>(state.fleets());
        fleets.add(new Fleet("restocker", "Restocker", "supplier-owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(restocker),
                FleetLocation.at(FleetLocation.Site.docked("low-port"))).withFuelPolicy(new FleetFuelPolicy(.05, .05, .05)));
        state = state.withFleets(fleets);
        var orders = new HashMap<String, MarketOrder>();
        for (String material : FUELS) orders.put(material, new MarketOrder(material, 0, 0, .005, 0));
        orders.put(ALUMINUM, new MarketOrder(ALUMINUM, 7500, 0, .8, 0));
        var hubs = new ArrayList<>(state.commercialHubs());
        hubs.add(new CommercialHub("reserve-market", "private-warehouse", 0, 10000, 7500, 10, orders));
        state = state.withCommercialHubs(hubs.stream().map(hub -> {
            if (!hub.id().equals("moon-market")) return hub;
            var empty = new HashMap<>(hub.activeOrders());
            empty.put(ALUMINUM, new MarketOrder(ALUMINUM, 0, 10000, .8, 0));
            return new CommercialHub(hub.id(), hub.entityId(), 0, hub.storageCapacityKg(), hub.currentStoredWeightKg() - 7500, 10, empty);
        }).toList());
        var run = fixture.commissioned(state);
        run.state = ShipBatteryCharging.charge(run.state, "restocker", "low-port", 500 / .9);
        assertEquals(500, ship(run.state, "restocker").powerState().batteryChargeKwh(), 1e-5);
        supplyCycle(run);
        fixture.oneWay(run, "trader", "lunar", "depot-port", "moon-port", GOODS, 120000);
        run.state = run.state.withTradeRoutes(run.state.tradeRoutes().stream().map(item -> List.of("lunar", "handling").contains(item.id())
                ? item.withActive(true) : item).toList());
        record(run, "Both autonomous routes activated once");
        return run;
    }

    private void supplyCycle(Run run) throws Exception {
        double moonFuel = List.of("liquid_hydrogen", "liquid_oxygen").stream()
                .mapToDouble(id -> hub(run.state, "moon-market").activeOrders().get(id).supplyKg()).sum();
        double needed = Math.max(0, 90000 - moonFuel);
        double depotFuel = List.of("liquid_hydrogen", "liquid_oxygen").stream()
                .mapToDouble(id -> hub(run.state, "depot-market").activeOrders().get(id).supplyKg()).sum();
        double remaining = needed + 240000 - ship(run.state, "trader").currentFuelKg() - depotFuel;
        while (remaining > 1e-4) {
            double batch = Math.min(30000, remaining);
            fixture.dispatchAndReturn(run, "restocker", "depot-supply", "low-port",
                    PropulsionCatalog.drive("mod_hydrolox_rocket").propellantMaterials(batch), 30000);
            activeIfStarted(run);
            remaining -= batch;
        }
        while (needed > 1e-4) {
            double batch = Math.min(32000, needed);
            var cargo = new HashMap<>(PropulsionCatalog.drive("mod_hydrolox_rocket").propellantMaterials(batch));
            cargo.put("hydrogen_gas", 17000.0); cargo.put("refined_uranium", 50.0);
            fixture.dispatchAndReturn(run, "tanker", "moon-supply", "depot-port", cargo, 45000);
            activeIfStarted(run);
            needed -= batch;
        }
        fixture.dispatchAndReturn(run, "restocker", "depot-supply", "low-port", GOODS, 30000);
        activeIfStarted(run);
    }

    private void until(Run run, Predicate<GameState> condition, int limit) {
        int days = 0;
        while (!condition.test(run.state) && days++ < limit) {
            fixture.day(run);
            assertActive(run);
        }
        assertTrue(days < limit, "Timed out: " + route(run.state, "lunar").status() + " / " + route(run.state, "handling").status());
    }

    private void waitWithoutPurchases(Run run, String reason, int days) throws Exception {
        assertFalse(fleet(run.state, "trader").location().inTransit());
        assertFalse(fleet(run.state, "supplier").location().inTransit());
        var before = run.state;
        run.state = fixture.reload(run.state, "waiting-" + run.state.turn());
        for (int day = 0; day < days; day++) {
            fixture.day(run);
            assertActive(run);
            assertFalse(fleet(run.state, "trader").location().inTransit(), reason);
            assertFalse(fleet(run.state, "supplier").location().inTransit(), reason);
        }
        assertEquals(before.commercialHubs(), run.state.commercialHubs(), reason);
        assertEquals(before.marketAccounts(), run.state.marketAccounts(), reason);
        assertEquals(before.empires(), run.state.empires(), reason);
        for (String ship : List.of("trader", "supplier")) {
            assertEquals(ship(before, ship).currentFuelKg(), ship(run.state, ship).currentFuelKg(), reason);
            assertEquals(ship(before, ship).storedCargoKg(), ship(run.state, ship).storedCargoKg(), reason);
        }
        record(run, reason);
    }

    private void release(Run run, String destination, double kg) {
        double total = stock(run.state, ALUMINUM);
        run.state = run.state.withCommercialHubs(run.state.commercialHubs().stream().map(hub -> {
            double delta = hub.id().equals("reserve-market") ? -kg : hub.id().equals(destination) ? kg : 0;
            if (delta == 0) return hub;
            var orders = new HashMap<>(hub.activeOrders()); var order = orders.get(ALUMINUM);
            assertTrue(order.supplyKg() + delta >= 0);
            orders.put(ALUMINUM, new MarketOrder(ALUMINUM, order.supplyKg() + delta, order.demandKg(), order.pricePerKg(), order.shortcomingScore()));
            return new CommercialHub(hub.id(), hub.entityId(), 0, hub.storageCapacityKg(), hub.currentStoredWeightKg() + delta, 10, orders);
        }).toList());
        assertEquals(total, stock(run.state, ALUMINUM));
        record(run, "Released tracked private lunar inventory");
    }

    private double sequesterBuyerCash(Run run, String hubId) {
        double cash = run.state.marketAccounts().stream().filter(item -> item.hubId().equals(hubId))
                .mapToDouble(MarketAccount::unsettledSalesCredits).findFirst().orElseThrow();
        transferCash(run, hubId, -cash);
        return cash;
    }

    private void restoreBuyerCash(Run run, String hubId, double cash) { transferCash(run, hubId, cash); }

    private void transferCash(Run run, String hubId, double cash) {
        double total = totalCash(run.state);
        run.state = run.state.withMarketAccounts(run.state.marketAccounts().stream().map(item -> item.hubId().equals(hubId)
                ? new MarketAccount(hubId, item.unsettledSalesCredits() + cash) : item).toList());
        run.state = run.state.withEmpires(run.state.empires().stream().map(owner -> owner.id().equals("provider")
                ? new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), owner.treasuryCredits() - cash,
                owner.corporateTaxRate(), owner.controlledSystemIds(), owner.ministries(), owner.systemGovernorAssignments(), owner.unlockedTechIds(), owner.activeShipDesignIds())
                : owner).toList());
        assertEquals(total, totalCash(run.state), 1e-5);
    }

    private void activeIfStarted(Run run) { if (!events.isEmpty()) assertActive(run); }
    private void assertActive(Run run) {
        assertTrue(route(run.state, "lunar").isActive());
        assertTrue(route(run.state, "handling").isActive());
    }
    private static boolean at(GameState state, String ship, String port) { return fleet(state, ship).location().isAt(FleetLocation.Site.docked(port)); }
    private static double delivered(GameState state) { return hub(state, "low-market").activeOrders().get(ALUMINUM).supplyKg(); }
    private void record(Run run, String event) { events.add(String.format(Locale.ROOT, "| %d | %s | %.0f | %s | %s |", run.state.turn(), event,
            delivered(run.state), route(run.state, "lunar").phase(), route(run.state, "handling").phase())); }
    private String report(Run run) {
        return "# Active backhaul recovery audit\n\nBoth return and handling routes are activated once and remain active through all subsequent ticks. "
                + "A separate paid RP-1 restocker supplies goods and fuel without taking the handler's ship. "
                + "Private lunar inventory releases conserve a tracked reserve hub; buyer cash shocks transfer money to and from the port operator. "
                + "These are explicit market events, not a production or private-warehouse transport model. No cargo or money is created.\n\n"
                + "| Day | Event | Final Earth aluminum kg | Trader phase | Handler phase |\n|---|---|---:|---|---|\n"
                + String.join("\n", events) + "\n\nOne paid outward goods shipment and two autonomous aluminum legs deliver 2,500 kg to final Earth. "
                + "Protected fuel lets the lunar trader perform the final leg itself while the handler stages. The final buyer takes the committed load after its price falls. "
                + "Waiting preserves market stock, accounts, owner cash, cargo and main fuel. State reloads during waits, funded supply flights and the handler's empty climb. "
                + "All ticks retain funded electricity, cargo preservation and successful maneuvers. Fuel materials, total aluminum and total money reconcile. "
                + "Source production, surface lift, capital, overhead and autonomous upstream procurement remain excluded.\n";
    }
}
