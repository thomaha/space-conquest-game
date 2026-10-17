package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.industry.PowerGridState;
import com.spaceconquest.engine.industry.IndustryMarketProcessor;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.StationModule;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Paid port replenishment and repeated physical returns by the same ships. */
class LunarRoundTripAuditTest {
    @TempDir Path directory;
    private static final double FUEL_PRICE = .005;
    private static final Map<String, Double> GOODS = Map.of("steel", 1250.0, "food_matrix", 1250.0);
    private static final List<String> FUELS = List.of("liquid_hydrogen", "liquid_oxygen", "hydrogen_gas", "rp1_kerosene", "refined_uranium");
    private record Tanker(String name, int tanks, String hold, boolean battery) {}
    private record TankerQuote(double hydrolox, double hydrogen, double cost, double days) {}
    private static final String BACKHAUL = "refined_aluminum";
    private record BackhaulResult(double quantity, double postedPrice, long days, int trips, double consumedCost,
                                  double result, double increment, double breakEvenPrice) {}
    static final class Run {
        GameState state;
        final GameState opening;
        final Map<String, Double> consumed = new HashMap<>();
        final List<String> rows = new ArrayList<>();
        int trips;
        double chargingCost;
        Run(GameState state) { this.state = state; opening = state; }
    }

    @Test void sameFreighterCompletesThreePaidLunarRoundTripsWithFiniteSuppliedFuel() throws Exception {
        var run = new Run(world());
        for (String id : List.of("supplier", "tanker", "trader")) {
            String port = id.equals("supplier") ? "low-port" : "depot-port";
            double cash = totalCash(run.state);
            run.state = ShipBatteryCharging.charge(run.state, id, port, 500 / .9);
            assertEquals(500, ship(run.state, id).powerState().batteryChargeKwh(), 1e-5);
            assertEquals(cash, totalCash(run.state), 1e-5);
            run.chargingCost += 500 / .9 * .02;
        }
        run.state = ShipPowerResupply.buy(run.state, "tanker", "depot-port", "uranium", 100);
        assertEquals(100, ship(run.state, "tanker").powerState().generatorMaterialsKg().get("refined_uranium"));
        for (int cycle = 1; cycle <= 3; cycle++) {
            for (int batch = 0; batch < 7; batch++)
                dispatchAndReturn(run, "supplier", "depot-supply", "low-port",
                        PropulsionCatalog.drive("mod_hydrolox_rocket").propellantMaterials(30000), 30000);
            dispatchAndReturn(run, "supplier", "depot-supply", "low-port", GOODS, 30000);
            // Tanker carries its own return hydrogen and feed alongside net hydrolox deliveries.
            for (int batch = 0; batch < 30; batch++) {
                var cargo = new HashMap<>(PropulsionCatalog.drive("mod_hydrolox_rocket").propellantMaterials(3000));
                cargo.put("hydrogen_gas", 17000.0);
                cargo.put("refined_uranium", 50.0);
                dispatchAndReturn(run, "tanker", "moon-supply", "depot-port", cargo, 30000);
            }
            assertTrue(mixtureStock(run.state, "moon-market") >= 90000 - 1e-4);
            dispatchAndReturn(run, "trader", "lunar", "depot-port", GOODS, 120000);
            assertEquals(cycle * 2500, route(run.state, "lunar").totalVolumeMovedKg(), 1e-5);
            assertTrue(ship(run.state, "trader").currentFuelKg() >= 24000);
            run.state = reload(run.state, "cycle-" + cycle);
            double sales = cycle * 6000.0;
            // Calculate source withdrawals directly; internal port resales are excluded.
            double acquired = acquisitionCost(run);
            double retained = retainedFuelValue(run.state);
            double result = sales - acquired - run.chargingCost + retained;
            double consumedCost = FUELS.stream().mapToDouble(id -> run.consumed.getOrDefault(id, 0.0) * price(id)).sum();
            assertEquals(sales - cycle * 2500 * .8 - consumedCost - run.chargingCost, result, 1e-4);
            assertTrue(result < 0, "This measured empty-return supply chain consumes more than its cargo margin.");
            run.rows.add(String.format(Locale.ROOT, "| %d | %d | %d | %.2f | %.2f | %.2f | %.2f |",
                    cycle, run.state.turn(), run.trips, consumedCost, acquired, retained, result));
        }
        assertEquals(117, run.trips);
        assertEquals(7500, hub(run.state, "moon-market").activeOrders().get("steel").supplyKg()
                + hub(run.state, "moon-market").activeOrders().get("food_matrix").supplyKg(), 1e-5);
        assertEquals(totalCash(run.opening), totalCash(run.state), 1e-4);
        for (String id : FUELS) assertEquals(stock(run.opening, id) - stock(run.state, id),
                run.consumed.getOrDefault(id, 0.0), 1e-4, id + " physical conservation");
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/lunar-round-trip-report.md"), report(run));
    }

    @Test void emptyLunarPortBlocksReturnWithoutBuyingRemoteFuelOrPartiallySpendingCash() throws Exception {
        var state = world();
        var run = new Run(state.withFleets(state.fleets().stream().filter(item -> !item.id().equals("tanker")).toList())
                .withTradeRoutes(state.tradeRoutes().stream().filter(item -> !item.id().equals("moon-supply")).toList()));
        for (String id : List.of("supplier", "trader")) run.state = ShipBatteryCharging.charge(run.state, id,
                id.equals("supplier") ? "low-port" : "depot-port", 500 / .9);
        for (int batch = 0; batch < 4; batch++) dispatchAndReturn(run, "supplier", "depot-supply", "low-port",
                PropulsionCatalog.drive("mod_hydrolox_rocket").propellantMaterials(30000), 30000);
        dispatchAndReturn(run, "supplier", "depot-supply", "low-port", GOODS, 30000);
        dispatch(run, "trader", "lunar", "depot-port", GOODS, 120000, false);
        for (int day = 0; day < 10 && !fleet(run.state, "trader").location().isAt(FleetLocation.Site.docked("moon-port")); day++) day(run);
        assertEquals(2500, route(run.state, "lunar").totalVolumeMovedKg(), 1e-5);
        assertEquals(TradeRoute.RETURNING, route(run.state, "lunar").phase());
        assertTrue(fleet(run.state, "trader").location().isAt(FleetLocation.Site.docked("moon-port")));
        var before = run.state;
        assertFalse(TradeLegReadiness.inspect(before, fleet(before, "trader"), hub(before, "depot-market")).ready());
        assertSame(before, TradeLegReadiness.prepare(before, fleet(before, "trader"), hub(before, "depot-market")));
        assertSame(before, ShipFueling.refuel(before, "trader", "low-port", 1000));
        assertSame(before, ShipFueling.refuel(before, "trader", "moon-port", 1000));
        run.state = reload(run.state, "waiting-for-lunar-fuel");
        for (int day = 0; day < 3; day++) day(run);
        assertEquals(before.commercialHubs(), run.state.commercialHubs());
        assertEquals(before.marketAccounts(), run.state.marketAccounts());
        assertEquals(before.empires(), run.state.empires());
        assertEquals(ship(before, "trader").currentFuelKg(), ship(run.state, "trader").currentFuelKg());
        assertEquals(TradeRoute.RETURNING, route(run.state, "lunar").phase());
        assertFalse(fleet(run.state, "trader").location().inTransit());
    }

    @Test void paidFuelDeliveryFundsItsReturnWithoutInventingDestinationStockOrChangingPolicy() throws Exception {
        var state = world();
        state = state.withFleets(List.of(fleet(state, "tanker"))).withTradeRoutes(List.of(route(state, "moon-supply")));
        state = ShipBatteryCharging.charge(state, "tanker", "depot-port", 500 / .9);
        state = ShipPowerResupply.buy(state, "tanker", "depot-port", "uranium", 100);
        state = ShipFueling.refuel(state, "tanker", "depot-port", 60000);
        var original = state;
        var shipment = new LogisticsProcessor().previewBasket(state, route(state, "moon-supply"),
                Map.of("hydrogen_gas", 17000.0, "refined_uranium", 50.0), true);
        assertNotNull(shipment);
        assertEquals(0, hub(original, "moon-market").currentStoredWeightKg());
        assertEquals(hub(original, "moon-market"), hub(shipment.state(), "moon-market"), "No future delivery enters live port inventory during departure");
        assertEquals(fleet(original, "tanker").fuelPolicy(), fleet(shipment.state(), "tanker").fuelPolicy());
        assertEquals(totalCash(original), totalCash(shipment.state()), 1e-5);
        var run = new Run(shipment.state().withTradeRoutes(List.of(shipment.route().withActive(false))));
        run.state = reload(run.state, "fuel-delivery-departure");
        assertEquals(fleet(original, "tanker").fuelPolicy(), fleet(run.state, "tanker").fuelPolicy());
        for (int days = 0; days < 60 && !(route(run.state, "moon-supply").onboardKg() == 0
                && fleet(run.state, "tanker").location().isAt(FleetLocation.Site.docked("depot-port"))); days++) day(run);
        assertEquals(17050, route(run.state, "moon-supply").totalVolumeMovedKg(), 1e-5);
        assertTrue(fleet(run.state, "tanker").location().isAt(FleetLocation.Site.docked("depot-port")));
        assertEquals(totalCash(original), totalCash(run.state), 1e-5);
        for (String material : FUELS) assertEquals(stock(run.opening, material) - stock(run.state, material),
                run.consumed.getOrDefault(material, 0.0), 1e-4, material);
        var emptyBuyer = original.withMarketAccounts(original.marketAccounts().stream().map(account -> account.hubId().equals("moon-market")
                ? new MarketAccount(account.hubId(), 0) : account).toList());
        assertNull(new LogisticsProcessor().previewBasket(emptyBuyer, route(emptyBuyer, "moon-supply"),
                Map.of("hydrogen_gas", 17000.0, "refined_uranium", 50.0), true));
        assertEquals(hub(original, "moon-market"), hub(emptyBuyer, "moon-market"));
    }

    void dispatchAndReturn(Run run, String shipId, String routeId, String origin,
                                   Map<String, Double> cargo, double capacity) throws Exception {
        double before = route(run.state, routeId).totalVolumeMovedKg();
        dispatch(run, shipId, routeId, origin, cargo, capacity);
        int days = 0;
        while (!(fleet(run.state, shipId).location().isAt(FleetLocation.Site.docked(origin))
                && TradeRoute.LOADING.equals(route(run.state, routeId).phase())) && days++ < 60) day(run);
        assertTrue(days < 60, shipId + " return blocked: " + route(run.state, routeId).status()
                + " site " + fleet(run.state, shipId).location().current() + " destination " + fleet(run.state, shipId).location().destination()
                + " manifest " + ship(run.state, shipId).storedCargoKg()
                + " fuel " + ship(run.state, shipId).currentFuelKg() + " phase " + route(run.state, routeId).phase()
                + " " + TradeLegReadiness.inspect(run.state, fleet(run.state, shipId),
                hub(run.state, route(run.state, routeId).originEntityId())));
        assertEquals(before + cargo.values().stream().mapToDouble(Double::doubleValue).sum(),
                route(run.state, routeId).totalVolumeMovedKg(), 1e-4);
        assertEquals(0, route(run.state, routeId).onboardKg());
        run.trips++;
    }

    @Test void compareCatalogTankerPayloadsAndVerifyThreeCycleSupplyEconomics() throws Exception {
        var candidates = List.of(new Tanker("Original", 2, "mod_cargo_vault", true),
                new Tanker("Larger hold only", 2, "mod_cargo_hold_large", true),
                new Tanker("Two tanks without battery", 2, "mod_cargo_vault", false),
                new Tanker("Three tanks with battery", 3, "mod_cargo_vault", true),
                new Tanker("Three tanks and vault", 3, "mod_cargo_vault", false),
                new Tanker("Three tanks and large hold", 3, "mod_cargo_hold_large", false),
                new Tanker("Four tanks and compact hold", 4, ChemicalFreighterCatalog.CARGO_HOLD, false),
                new Tanker("One tank and vault", 1, "mod_cargo_vault", false));
        var text = new StringBuilder("# Lunar tanker comparison\n\nBounded catalog fission comparison at fixed 5% tanker and 20% goods reserves. ")
                .append("All designs retain 0.5 cm steel armor and the original research and prices. ")
                .append("The payload grid uses 1,000 kg hydrolox increments, 1,000 kg return-hydrogen increments and 50 kg uranium. ")
                .append("The selected payload minimizes projected consumed fuel value per kg of net hydrolox. ")
                .append("Paid outward arrival, local sale, local return purchases and complete return readiness are projected on isolated snapshots. ")
                .append("Every selected design then completes three actual goods cycles. The final tanker load and depot batches shrink to current requirements. ")
                .append("All comparison fixtures start with 10,000,000 kg hydrogen gas and 20,000 kg uranium at the depot, enough for the inefficient compact-hold case. ")
                .append("This shared scheduling policy also applies to the original design. No return cargo is sold.\n\n")
                .append("| Design | Slots | Net hydrolox kg/trip | Return hydrogen kg/trip | Quote cost/kg | Tanker trips | All round trips | Days | Three-cycle consumption result |\n")
                .append("|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        double original = Double.NaN, improved = Double.NaN;
        int accepted = 0;
        for (var candidate : candidates) {
            var state = comparisonWorld();
            var modules = new ArrayList<>(List.of("mod_fission_thruster", candidate.hold(),
                    ShipComponentCatalog.FISSION_REACTOR_ID, ShipComponentCatalog.REACTOR_TANK_ID));
            for (int tank = 0; tank < candidate.tanks(); tank++) modules.add(PropulsionCatalog.FUEL_TANK_MODULE_ID);
            if (candidate.battery()) modules.add(ShipComponentCatalog.BATTERY_ID);
            int slots = modules.stream().map(ShipComponentCatalog::module).mapToInt(ShipModule::slotCost).sum();
            var design = ShipBlueprintFactory.evaluate(state, new ShipDesignSpecification("tanker-design", candidate.name(),
                    "supplier-owner", ShipRole.CARGO_TRANSPORT, "steel", modules, "steel", .5));
            if (!design.valid()) {
                assertEquals("Three tanks with battery", candidate.name());
                assertTrue(design.errors().stream().anyMatch(error -> error.contains("slots")));
                text.append("| ").append(candidate.name()).append(" | ").append(slots).append(" | Rejected: exceeds hull slots | | | | | | |\n");
                continue;
            }
            state = state.withShipDesigns(state.shipDesigns().stream().map(item -> item.id().equals("tanker-design") ? design.design() : item).toList());
            state = state.withTradeRoutes(state.tradeRoutes().stream().map(item -> item.id().equals("moon-supply")
                    ? new TradeRoute(item.id(), item.name(), item.ownerEntityId(), item.originEntityId(), item.destinationEntityId(),
                    item.materialId(), 50000, 0, 5e6, item.assignedFreighterIds(), 0, false) : item).toList());
            var run = commissioned(state);
            // Physically purchase and stage the first cycle's stock before any candidate quote.
            replenishDepot(run, 210000);
            var quoted = bestTankerPayload(run.state, design.design());
            if (quoted == null) {
                assertTrue(candidate.name().equals("One tank and vault") || candidate.name().equals("Four tanks and compact hold"));
                text.append("| ").append(candidate.name()).append(" | ").append(slots).append(" | No funded grid payload | | | | | | |\n");
                continue;
            }
            int tankerTrips = runComparisonCycles(run, quoted, design.design().fuelCapacityKg());
            double result = 18000 - acquisitionCost(run) - run.chargingCost + retainedFuelValue(run.state);
            double consumed = FUELS.stream().mapToDouble(id -> run.consumed.getOrDefault(id, 0.0) * price(id)).sum();
            assertEquals(12000 - consumed - run.chargingCost, result, 1e-4);
            for (String id : FUELS) assertEquals(stock(run.opening, id) - stock(run.state, id), run.consumed.getOrDefault(id, 0.0), 1e-4, id);
            assertEquals(7500, route(run.state, "lunar").totalVolumeMovedKg(), 1e-5);
            assertEquals(totalCash(run.opening), totalCash(run.state), 1e-4);
            text.append(String.format(Locale.ROOT, "| %s | %d | %.0f | %.0f | %.5f | %d | %d | %d | %.2f |%n",
                    candidate.name(), slots, quoted.hydrolox(), quoted.hydrogen(), quoted.cost() / quoted.hydrolox(),
                    tankerTrips, run.trips, run.state.turn(), result));
            if (candidate.name().equals("Original")) original = result;
            if (candidate.name().equals("Three tanks and large hold")) improved = result;
            accepted++;
        }
        assertTrue(accepted >= 5);
        assertTrue(original < 0);
        assertTrue(improved > original, "Measured larger-tank delivery must improve the controlled baseline.");
        assertTrue(improved > 0, "The best measured design covers consumed operating supplies at these fixture prices.");
        text.append("\nSource production, surface lift, initial nuclear stock lift to 2,000 km, capital and overhead remain excluded. ")
                .append("The grid is bounded and does not prove global optimality. Quotes do not reserve supplies. Every committed flight survives reload; ")
                .append("all actual ticks retain funded electricity, cargo preservation and maneuvers. Nuclear feed remains a paid requirement without a battery.\n");
        Files.writeString(Path.of("target/lunar-tanker-comparison.md"), text.toString());
    }

    Run commissioned(GameState state) {
        var run = new Run(state);
        for (String id : List.of("supplier", "tanker", "trader")) {
            var ship = ship(run.state, id);
            var design = run.state.shipDesigns().stream().filter(item -> item.id().equals(ship.designId())).findFirst().orElseThrow();
            if (design.powerProfile().batteryKwh() == 0) continue;
            String port = id.equals("supplier") ? "low-port" : "depot-port";
            run.state = ShipBatteryCharging.charge(run.state, id, port, 500 / .9);
            assertEquals(500, ship(run.state, id).powerState().batteryChargeKwh(), 1e-5);
            run.chargingCost += 500 / .9 * .02;
        }
        run.state = ShipPowerResupply.buy(run.state, "tanker", "depot-port", "uranium", 100);
        assertEquals(100, ship(run.state, "tanker").powerState().generatorMaterialsKg().get("refined_uranium"));
        return run;
    }

    @Test void measurePaidBackhaulWithLoadedLunarReturnAndLowEarthDelivery() throws Exception {
        var baseline = backhaulRun(0, 3);
        double baseCost = fuelCost(baseline);
        double baseResult = 18000 - acquisitionCost(baseline) - baseline.chargingCost + retainedFuelValue(baseline.state);
        assertEquals(12000 - baseCost - baseline.chargingCost, baseResult, 1e-4);
        assertTrue(baseResult > 0);
        var results = new ArrayList<BackhaulResult>();
        for (double quantity : List.of(500.0, 1250.0, 2500.0)) for (double postedPrice : List.of(1.0, 1.25, 1.5, 2.0, 3.0)) {
            var run = backhaulRun(quantity, postedPrice);
            double margin = 3 * quantity * (postedPrice * IndustryMarketProcessor.WHOLESALE_SHARE - .8);
            double cost = fuelCost(run);
            double result = 18000 + margin - acquisitionCost(run) - run.chargingCost + retainedFuelValue(run.state);
            assertEquals(12000 + margin - cost - run.chargingCost, result, 1e-4);
            double increment = result - baseResult;
            assertEquals(margin - (cost - baseCost), increment, 1e-4);
            if (postedPrice == 1) assertTrue(increment < 0, "Zero cargo margin cannot cover added return and handling fuel.");
            if (postedPrice == 3) assertTrue(increment > 0, "The high-margin fixture must cover its measured added transport.");
            double breakEven = (.8 + (cost - baseCost) / (3 * quantity)) / IndustryMarketProcessor.WHOLESALE_SHARE;
            results.add(new BackhaulResult(quantity, postedPrice, run.state.turn(), run.trips, cost, result, increment, breakEven));
        }
        Files.createDirectories(Path.of("target"));
        var text = new StringBuilder("# Lunar return-cargo audit\n\nThe same improved fission tanker supplies three goods cycles. ")
                .append("Backhaul uses the same hydrolox freighter with 500, 1,250 or 2,500 kg aluminum purchased at the Moon for 0.8 credits/kg. ")
                .append("The Moon starts with a finite 7,500 kg aluminum stock. After the loaded lunar return, the RP-1 supplier makes a paid empty ")
                .append("climb from Earth 500 km to the 2,000 km depot and delivers the aluminum back to 500 km. Depot resales are internal transfers. ")
                .append("Final low-Earth sales pay the existing 80% wholesale share. All cargo is paid and physically transported. ")
                .append("The empty-return baseline uses the same fixtures and completes 34 round trips in 188 days.\n\n")
                .append(String.format(Locale.ROOT, "Baseline consumption result: %.2f credits. Baseline consumed supplies: %.2f credits.\n\n", baseResult, baseCost))
                .append("| Return cargo kg/cycle | Earth posted price | Days | All round trips | Consumed supplies | Chain result | Increment over empty | Break-even Earth posted price |\n")
                .append("|---|---:|---:|---:|---:|---:|---:|---:|\n");
        for (var row : results) text.append(String.format(Locale.ROOT, "| %.0f | %.2f | %d | %d | %.2f | %.2f | %.2f | %.5f |%n",
                row.quantity(), row.postedPrice(), row.days(), row.trips(), row.consumedCost(), row.result(), row.increment(), row.breakEvenPrice()));
        text.append("\nEach loaded lunar return and onward Earth delivery survives reload. Total money, fuel materials and aluminum are conserved. ")
                .append("Missing lunar stock, exhausted depot buyer cash and an insolvent trader reject isolated backhaul previews atomically. ")
                .append("This is explicit contracting, not autonomous backhaul selection. No separate port fee is charged by these parking-port fixtures; ")
                .append("handling propulsion is included. Source production, surface lift, initial nuclear-stock lift, capital and overhead remain excluded.\n");
        Files.writeString(Path.of("target/lunar-return-cargo-report.md"), text.toString());
    }

    private Run backhaulRun(double quantity, double postedPrice) throws Exception {
        var run = commissioned(backhaulWorld(quantity, postedPrice));
        if (quantity == 0) runComparisonCycles(run, new TankerQuote(32000, 17000, 0, 0), 45000);
        else for (int cycle = 1; cycle <= 3; cycle++) {
            double needed = Math.max(0, 90000 - mixtureStock(run.state, "moon-market"));
            replenishDepot(run, needed + 120000 - ship(run.state, "trader").currentFuelKg());
            while (needed > 1e-4) {
                double batch = Math.min(32000, needed);
                dispatchAndReturn(run, "tanker", "moon-supply", "depot-port", tankerCargo(batch, 17000), 45000);
                needed -= batch;
            }
            dispatchAndReturn(run, "supplier", "depot-supply", "low-port", GOODS, 30000);
            resetOneWay(run, "lunar", "depot-market", "moon-market");
            oneWay(run, "trader", "lunar", "depot-port", "moon-port", GOODS, 120000);
            resetOneWay(run, "backhaul", "moon-market", "depot-market");
            if (cycle == 1 && quantity == 2500 && postedPrice == 3) rejectUnfundedBackhaul(run.state, quantity);
            oneWay(run, "trader", "backhaul", "moon-port", "depot-port", Map.of(BACKHAUL, quantity), 120000);
            run.trips++;
            assertTrue(ship(run.state, "trader").currentFuelKg() >= 24000);
            stageHandlingSupplier(run);
            resetOneWay(run, "handling", "depot-market", "low-market");
            oneWay(run, "supplier", "handling", "depot-port", "low-port", Map.of(BACKHAUL, quantity),
                    ship(run.state, "supplier").currentFuelKg());
            run.trips++;
            assertEquals(cycle * quantity, hub(run.state, "low-market").activeOrders().get(BACKHAUL).supplyKg(), 1e-5);
            assertEquals(cycle * quantity, route(run.state, "backhaul").totalVolumeMovedKg(), 1e-5);
            assertEquals(cycle * quantity, route(run.state, "handling").totalVolumeMovedKg(), 1e-5);
            run.state = reload(run.state, "backhaul-cycle-" + cycle);
        }
        assertEquals(7500, route(run.state, "lunar").totalVolumeMovedKg(), 1e-5);
        assertEquals(7500 - quantity * 3, hub(run.state, "moon-market").activeOrders().get(BACKHAUL).supplyKg(), 1e-5);
        assertEquals(7500, stock(run.state, BACKHAUL), 1e-5);
        for (String id : FUELS) assertEquals(stock(run.opening, id) - stock(run.state, id), run.consumed.getOrDefault(id, 0.0), 1e-4, id);
        assertEquals(totalCash(run.opening), totalCash(run.state), 1e-4);
        return run;
    }

    GameState backhaulWorld(double quantity, double postedPrice) throws Exception {
        var state = comparisonWorld();
        var modules = new ArrayList<>(List.of("mod_fission_thruster", "mod_cargo_hold_large",
                ShipComponentCatalog.FISSION_REACTOR_ID, ShipComponentCatalog.REACTOR_TANK_ID));
        for (int tank = 0; tank < 4; tank++) modules.add(PropulsionCatalog.FUEL_TANK_MODULE_ID);
        var design = ShipBlueprintFactory.evaluate(state, new ShipDesignSpecification("tanker-design", "Improved tanker",
                "supplier-owner", ShipRole.CARGO_TRANSPORT, "steel", modules, "steel", .5,
                modules.stream().map(ShipComponentCatalog::module).mapToInt(ShipModule::slotCost).sum()));
        assertTrue(design.valid(), design.errors().toString());
        state = state.withShipDesigns(state.shipDesigns().stream().map(item -> item.id().equals("tanker-design") ? design.design() : item).toList());
        state = state.withCommercialHubs(state.commercialHubs().stream().map(hub -> {
            var orders = new HashMap<>(hub.activeOrders());
            double supply = hub.id().equals("moon-market") ? 7500 : 0;
            double price = hub.id().equals("moon-market") ? .8 : hub.id().equals("low-market") ? postedPrice : 1;
            orders.put(BACKHAUL, new MarketOrder(BACKHAUL, supply, 10000, price, 0));
            return new CommercialHub(hub.id(), hub.entityId(), 0, hub.storageCapacityKg(), hub.currentStoredWeightKg() + supply, 10, orders);
        }).toList());
        var routes = new ArrayList<>(state.tradeRoutes().stream().map(item -> item.id().equals("moon-supply")
                ? new TradeRoute(item.id(), item.name(), item.ownerEntityId(), item.originEntityId(), item.destinationEntityId(),
                item.materialId(), 50000, 0, 5e6, item.assignedFreighterIds(), 0, false)
                : item.id().equals("lunar") && quantity > 0 ? item.withRoaming(true) : item).toList());
        if (quantity > 0) {
            routes.add(route("backhaul", "trader", "trader-owner", "moon-market", "depot-market").withRoaming(true));
            routes.add(route("handling", "supplier", "supplier-owner", "depot-market", "low-market").withRoaming(true));
        }
        return state.withTradeRoutes(routes);
    }

    void resetOneWay(Run run, String id, String origin, String destination) {
        var replacement = route(run.state, id).atNewOrigin(origin).withDestination(destination);
        run.state = run.state.withTradeRoutes(run.state.tradeRoutes().stream().map(item -> item.id().equals(id) ? replacement : item).toList());
    }

    void oneWay(Run run, String shipId, String routeId, String origin, String destination,
                        Map<String, Double> cargo, double tank) throws Exception {
        double before = route(run.state, routeId).totalVolumeMovedKg();
        dispatch(run, shipId, routeId, origin, cargo, tank, false);
        int days = 0;
        while (!(fleet(run.state, shipId).location().isAt(FleetLocation.Site.docked(destination))
                && TradeRoute.LOADING.equals(route(run.state, routeId).phase())) && days++ < 30) day(run);
        assertTrue(days < 30, routeId + " must complete its paid one-way delivery.");
        assertEquals(before + cargo.values().stream().mapToDouble(Double::doubleValue).sum(), route(run.state, routeId).totalVolumeMovedKg(), 1e-5);
        assertEquals(0, route(run.state, routeId).onboardKg());
        run.state = reload(run.state, routeId + "-arrival-" + run.state.turn());
    }

    private void stageHandlingSupplier(Run run) throws Exception {
        assertTrue(fleet(run.state, "supplier").location().isAt(FleetLocation.Site.docked("low-port")));
        run.state = ShipFueling.refuel(run.state, "supplier", "low-port", 30000 - ship(run.state, "supplier").currentFuelKg());
        assertEquals(30000, ship(run.state, "supplier").currentFuelKg(), 1e-5);
        var destination = FleetLocation.Site.docked("depot-port");
        assertTrue(TradeLegReadiness.inspect(run.state, fleet(run.state, "supplier"), hub(run.state, "depot-market")).ready());
        var plan = TradeLocalPlan.plan(run.state, fleet(run.state, "supplier"), destination);
        assertNotNull(plan);
        var departed = LocalTravel.depart(fleet(run.state, "supplier"), destination, plan);
        run.state = run.state.withFleets(run.state.fleets().stream().map(item -> item.id().equals("supplier") ? departed : item).toList());
        run.state = reload(run.state, "empty-handling-climb-" + run.state.turn());
        int days = 0;
        while (!fleet(run.state, "supplier").location().isAt(destination) && days++ < 10) day(run);
        assertTrue(days < 10);
    }

    private void rejectUnfundedBackhaul(GameState state, double quantity) {
        var funded = ShipFueling.refuel(state, "trader", "moon-port", 120000 - ship(state, "trader").currentFuelKg());
        var processor = new LogisticsProcessor();
        var cargo = Map.of(BACKHAUL, quantity);
        assertNotNull(processor.previewBasket(funded, route(funded, "backhaul"), cargo));
        var moon = hub(funded, "moon-market");
        var orders = new HashMap<>(moon.activeOrders());
        orders.put(BACKHAUL, new MarketOrder(BACKHAUL, 0, 10000, .8, 0));
        var empty = funded.withCommercialHubs(funded.commercialHubs().stream().map(item -> item.id().equals(moon.id())
                ? new CommercialHub(moon.id(), moon.entityId(), 0, moon.storageCapacityKg(),
                moon.currentStoredWeightKg() - moon.activeOrders().get(BACKHAUL).supplyKg(), 10, orders) : item).toList());
        var noBuyer = funded.withMarketAccounts(funded.marketAccounts().stream().map(item -> item.hubId().equals("depot-market")
                ? new MarketAccount(item.hubId(), 0) : item).toList());
        var noOwner = funded.withEmpires(funded.empires().stream().map(item -> item.id().equals("trader-owner")
                ? new Empire(item.id(), item.name(), item.raceId(), item.societyStructure(), 0, 0, List.of(), List.of(), Map.of(),
                item.unlockedTechIds(), List.of()) : item).toList());
        for (var rejected : List.of(empty, noBuyer, noOwner)) {
            var markets = rejected.commercialHubs(); var fleets = rejected.fleets(); double cash = totalCash(rejected);
            assertNull(processor.previewBasket(rejected, route(rejected, "backhaul"), cargo));
            assertEquals(markets, rejected.commercialHubs()); assertEquals(fleets, rejected.fleets());
            assertEquals(cash, totalCash(rejected));
        }
    }

    private static double fuelCost(Run run) {
        return FUELS.stream().mapToDouble(id -> run.consumed.getOrDefault(id, 0.0) * price(id)).sum();
    }

    @Test void autonomousBackhaulSelectionMatchesPaidDepotDelivery() throws Exception {
        var state = backhaulWorld(2500, 3);
        state = withAluminumPrice(state, "depot-market", 1.25);
        var run = commissioned(state.withTradeRoutes(state.tradeRoutes().stream()
                .filter(item -> !List.of("backhaul", "handling").contains(item.id())).toList()));
        replenishDepot(run, 330000); // Leave a finite full refill for the trader's next departure from the depot.
        for (double batch : List.of(32000.0, 32000.0, 26000.0))
            dispatchAndReturn(run, "tanker", "moon-supply", "depot-port", tankerCargo(batch, 17000), 45000);
        dispatchAndReturn(run, "supplier", "depot-supply", "low-port", GOODS, 30000);
        oneWay(run, "trader", "lunar", "depot-port", "moon-port", GOODS, 120000);
        var before = run.state;
        var route = route(before, "lunar").withActive(true);
        var selected = RoamingTradePlanner.choose(before, route, fleet(before, "trader"));
        assertNotNull(selected.shipment(), selected.explanation());
        assertEquals("depot-market", selected.shipment().route().destinationEntityId());
        assertEquals(2500, selected.shipment().route().onboardKg(), 1e-5);
        assertEquals(Map.of(BACKHAUL, new TradeCargo(2500, 2000)), selected.shipment().route().cargoManifest());
        assertEquals(before, run.state, "Selection previews must not commit purchases.");
        run.state = before.withTradeRoutes(before.tradeRoutes().stream().map(item -> item.id().equals("lunar") ? route : item).toList());
        run.state = new LogisticsProcessor().processTradeRoutes(run.state).state();
        assertEquals("depot-market", route(run.state, "lunar").destinationEntityId());
        assertTrue(fleet(run.state, "trader").location().inTransit());
        run.state = run.state.withTradeRoutes(run.state.tradeRoutes().stream().map(item -> item.id().equals("lunar") ? item.withActive(false) : item).toList());
        run.state = reload(run.state, "autonomous-backhaul-departure");
        double costBefore = fuelCost(run);
        int days = 0;
        while (!(fleet(run.state, "trader").location().isAt(FleetLocation.Site.docked("depot-port"))
                && TradeRoute.LOADING.equals(route(run.state, "lunar").phase())) && days++ < 30) day(run);
        assertTrue(days < 30);
        assertEquals(2500, hub(run.state, "depot-market").activeOrders().get(BACKHAUL).supplyKg(), 1e-5);
        assertEquals(5000, route(run.state, "lunar").totalVolumeMovedKg(), 1e-5);
        assertTrue(ship(run.state, "trader").currentFuelKg() >= 24000);
        double consumed = fuelCost(run) - costBefore;
        double actualProfit = 500 - consumed;
        assertEquals(selected.profitCredits(), actualProfit, .02);
        run.state = reload(run.state, "autonomous-backhaul-arrival");
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/autonomous-backhaul-report.md"), String.format(Locale.ROOT,
                "# Autonomous backhaul audit%n%nSelected %s; %.0f kg; quote profit %.5f; actual voyage profit %.5f; consumed supplies %.5f; days %d.%n",
                selected.shipment().route().destinationEntityId(), selected.shipment().route().onboardKg(), selected.profitCredits(), actualProfit, consumed, days));
    }

    GameState withAluminumPrice(GameState state, String hubId, double price) {
        return state.withCommercialHubs(state.commercialHubs().stream().map(hub -> {
            if (!hub.id().equals(hubId)) return hub;
            var orders = new HashMap<>(hub.activeOrders());
            var order = orders.get(BACKHAUL);
            orders.put(BACKHAUL, new MarketOrder(BACKHAUL, order.supplyKg(), order.demandKg(), price, order.shortcomingScore()));
            return hub.withInventory(state.turn(), hub.currentStoredWeightKg(), orders);
        }).toList());
    }

    @Test void autonomousBackhaulAndHandlingCompleteThreeConservedCycles() throws Exception {
        var state = withAluminumPrice(backhaulWorld(2500, 3), "depot-market", 1.25);
        state = state.withTradeRoutes(state.tradeRoutes().stream().filter(item -> !item.id().equals("backhaul"))
                .map(item -> item.id().equals("handling") ? item.atNewOrigin("low-market") : item).toList());
        var run = commissioned(state);
        var text = new StringBuilder("# Autonomous return-cargo chain\n\nMoon aluminum costs 0.8 credits/kg, the Earth depot posts 1.25 ")
                .append("and Earth 500 km posts 3. Every autonomous decision uses current stock and paid next-leg or staging previews. ")
                .append("Outbound goods and lunar fuel supply remain explicitly dispatched. Only the lunar return and Earth handling service are autonomous.\n\n")
                .append("| Cycle | Day | Lunar return quote profit | Earth handling quote profit | Cumulative low-Earth aluminum kg | Chain consumption result |\n")
                .append("|---|---:|---:|---:|---:|---:|\n");
        String probes = "";
        for (int cycle = 1; cycle <= 3; cycle++) {
            double needed = Math.max(0, 90000 - mixtureStock(run.state, "moon-market"));
            replenishDepot(run, needed + 240000 - ship(run.state, "trader").currentFuelKg());
            while (needed > 1e-4) {
                double batch = Math.min(32000, needed);
                dispatchAndReturn(run, "tanker", "moon-supply", "depot-port", tankerCargo(batch, 17000), 45000);
                needed -= batch;
            }
            dispatchAndReturn(run, "supplier", "depot-supply", "low-port", GOODS, 30000);
            resetOneWay(run, "lunar", "depot-market", "moon-market");
            oneWay(run, "trader", "lunar", "depot-port", "moon-port", GOODS, 120000);
            if (cycle == 1) probes = autonomousPriceChecks(run.state);
            var lunar = RoamingTradePlanner.choose(run.state, route(run.state, "lunar").withActive(true), fleet(run.state, "trader"));
            assertNotNull(lunar.shipment());
            assertEquals("depot-market", lunar.shipment().route().destinationEntityId());
            autonomousDelivery(run, "lunar", "trader", "depot-port", false);
            assertTrue(ship(run.state, "trader").currentFuelKg() >= 24000);
            resetOneWay(run, "handling", "low-market", "depot-market");
            var noEarthBuyer = run.state.withMarketAccounts(run.state.marketAccounts().stream().map(item -> item.hubId().equals("low-market")
                    ? new MarketAccount(item.hubId(), 0) : item).toList());
            assertNull(IntermediateTradePlanner.choose(noEarthBuyer, route(noEarthBuyer, "handling").withActive(true), fleet(noEarthBuyer, "supplier")));
            var staged = IntermediateTradePlanner.choose(run.state, route(run.state, "handling").withActive(true), fleet(run.state, "supplier"));
            assertNotNull(staged, "The handler must quote both the paid empty climb and loaded low-Earth delivery.");
            assertTrue(staged.profitCredits() > 0);
            assertEquals(TradeRoute.REPOSITIONING, staged.route().phase());
            double beforeCost = fuelCost(run);
            autonomousDelivery(run, "handling", "supplier", "low-port", true);
            double handlingProfit = 2500 * (2.4 - 1.25) - (fuelCost(run) - beforeCost);
            assertEquals(staged.profitCredits(), handlingProfit, .02);
            assertEquals(cycle * 2500, hub(run.state, "low-market").activeOrders().get(BACKHAUL).supplyKg(), 1e-5);
            assertEquals(0, hub(run.state, "depot-market").activeOrders().get(BACKHAUL).supplyKg(), 1e-5);
            run.state = reload(run.state, "autonomous-chain-cycle-" + cycle);
            double margin = cycle * 8000.0;
            double result = margin - fuelCost(run) - run.chargingCost;
            text.append(String.format(Locale.ROOT, "| %d | %d | %.5f | %.5f | %d | %.2f |%n", cycle, run.state.turn(),
                    lunar.profitCredits(), staged.profitCredits(), cycle * 2500, result));
        }
        assertEquals(15000, route(run.state, "lunar").totalVolumeMovedKg(), 1e-5);
        assertEquals(7500, route(run.state, "handling").totalVolumeMovedKg(), 1e-5);
        assertEquals(0, hub(run.state, "moon-market").activeOrders().get(BACKHAUL).supplyKg(), 1e-5);
        assertEquals(7500, stock(run.state, BACKHAUL), 1e-5);
        for (String id : FUELS) assertEquals(stock(run.opening, id) - stock(run.state, id), run.consumed.getOrDefault(id, 0.0), 1e-4, id);
        double result = 36000 - acquisitionCost(run) - run.chargingCost + retainedFuelValue(run.state);
        assertEquals(24000 - fuelCost(run) - run.chargingCost, result, 1e-4);
        assertTrue(result > 0);
        text.append(probes).append("\nAll actual ticks preserve fuel, money, cargo and electricity. Every selected return, empty handling climb and loaded handling ")
                .append("leg survives reload. A selected route is deactivated after loaded departure to bound the experiment; its committed shipment still completes. ")
                .append("No downstream purchase or sale is reserved by a lunar quote. Each handler rechecks live depot markets on actual staging arrival. ")
                .append("Source production, surface lift, capital, overhead and autonomous upstream supply remain excluded.\n");
        Files.writeString(Path.of("target/autonomous-backhaul-chain-report.md"), text.toString());
    }

    private void autonomousDelivery(Run run, String routeId, String shipId, String target, boolean staging) throws Exception {
        var active = route(run.state, routeId).withActive(true);
        run.state = run.state.withTradeRoutes(run.state.tradeRoutes().stream().map(item -> item.id().equals(routeId) ? active : item).toList());
        run.state = new LogisticsProcessor().processTradeRoutes(run.state).state();
        assertEquals(staging ? TradeRoute.REPOSITIONING : TradeRoute.DELIVERING, route(run.state, routeId).phase());
        assertTrue(fleet(run.state, shipId).location().inTransit());
        run.state = reload(run.state, routeId + "-autonomous-start-" + run.state.turn());
        int days = 0;
        boolean loadedReload = false;
        while (!(fleet(run.state, shipId).location().isAt(FleetLocation.Site.docked(target))
                && TradeRoute.LOADING.equals(route(run.state, routeId).phase())) && days++ < 30) {
            if (TradeRoute.DELIVERING.equals(route(run.state, routeId).phase())) {
                var stopped = route(run.state, routeId).withActive(false);
                run.state = run.state.withTradeRoutes(run.state.tradeRoutes().stream().map(item -> item.id().equals(routeId) ? stopped : item).toList());
                if (!loadedReload) {
                    run.state = reload(run.state, routeId + "-autonomous-loaded-" + run.state.turn());
                    loadedReload = true;
                }
            }
            day(run);
        }
        assertTrue(days < 30, "Autonomous delivery must complete: " + route(run.state, routeId).status());
        assertTrue(loadedReload);
        assertEquals(0, route(run.state, routeId).onboardKg());
    }

    private String autonomousPriceChecks(GameState state) {
        var text = new StringBuilder("\nAutonomous choices at the first lunar arrival (isolated price probes):\n\n")
                .append("| Shipment cap kg | Depot posted price | Selected port | Quote profit |\n|---|---:|---|---:|\n");
        for (double cap : List.of(500.0, 1250.0, 2500.0)) for (double price : List.of(1.0, 1.25, 1.5, 2.0, 3.0)) {
            var probe = withAluminumPrice(state, "depot-market", price);
            var route = new TradeRoute("probe", "Probe", "trader-owner", "moon-market", "depot-market", BACKHAUL,
                    cap, 0, 5e6, List.of("trader"), 0, true).withRoaming(true);
            var selection = RoamingTradePlanner.choose(probe, route, fleet(probe, "trader"));
            if (price == 1 || cap == 500 && price == 1.25) assertNull(selection.shipment());
            if (cap == 2500 && price == 1.25) assertNotNull(selection.shipment());
            text.append(String.format(Locale.ROOT, "| %.0f | %.2f | %s | %.5f |%n", cap, price,
                    selection.shipment() == null ? "Waiting" : selection.shipment().route().destinationEntityId(), selection.profitCredits()));
        }
        var active = route(state, "lunar").withActive(true);
        var selected = RoamingTradePlanner.choose(state, active, fleet(state, "trader"));
        assertNotNull(selected.shipment());
        var direct = TradeLegReadiness.inspect(selected.shipment().state(), selected.shipment().preparedFleet(), hub(state, "low-market"));
        assertFalse(direct.ready());
        text.append("\nDirect loaded Moon-to-Earth-500 km readiness: ").append(direct.explanation()).append("\n\n");
        for (String hubId : List.of("depot-market", "low-market")) {
            var noCash = state.withMarketAccounts(state.marketAccounts().stream().map(item -> item.hubId().equals(hubId)
                    ? new MarketAccount(item.hubId(), 0) : item).toList());
            var choice = RoamingTradePlanner.choose(noCash, active, fleet(noCash, "trader"));
            if (hubId.equals("depot-market")) assertNull(choice.shipment());
            else assertNotNull(choice.shipment(), "A lunar sale does not promise a separate handler's future buyer cash.");
        }
        assertEquals(0, route(state, "lunar").onboardKg());
        return text.toString();
    }

    private GameState comparisonWorld() throws Exception {
        var state = world();
        var depot = hub(state, "depot-market");
        var orders = new HashMap<>(depot.activeOrders());
        orders.put("hydrogen_gas", new MarketOrder("hydrogen_gas", 10000000, 5e6, FUEL_PRICE, 0));
        orders.put("refined_uranium", new MarketOrder("refined_uranium", 20000, 5e6, 1, 0));
        var stocked = new CommercialHub(depot.id(), depot.entityId(), 0, 12e6, 10020000, 10, orders);
        return state.withCommercialHubs(state.commercialHubs().stream().map(item -> item.id().equals(depot.id()) ? stocked : item).toList());
    }

    private void replenishDepot(Run run, double needed) throws Exception {
        double remaining = Math.max(0, needed - mixtureStock(run.state, "depot-market"));
        while (remaining > 1e-4) {
            double batch = Math.min(30000, remaining);
            dispatchAndReturn(run, "supplier", "depot-supply", "low-port",
                    PropulsionCatalog.drive("mod_hydrolox_rocket").propellantMaterials(batch), 30000);
            remaining -= batch;
        }
    }

    private int runComparisonCycles(Run run, TankerQuote quote, double capacity) throws Exception {
        int trips = 0;
        for (int cycle = 1; cycle <= 3; cycle++) {
            double needed = Math.max(0, 90000 - mixtureStock(run.state, "moon-market"));
            replenishDepot(run, needed + 120000 - ship(run.state, "trader").currentFuelKg());
            while (needed > 1e-4) {
                double hydrolox = Math.min(quote.hydrolox(), needed);
                dispatchAndReturn(run, "tanker", "moon-supply", "depot-port", tankerCargo(hydrolox, quote.hydrogen()), capacity);
                needed -= hydrolox; trips++;
            }
            dispatchAndReturn(run, "supplier", "depot-supply", "low-port", GOODS, 30000);
            dispatchAndReturn(run, "trader", "lunar", "depot-port", GOODS, 120000);
            assertEquals(2500 * cycle, route(run.state, "lunar").totalVolumeMovedKg(), 1e-5);
            assertTrue(ship(run.state, "trader").currentFuelKg() >= 24000);
            run.state = reload(run.state, "comparison-cycle-" + cycle);
        }
        return trips;
    }

    private TankerQuote bestTankerPayload(GameState state, ShipDesign design) {
        state = ShipFueling.refuel(state, "tanker", "depot-port", design.fuelCapacityKg());
        assertEquals(design.fuelCapacityKg(), ship(state, "tanker").currentFuelKg(), 1e-5);
        var before = state;
        TankerQuote best = null;
        for (int hydrolox = 1000; hydrolox <= design.maxCargoMassKg(); hydrolox += 1000) {
            for (int hydrogen = 0; hydrogen <= 30000 && hydrolox + hydrogen + 50 <= design.maxCargoMassKg() - design.fuelCapacityKg() * .001; hydrogen += 1000) {
                var quote = quoteTanker(before, hydrolox, hydrogen);
                if (quote == null) continue;
                if (best == null || quote.cost() / quote.hydrolox() < best.cost() / best.hydrolox()) best = quote;
                break;
            }
        }
        assertEquals(before, state, "Isolated lookahead must not commit candidate purchases.");
        return best;
    }

    private Map<String, Double> tankerCargo(double hydrolox, double hydrogen) {
        var cargo = new HashMap<>(PropulsionCatalog.drive("mod_hydrolox_rocket").propellantMaterials(hydrolox));
        if (hydrogen > 0) cargo.put("hydrogen_gas", hydrogen);
        cargo.put("refined_uranium", 50.0);
        return cargo;
    }

    private TankerQuote quoteTanker(GameState state, double hydrolox, double hydrogen) {
        var shipment = new LogisticsProcessor().previewBasket(state, route(state, "moon-supply"), tankerCargo(hydrolox, hydrogen), true);
        if (shipment == null) return null;
        var site = FleetLocation.Site.docked("moon-port");
        var outward = TradeLocalPlan.plan(shipment.state(), shipment.preparedFleet(), site);
        if (outward == null) return null;
        var arrival = TradeReturnFuel.arrival(shipment.state(), shipment.preparedFleet(), hub(shipment.state(), "moon-market"));
        if (arrival == null) return null;
        var projected = shipment.state().withFleets(shipment.state().fleets().stream()
                .map(item -> item.id().equals("tanker") ? arrival : item).toList())
                .withTurn(state.turn() + (long) Math.ceil(outward.days()))
                .withTradeRoutes(List.of(shipment.route().withRoaming(true).withActive(false)));
        projected = new LogisticsProcessor().processTradeRoutes(projected).state();
        assertEquals(0, route(projected, "moon-supply").onboardKg(), 1e-5);
        var funded = TradeLegReadiness.prepare(projected, fleet(projected, "tanker"), hub(projected, "depot-market"));
        if (!TradeLegReadiness.inspect(funded, fleet(funded, "tanker"), hub(funded, "depot-market")).ready()) return null;
        var returning = TradeLocalPlan.plan(funded, fleet(funded, "tanker"), FleetLocation.Site.docked("depot-port"));
        if (returning == null) return null;
        var returned = LocalTravel.arrivalPreview(funded, fleet(funded, "tanker"), FleetLocation.Site.docked("depot-port"), returning);
        var end = funded.withFleets(funded.fleets().stream().map(item -> item.id().equals("tanker") ? returned : item).toList());
        double cost = FUELS.stream().mapToDouble(id -> (stock(state, id) - stock(end, id)) * price(id)).sum();
        assertTrue(cost > 0);
        assertEquals(totalCash(state), totalCash(end), 1e-4);
        return new TankerQuote(hydrolox, hydrogen, cost, Math.ceil(outward.days()) + Math.ceil(returning.days()));
    }

    private void dispatch(Run run, String shipId, String routeId, String origin,
                          Map<String, Double> cargo, double capacity) throws Exception {
        dispatch(run, shipId, routeId, origin, cargo, capacity, true);
    }
    private void dispatch(Run run, String shipId, String routeId, String origin,
                          Map<String, Double> cargo, double capacity, boolean secureReturn) throws Exception {
        capacity = FleetSupplySimulation.design(run.state, ship(run.state, shipId)).fuelCapacityKg();
        assertTrue(fleet(run.state, shipId).location().isAt(FleetLocation.Site.docked(origin)));
        double needed = capacity - ship(run.state, shipId).currentFuelKg();
        if (needed > 1e-5) run.state = ShipFueling.refuel(run.state, shipId, origin, needed);
        assertEquals(capacity, ship(run.state, shipId).currentFuelKg(), 1e-5);
        var shipment = new LogisticsProcessor().previewBasket(run.state, route(run.state, routeId), cargo, secureReturn);
        // A paid orbital itinerary may be unavailable at this phase. Wait real engine days rather
        // than assuming every repeated departure has the same rendezvous geometry.
        for (int wait = 0; shipment == null && secureReturn && wait < 7; wait++) {
            day(run);
            shipment = new LogisticsProcessor().previewBasket(run.state, route(run.state, routeId), cargo, true);
        }
        if (shipment == null) {
            var original = ship(run.state, shipId);
            var loaded = new HashMap<>(original.storedCargoKg());
            cargo.forEach((id, kg) -> loaded.merge(id, kg, Double::sum));
            var diagnostic = ShipPowerResupply.replace(run.state, new ShipInstance(original.id(), original.designId(), original.ownerEntityId(),
                    original.currentHullHealth(), original.currentShieldHealth(), original.currentFuelKg(), loaded,
                    original.passengerCount(), original.passengerRaceId(), original.transitMode(), original.powerState(), original.supplyState()));
            fail(shipId + " at day " + run.state.turn() + " cargo " + cargo + " loaded leg: " + TradeLegReadiness.inspect(diagnostic,
                    fleet(diagnostic, shipId), hub(diagnostic, route(diagnostic, routeId).destinationEntityId())));
        }
        var accepted = shipment;
        run.state = accepted.state().withTradeRoutes(accepted.state().tradeRoutes().stream()
                .map(item -> item.id().equals(routeId) ? accepted.route().withActive(false) : item).toList());
        run.state = reload(run.state, "flight-" + run.trips);
    }

    void day(Run run) {
        day(run, (before, after) -> {});
    }

    void day(Run run, java.util.function.BiConsumer<GameState, GameState> logisticsObserver) {
        var before = run.state;
        var powered = ShipPowerProcessor.advanceDay(before);
        var moved = before.withFleets(new FleetProcessor().processFleetMovements(powered, before.orbitalStations(), List.of()))
                .withTurn(before.turn() + 1);
        for (String material : FUELS) {
            double burned = stock(before, material) - stock(moved, material);
            assertTrue(burned >= -1e-5, material);
            run.consumed.merge(material, burned, Double::sum);
        }
        for (var fleet : moved.fleets()) for (var ship : fleet.ships()) {
            assertEquals(0, ship.powerState().lastUnmetEssentialKwh(), 1e-5, ship.id());
            assertEquals(0, ship.powerState().unmetCargoKwh(), 1e-5, ship.id());
            assertTrue(ship.powerState().cargoPreservation().lostKgToday().values().stream().allMatch(kg -> kg == 0));
            assertFalse(fleet.location().orbitalFlight() != null && fleet.location().orbitalFlight().failed(), fleet.id());
        }
        run.state = new LogisticsProcessor().processTradeRoutes(moved).state();
        logisticsObserver.accept(moved, run.state);
        assertEquals(totalCash(run.opening), totalCash(run.state), 1e-4);
        assertTrue(run.state.marketAccounts().stream().allMatch(item -> item.unsettledSalesCredits() >= 0));
    }

    private GameState world() throws Exception {
        var tech = List.of("rocketry", "spacecraft", "large_hull_engineering", "hydrolox_propulsion", "electricity", "solar_power", "industrial_production",
                "space_stations", "nuclear_fission");
        var owners = List.of(owner("supplier-owner", tech), owner("trader-owner", tech), owner("provider", tech));
        var low = new HashMap<String, Double>(PropulsionCatalog.drive("mod_chemical_rocket").propellantMaterials(4000000));
        PropulsionCatalog.drive("mod_hydrolox_rocket").propellantMaterials(990000).forEach((id, kg) -> low.merge(id, kg, Double::sum));
        GOODS.forEach((id, kg) -> low.put(id, kg * 3));
        var depot = new HashMap<String, Double>();
        depot.put("hydrogen_gas", 10000000.0); depot.put("refined_uranium", 20000.0);
        var state = GameState.builder().solarSystems(DataModelLoader.loadSolarSystems()).empires(owners)
                .orbitalStations(List.of(port("low-port", "earth", 500), port("depot-port", "earth", 2000), port("moon-port", "moon", 500)))
                .commercialHubs(List.of(market("low-market", "low-port", low, 0),
                        market("depot-market", "depot-port", depot, 1), market("moon-market", "moon-port", Map.of(), 3)))
                .marketAccounts(List.of(new MarketAccount("low-market", 1e6), new MarketAccount("depot-market", 1e6),
                        new MarketAccount("moon-market", 1e6)))
                .powerGrids(List.of(new PowerGridState("low-port", 1000, 0, 1000, 10000, 10000, false),
                        new PowerGridState("depot-port", 1000, 0, 1000, 10000, 10000, false))).build();
        var supplierModules = List.of("mod_chemical_rocket", ChemicalFreighterCatalog.MAIN_TANK,
                "mod_cargo_vault", ShipComponentCatalog.SOLAR_ARRAY_ID, ShipComponentCatalog.BATTERY_ID);
        var tankerModules = new ArrayList<>(ShipComponentCatalog.workbenchModules("mod_fission_thruster", false, ShipComponentCatalog.FISSION_REACTOR_ID));
        tankerModules.add(PropulsionCatalog.FUEL_TANK_MODULE_ID);
        tankerModules.add(PropulsionCatalog.FUEL_TANK_MODULE_ID);
        tankerModules.add(PropulsionCatalog.FUEL_TANK_MODULE_ID);
        var designs = new ArrayList<ShipDesign>();
        var specifications = List.of(new ShipDesignSpecification("supplier-design", "Supplier", "supplier-owner", ShipRole.CARGO_TRANSPORT,
                        "steel", supplierModules, "steel", .5),
                new ShipDesignSpecification("tanker-design", "Lunar tanker", "supplier-owner", ShipRole.CARGO_TRANSPORT,
                        "steel", tankerModules, "steel", .5),
                new ShipDesignSpecification("trader-design", "Lunar freighter", "trader-owner", ShipRole.CARGO_TRANSPORT,
                        "steel", ChemicalFreighterCatalog.modules("mod_hydrolox_rocket"), "steel", .5));
        for (var specification : specifications) {
            var fitted = new ShipDesignSpecification(specification.id(), specification.name(), specification.ownerEntityId(), specification.role(),
                    specification.hullMaterialId(), specification.moduleIds(), specification.armorMaterialId(), specification.armorThicknessCm(),
                    specification.moduleIds().stream().map(ShipComponentCatalog::module).mapToInt(ShipModule::slotCost).sum());
            var evaluation = ShipBlueprintFactory.evaluate(state, fitted);
            assertTrue(evaluation.valid(), evaluation.errors().toString()); designs.add(evaluation.design());
        }
        return state.withShipDesigns(designs).withFleets(List.of(fleet("supplier", "supplier-owner", "low-port", .05),
                        fleet("tanker", "supplier-owner", "depot-port", .05), fleet("trader", "trader-owner", "depot-port", .20)))
                .withTradeRoutes(List.of(route("depot-supply", "supplier", "supplier-owner", "low-market", "depot-market"),
                        route("moon-supply", "tanker", "supplier-owner", "depot-market", "moon-market"),
                        route("lunar", "trader", "trader-owner", "depot-market", "moon-market")));
    }

    private CommercialHub market(String id, String port, Map<String, Double> opening, double goodsPrice) {
        var orders = new HashMap<String, MarketOrder>();
        for (String material : FUELS) orders.put(material, new MarketOrder(material, opening.getOrDefault(material, 0.0), 5e6, price(material), 0));
        GOODS.forEach((material, kg) -> orders.put(material, new MarketOrder(material, opening.getOrDefault(material, 0.0),
                10000, goodsPrice == 0 ? .8 : goodsPrice, 0)));
        return new CommercialHub(id, port, 0, id.equals("depot-market") ? 12e6 : 8e6,
                opening.values().stream().mapToDouble(Double::doubleValue).sum(), 10, orders);
    }
    private Empire owner(String id, List<String> tech) {
        return new Empire(id, id, "human", "Individualist", 1e6, 0, List.of(), List.of(), Map.of(), tech, List.of());
    }
    private Fleet fleet(String id, String owner, String port, double reserve) {
        var ship = new ShipInstance(id, id + "-design", owner, 100, 0, 0, Map.of()).withPowerState(ShipPowerState.empty());
        return new Fleet(id, id, owner, "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship), FleetLocation.at(FleetLocation.Site.docked(port)))
                .withFuelPolicy(new FleetFuelPolicy(reserve, reserve, reserve));
    }
    private TradeRoute route(String id, String ship, String owner, String origin, String destination) {
        return new TradeRoute(id, id, owner, origin, destination, "steel", 30000, 0, 5e6, List.of(ship), 0, false);
    }
    private OrbitalStation port(String id, String body, double altitude) {
        var yard = new StationModule("yard-" + id, "Yard", StationModule.TYPE_CAPITAL_SLIPWAY, 4, 10000, 10, 0, Map.of(), "engineer", 0, true);
        return new OrbitalStation(id, id, "sol", body, "provider", OrbitalStation.OWNERSHIP_PUBLIC_STATE,
                20, List.of(yard), Map.of(), 100, 10, 0, 0, 100, 100, "steel", 0, true).withParkingAltitudeKm(altitude);
    }
    GameState reload(GameState state, String name) throws Exception {
        var manager = new SaveGameManager(directory); var file = directory.resolve(name + ".scsave").toFile();
        manager.save(file, state, 1, "2027-01-01T08:00:00");
        var restored = manager.load(file).toGameState(state.turn(), "RUNNING");
        assertEquals(state.fleets(), restored.fleets()); assertEquals(state.tradeRoutes(), restored.tradeRoutes());
        assertEquals(state.commercialHubs(), restored.commercialHubs()); assertEquals(state.marketAccounts(), restored.marketAccounts());
        assertEquals(state.empires(), restored.empires()); assertEquals(state.powerGrids(), restored.powerGrids());
        return restored;
    }
    static TradeRoute route(GameState state, String id) { return state.tradeRoutes().stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow(); }
    static Fleet fleet(GameState state, String id) { return state.fleets().stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow(); }
    static ShipInstance ship(GameState state, String id) { return fleet(state, id).ships().getFirst(); }
    static CommercialHub hub(GameState state, String id) { return state.commercialHubs().stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow(); }
    static double totalCash(GameState state) { return state.empires().stream().mapToDouble(Empire::treasuryCredits).sum()
            + state.marketAccounts().stream().mapToDouble(MarketAccount::unsettledSalesCredits).sum(); }
    private static double price(String id) { return id.equals("refined_uranium") ? 1 : FUEL_PRICE; }
    private static double mixtureStock(GameState state, String hub) { return List.of("liquid_hydrogen", "liquid_oxygen").stream()
            .mapToDouble(id -> hub(state, hub).activeOrders().get(id).supplyKg()).sum(); }
    static double stock(GameState state, String material) {
        double market = state.commercialHubs().stream().mapToDouble(hub -> hub.activeOrders().get(material).supplyKg()
                + hub.reservedTradeFuel().values().stream().mapToDouble(fuel -> fuel.materialsKg().getOrDefault(material, 0.0)).sum()).sum();
        double ships = state.fleets().stream().flatMap(fleet -> fleet.ships().stream()).mapToDouble(ship -> {
            var design = state.shipDesigns().stream().filter(item -> item.id().equals(ship.designId())).findFirst().orElseThrow();
            var drive = PropulsionCatalog.mainDrive(design.equippedModuleIds());
            return drive.propellantMaterials(ship.currentFuelKg()).getOrDefault(material, 0.0)
                    + ship.storedCargoKg().getOrDefault(material, 0.0)
                    + ship.powerState().generatorMaterialsKg().getOrDefault(material, 0.0);
        }).sum();
        return market + ships;
    }
    private static double acquisitionCost(Run run) {
        double result = .8 * route(run.state, "lunar").totalVolumeMovedKg();
        for (String material : FUELS) for (String source : List.of("low-market", "depot-market")) {
            if (source.equals("depot-market") && !List.of("hydrogen_gas", "refined_uranium").contains(material)) continue;
            result += (hub(run.opening, source).activeOrders().get(material).supplyKg()
                    - hub(run.state, source).activeOrders().get(material).supplyKg()) * price(material);
        }
        return result;
    }
    private static double retainedFuelValue(GameState state) {
        double result = 0;
        for (String material : FUELS) {
            double retained = stock(state, material) - hub(state, "low-market").activeOrders().get(material).supplyKg();
            if (List.of("hydrogen_gas", "refined_uranium").contains(material))
                retained -= hub(state, "depot-market").activeOrders().get(material).supplyKg();
            result += retained * price(material);
        }
        return result;
    }
    private String report(Run run) {
        var text = new StringBuilder("# Lunar round-trip audit\n\nThree cycles use the same hydrolox freighter, RP-1 depot supplier and fission lunar tanker. "
                + "All ships return physically. The Moon starts with no fuel. Each tanker delivery carries 3,000 kg hydrolox, "
                + "17,000 kg hydrogen for its own return and 50 kg uranium. Thirty tanker round trips precede each freight cycle. "
                + "Finite hydrogen and uranium stocks start at Earth 2,000 km; hydrolox and goods start at Earth 500 km. "
                + "Source production, surface lift, nuclear-supply lift to 2,000 km, capital and overhead are excluded. "
                + "Contracts are explicitly dispatched through paid shipment previews; returns and refueling use route processing.\n\n"
                + "Source propellant costs 0.005 credits/kg, uranium costs 1 and goods cost 0.8. Moon goods pay 2.4 credits/kg "
                + "after the existing wholesale share. Fuel resales are internal transfers. Retained inventory is valued at source cost.\n\n"
                + "| Cycle | Day | Completed round trips | Consumed fuel cost | Acquired inputs | Retained fuel value | Consumption result |\n"
                + "|---|---:|---:|---:|---:|---:|---:|\n" + String.join("\n", run.rows)
                + "\n\nEvery flight survives save/load. Total money and per-material fuel conservation are checked. "
                + "Electricity and cargo preservation remain funded throughout. No return cargo revenue is assumed.\n");
        text.append("\n| Material | Physically consumed kg | Source value |\n|---|---:|---:|\n");
        for (String material : FUELS) {
            double kg = run.consumed.getOrDefault(material, 0.0);
            text.append(String.format(Locale.ROOT, "| %s | %.2f | %.2f |%n", material, kg, kg * price(material)));
        }
        return text.append("\nThe separate depleted-port test retains the freighter at the Moon in RETURNING, survives reload and rejects ")
                .append("unfunded local or inaccessible remote refills without changing market stock or cash. This fixture measures a feasible ")
                .append("supply design rather than the optimal fleet. Return cargo, improved tanker designs or local fuel production require separate audits.\n").toString();
    }
}
