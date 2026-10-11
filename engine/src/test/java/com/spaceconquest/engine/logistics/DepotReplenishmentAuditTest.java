package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.industry.PowerGridState;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.StationModule;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Finite opening source stocks replenish an initially empty depot through paid physical deliveries. */
class DepotReplenishmentAuditTest {
    @TempDir Path directory;
    private static final double RAW_FUEL_PRICE = .005;
    private static final double DEPOT_FUEL_PRICE = .01;
    private static final double TANK = 120000;
    private static final Map<String, Double> GOODS = Map.of("steel", 1250.0, "food_matrix", 1250.0);
    private record Wave(int number, long turn, double deliveredKg, double supplierFuel, double lunarFuel,
                        double chainCash, double inventory, double result) {}
    private static final class Run {
        GameState state;
        double supplierFuel;
        double lunarFuel;
        int supplyTrips;
        double openingChainCash;
        double openingTotalCash;
        double chargingCost;
        final List<Wave> waves = new ArrayList<>();
        Run(GameState state) {
            this.state = state;
            openingChainCash = chainCash(state);
            openingTotalCash = totalCash(state);
        }
    }

    @Test void repeatedPaidDeliveriesRestartStockedOutLunarTradeWithoutCreatingStockOrCash() throws Exception {
        var run = new Run(world());
        charge(run, "supplier", "low-port");
        for (int wave = 1; wave <= 3; wave++) charge(run, "trader-" + wave, "depot-port");
        for (int wave = 1; wave <= 3; wave++) {
            String routeId = "lunar-" + wave;
            activate(run, routeId);
            waitForStock(run, routeId);
            double retainedOpeningFuel = fuelStock(run.state);
            for (int batch = 0; batch < 4; batch++) {
                supply(run, PropulsionCatalog.drive("mod_hydrolox_rocket").propellantMaterials(30000));
                assertEquals(0, route(run.state, routeId).onboardKg(), "Fuel alone must not create a cargo shipment.");
            }
            assertEquals(retainedOpeningFuel + TANK, fuelStock(run.state), 1e-5);
            assertEquals(0, hub(run.state, "depot-market").activeOrders().get("steel").supplyKg());
            supply(run, GOODS);
            int days = 0;
            while (route(run.state, routeId).totalVolumeMovedKg() < 2500 && days++ < 60) day(run);
            assertEquals(2500, route(run.state, routeId).totalVolumeMovedKg(), 1e-5);
            assertTrue(fleet(run.state, "trader-" + wave).location().isAt(FleetLocation.Site.docked("moon-port")));
            assertTrue(ship(run.state, "trader-" + wave).currentFuelKg() >= 24000);
            assertTrue(fuelStock(run.state) > 0, "Expensive-port refueling retains public fuel for later trips");
            assertEquals(fuelStock(run.state), hub(run.state, "depot-market").currentStoredWeightKg(), 1e-5);
            double cash = chainCash(run.state) - run.openingChainCash;
            double inventory = inventory(run.state);
            run.waves.add(new Wave(wave, run.state.turn(), lunarDelivered(run.state), run.supplierFuel,
                    run.lunarFuel, cash, inventory, cash + inventory));
            run.state = reload(run.state, "after-wave-" + wave);
        }
        assertEquals(15, run.supplyTrips);
        assertEquals(7500, lunarDelivered(run.state), 1e-5);
        assertEquals(367500, route(run.state, "supply").totalVolumeMovedKg(), 1e-5);
        assertTrue(fleet(run.state, "supplier").location().isAt(FleetLocation.Site.docked("low-port")));
        assertEquals(0, route(run.state, "supply").onboardKg());
        assertEquals(7500, hub(run.state, "moon-market").currentStoredWeightKg(), 1e-5);
        assertEquals(82000, account(run.state, "moon-market"), 1e-5);
        assertEquals(run.openingTotalCash, totalCash(run.state), 1e-5);
        assertSourceConservation(run);
        var last = run.waves.getLast();
        double sourceCargoCost = 7500 * .8 + 3 * TANK * RAW_FUEL_PRICE;
        double supplierFuelPurchased = run.supplierFuel + ship(run.state, "supplier").currentFuelKg();
        double expectedCash = 18000 - sourceCargoCost - supplierFuelPurchased * RAW_FUEL_PRICE - run.chargingCost;
        double consumedResult = 18000 - 7500 * .8 - (run.supplierFuel + run.lunarFuel) * RAW_FUEL_PRICE - run.chargingCost;
        assertEquals(expectedCash, last.chainCash(), 1e-5);
        assertEquals(consumedResult, last.result(), 1e-5);
        assertTrue(last.result() > 0, "This fixture tests repeatability at explicit cheap source prices.");
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/depot-replenishment-report.md"), report(run));
    }

    @Test void incompleteSourceMixturesAndUnfundedContractsCannotCreateDepotSupplies() throws Exception {
        var run = new Run(world());
        charge(run, "supplier", "low-port");
        var funded = ShipFueling.refuel(run.state, "supplier", "low-port", 30000);
        var cargo = PropulsionCatalog.drive("mod_hydrolox_rocket").propellantMaterials(30000);
        var processor = new LogisticsProcessor();
        assertNotNull(processor.previewBasket(funded, route(funded, "supply"), cargo));
        var low = hub(funded, "low-market");
        var orders = new HashMap<>(low.activeOrders());
        double missing = orders.get("liquid_hydrogen").supplyKg();
        orders.put("liquid_hydrogen", new MarketOrder("liquid_hydrogen", 0, 0, RAW_FUEL_PRICE, 0));
        var depleted = funded.withCommercialHubs(funded.commercialHubs().stream().map(hub -> hub.id().equals(low.id())
                ? new CommercialHub(low.id(), low.entityId(), 0, low.storageCapacityKg(), low.currentStoredWeightKg() - missing, 10, orders) : hub).toList());
        var noBuyer = funded.withMarketAccounts(funded.marketAccounts().stream().map(account -> account.hubId().equals("depot-market")
                ? new MarketAccount(account.hubId(), 0) : account).toList());
        var noOwner = funded.withEmpires(funded.empires().stream().map(owner -> owner.id().equals("supplier-owner")
                ? new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), 0, 0, List.of(), List.of(), Map.of(),
                owner.unlockedTechIds(), List.of()) : owner).toList());
        for (var rejected : List.of(depleted, noBuyer, noOwner)) {
            double cash = totalCash(rejected);
            var depot = hub(rejected, "depot-market");
            var source = hub(rejected, "low-market");
            assertNull(processor.previewBasket(rejected, route(rejected, "supply"), cargo));
            assertEquals(source, hub(rejected, "low-market"));
            assertEquals(depot, hub(rejected, "depot-market"));
            assertEquals(cash, totalCash(rejected));
            assertTrue(ship(rejected, "supplier").storedCargoKg().isEmpty());
            assertFalse(fleet(rejected, "supplier").location().inTransit());
        }
    }

    private void waitForStock(Run run, String routeId) {
        var before = run.state;
        String trader = routeId.replace("lunar", "trader");
        for (int day = 0; day < 3; day++) {
            day(run);
            assertEquals(0, route(run.state, routeId).onboardKg());
            assertFalse(fleet(run.state, trader).location().inTransit());
            assertEquals(0, ship(run.state, trader).currentFuelKg());
            assertEquals("depot-market", route(run.state, routeId).originEntityId());
        }
        assertEquals(before.commercialHubs(), run.state.commercialHubs());
        assertEquals(chainCash(before), chainCash(run.state), 1e-5);
        assertTrue(route(run.state, routeId).status().contains("Waiting"));
    }

    private void supply(Run run, Map<String, Double> cargo) throws Exception {
        assertTrue(fleet(run.state, "supplier").location().isAt(FleetLocation.Site.docked("low-port")));
        var prior = run.state;
        double needed = 30000 - ship(prior, "supplier").currentFuelKg();
        run.state = ShipFueling.refuel(prior, "supplier", "low-port", needed);
        assertEquals(30000, ship(run.state, "supplier").currentFuelKg(), 1e-5);
        assertEquals(needed * RAW_FUEL_PRICE, ownerCash(prior, "supplier-owner") - ownerCash(run.state, "supplier-owner"), 1e-5);
        var processor = new LogisticsProcessor();
        var shipment = processor.previewBasket(run.state, route(run.state, "supply"), cargo);
        assertNotNull(shipment, "The catalog supplier must fund its loaded depot leg: " + cargo);
        assertEquals(0, route(run.state, "supply").onboardKg());
        double cost = cargo.entrySet().stream().mapToDouble(entry -> entry.getValue()
                * hub(run.state, "low-market").activeOrders().get(entry.getKey()).pricePerKg()).sum();
        assertEquals(cost, ownerCash(run.state, "supplier-owner") - ownerCash(shipment.state(), "supplier-owner"), 1e-5);
        double deliveredBefore = route(run.state, "supply").totalVolumeMovedKg();
        double cargoKg = cargo.values().stream().mapToDouble(Double::doubleValue).sum();
        run.state = replaceRoute(shipment.state(), shipment.route().withActive(false));
        run.state = reload(run.state, "supply-" + run.supplyTrips);
        int days = 0;
        while (!(fleet(run.state, "supplier").location().isAt(FleetLocation.Site.docked("low-port"))
                && TradeRoute.LOADING.equals(route(run.state, "supply").phase())) && days++ < 30) day(run);
        assertTrue(days < 30, "The supplier must fund its actual empty return without depot RP-1.");
        assertEquals(deliveredBefore + cargoKg, route(run.state, "supply").totalVolumeMovedKg(), 1e-5);
        assertEquals(0, route(run.state, "supply").onboardKg());
        assertEquals(0, ship(run.state, "supplier").storedCargoKg().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-5);
        run.supplyTrips++;
    }

    private void day(Run run) {
        var before = run.state;
        var powered = ShipPowerProcessor.advanceDay(before);
        var moved = before.withFleets(new FleetProcessor().processFleetMovements(powered, before.orbitalStations(), List.of()))
                .withTurn(before.turn() + 1);
        for (var fleet : moved.fleets()) for (var ship : fleet.ships()) {
            double burned = ship(before, ship.id()).currentFuelKg() - ship.currentFuelKg();
            assertTrue(burned >= -1e-6);
            if (ship.id().equals("supplier")) run.supplierFuel += burned; else run.lunarFuel += burned;
            assertEquals(0, ship.powerState().lastUnmetEssentialKwh(), 1e-5);
            assertEquals(0, ship.powerState().unmetCargoKwh(), 1e-5);
            assertTrue(ship.powerState().cargoPreservation().lostKgToday().values().stream().allMatch(kg -> kg == 0));
            assertFalse(fleet.location().orbitalFlight() != null && fleet.location().orbitalFlight().failed(),
                    fleet.name() + ": " + fleet.location());
        }
        var processor = new LogisticsProcessor();
        // These are explicit one-way goods probes. The empty Moon port cannot insure automatic returns.
        for (var route : moved.tradeRoutes()) {
            if (route.id().equals("supply") || !route.isActive() || !TradeRoute.LOADING.equals(route.phase())) continue;
            var shipment = processor.previewBasket(moved, route, GOODS);
            if (shipment != null) moved = replaceRoute(shipment.state(), shipment.route().withRoaming(true).withActive(false));
        }
        var traded = processor.processTradeRoutes(moved);
        if (route(before, "supply").onboardKg() > 0
                && !fleet(moved, "supplier").location().isAt(FleetLocation.Site.docked("depot-port")))
            assertEquals(route(before, "supply").totalVolumeMovedKg(), route(traded.state(), "supply").totalVolumeMovedKg());
        run.state = traded.state();
        assertEquals(run.openingTotalCash, totalCash(run.state), 1e-5);
        assertTrue(run.state.marketAccounts().stream().allMatch(account -> account.unsettledSalesCredits() >= 0));
        assertTrue(run.state.commercialHubs().stream().allMatch(hub -> hub.currentStoredWeightKg() >= -1e-6));
    }

    private void charge(Run run, String ship, String port) {
        double before = chainCash(run.state);
        run.state = ShipBatteryCharging.charge(run.state, ship, port, 500 / .9);
        assertEquals(500, ship(run.state, ship).powerState().batteryChargeKwh(), 1e-5);
        run.chargingCost += before - chainCash(run.state);
    }

    private void activate(Run run, String id) {
        run.state = replaceRoute(run.state, route(run.state, id).withActive(true));
    }

    private GameState replaceRoute(GameState state, TradeRoute replacement) {
        return state.withTradeRoutes(state.tradeRoutes().stream().map(route -> route.id().equals(replacement.id()) ? replacement : route).toList());
    }

    private void assertSourceConservation(Run run) {
        var low = hub(run.state, "low-market");
        assertEquals(0, low.activeOrders().get("liquid_hydrogen").supplyKg(), 1e-5);
        for (String good : GOODS.keySet()) assertEquals(0, low.activeOrders().get(good).supplyKg(), 1e-5);
        double rp1Loaded = run.supplierFuel + ship(run.state, "supplier").currentFuelKg();
        var rp1 = PropulsionCatalog.drive("mod_chemical_rocket").propellantMaterials(600000 - rp1Loaded);
        rp1.forEach((id, quantity) -> assertEquals(quantity, low.activeOrders().get(id).supplyKg(), 1e-5));
        assertEquals(rp1.values().stream().mapToDouble(Double::doubleValue).sum(), low.currentStoredWeightKg(), 1e-5);
    }

    private GameState reload(GameState state, String name) throws Exception {
        var manager = new SaveGameManager(directory); var file = directory.resolve(name + ".scsave").toFile();
        manager.save(file, state, 1, "2027-01-01T08:00:00");
        var restored = manager.load(file).toGameState(state.turn(), "RUNNING");
        assertEquals(state.fleets(), restored.fleets());
        assertEquals(state.tradeRoutes(), restored.tradeRoutes());
        assertEquals(state.commercialHubs(), restored.commercialHubs());
        assertEquals(state.marketAccounts(), restored.marketAccounts());
        assertEquals(state.empires(), restored.empires());
        assertEquals(state.powerGrids(), restored.powerGrids());
        return restored;
    }

    private GameState world() throws Exception {
        var technologies = List.of("rocketry", "hydrolox_propulsion", "electricity", "solar_power", "industrial_production", "space_stations");
        var supplier = new Empire("supplier-owner", "Supplier", "human", "Individualist", 1e6, 0, List.of(), List.of(), Map.of(), technologies, List.of());
        var trader = new Empire("trader-owner", "Trader", "human", "Individualist", 1e6, 0, List.of(), List.of(), Map.of(), technologies, List.of());
        var provider = new Empire("provider", "Port operator", "human", "Individualist", 1e6, 0, List.of(), List.of(), Map.of(), technologies, List.of());
        var lowOrders = new HashMap<String, MarketOrder>();
        var quantities = new HashMap<>(PropulsionCatalog.drive("mod_chemical_rocket").propellantMaterials(600000));
        PropulsionCatalog.drive("mod_hydrolox_rocket").propellantMaterials(360000).forEach((id, kg) -> quantities.merge(id, kg, Double::sum));
        quantities.forEach((id, kg) -> lowOrders.put(id, new MarketOrder(id, kg, 0, RAW_FUEL_PRICE, 0)));
        GOODS.forEach((id, kg) -> lowOrders.put(id, new MarketOrder(id, kg * 3, 0, .8, 0)));
        var depotOrders = new HashMap<String, MarketOrder>();
        PropulsionCatalog.drive("mod_hydrolox_rocket").propellantMaterials(360000).forEach((id, kg) ->
                depotOrders.put(id, new MarketOrder(id, 0, kg, DEPOT_FUEL_PRICE, 0)));
        GOODS.forEach((id, kg) -> depotOrders.put(id, new MarketOrder(id, 0, kg * 3, 1, 0)));
        var moonOrders = new HashMap<String, MarketOrder>();
        GOODS.forEach((id, kg) -> moonOrders.put(id, new MarketOrder(id, 0, kg * 3, 3, 0)));
        var state = GameState.builder().solarSystems(DataModelLoader.loadSolarSystems()).empires(List.of(supplier, trader, provider))
                .orbitalStations(List.of(port("low-port", "earth", 500), port("depot-port", "earth", 2000), port("moon-port", "moon", 500)))
                .commercialHubs(List.of(new CommercialHub("low-market", "low-port", 0, 2e6, 967500, 10, lowOrders),
                        new CommercialHub("depot-market", "depot-port", 0, 2e6, 0, 10, depotOrders),
                        new CommercialHub("moon-market", "moon-port", 0, 2e6, 0, 10, moonOrders)))
                .marketAccounts(List.of(new MarketAccount("low-market", 100000), new MarketAccount("depot-market", 100000),
                        new MarketAccount("moon-market", 100000)))
                .powerGrids(List.of(new PowerGridState("low-port", 1000, 0, 1000, 10000, 10000, false),
                        new PowerGridState("depot-port", 1000, 0, 1000, 10000, 10000, false))).build();
        var modules = List.of("mod_chemical_rocket", PropulsionCatalog.FUEL_TANK_MODULE_ID, PropulsionCatalog.FUEL_TANK_MODULE_ID,
                "mod_cargo_vault", ShipComponentCatalog.SOLAR_ARRAY_ID, ShipComponentCatalog.SOLAR_ARRAY_ID, ShipComponentCatalog.BATTERY_ID);
        var supplyDesign = ShipBlueprintFactory.evaluate(state, new ShipDesignSpecification("supply-design", "Depot supplier", "supplier-owner",
                ShipRole.CARGO_TRANSPORT, "steel", modules, "steel", .5));
        var lunarDesign = ShipBlueprintFactory.evaluate(state, new ShipDesignSpecification("lunar-design", "Lunar freighter", "trader-owner",
                ShipRole.CARGO_TRANSPORT, "steel", ChemicalFreighterCatalog.modules("mod_hydrolox_rocket"), "steel", .5));
        assertTrue(supplyDesign.valid(), supplyDesign.errors().toString());
        assertTrue(lunarDesign.valid(), lunarDesign.errors().toString());
        assertEquals(30000, supplyDesign.design().maxCargoMassKg());
        var fleets = new ArrayList<Fleet>(); var routes = new ArrayList<TradeRoute>();
        fleets.add(fleet("supplier", "supplier-owner", "supply-design", "low-port", .05));
        routes.add(new TradeRoute("supply", "Depot supply contract", "supplier-owner", "low-market", "depot-market", "liquid_hydrogen",
                30000, 0, 2e6, List.of("supplier"), 0, false));
        for (int wave = 1; wave <= 3; wave++) {
            fleets.add(fleet("trader-" + wave, "trader-owner", "lunar-design", "depot-port", .20));
            routes.add(new TradeRoute("lunar-" + wave, "Lunar wave " + wave, "trader-owner", "depot-market", "moon-market", "steel",
                    2500, 0, 2e6, List.of("trader-" + wave), 0, false).withRoaming(true));
        }
        return state.withShipDesigns(List.of(supplyDesign.design(), lunarDesign.design())).withFleets(fleets).withTradeRoutes(routes);
    }

    private Fleet fleet(String id, String owner, String design, String port, double reserve) {
        var ship = new ShipInstance(id, design, owner, 100, 0, 0, Map.of()).withPowerState(ShipPowerState.empty());
        return new Fleet(id, id, owner, "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship), FleetLocation.at(FleetLocation.Site.docked(port)))
                .withFuelPolicy(new FleetFuelPolicy(reserve, reserve, reserve));
    }
    private OrbitalStation port(String id, String body, double altitude) {
        var yard = new StationModule("yard-" + id, "Yard", StationModule.TYPE_CAPITAL_SLIPWAY, 4, 10000, 10, 0, Map.of(), "engineer", 0, true);
        return new OrbitalStation(id, id, "sol", body, "provider", OrbitalStation.OWNERSHIP_PUBLIC_STATE,
                20, List.of(yard), Map.of(), 100, 10, 0, 0, 100, 100, "steel", 0, true).withParkingAltitudeKm(altitude);
    }
    private static TradeRoute route(GameState state, String id) { return state.tradeRoutes().stream().filter(route -> route.id().equals(id)).findFirst().orElseThrow(); }
    private static Fleet fleet(GameState state, String id) { return state.fleets().stream().filter(fleet -> fleet.id().equals(id)).findFirst().orElseThrow(); }
    private static ShipInstance ship(GameState state, String id) { return fleet(state, id).ships().getFirst(); }
    private static CommercialHub hub(GameState state, String id) { return state.commercialHubs().stream().filter(hub -> hub.id().equals(id)).findFirst().orElseThrow(); }
    private static double ownerCash(GameState state, String id) { return state.empires().stream().filter(owner -> owner.id().equals(id)).findFirst().orElseThrow().treasuryCredits(); }
    private static double account(GameState state, String id) { return state.marketAccounts().stream().filter(account -> account.hubId().equals(id)).findFirst().orElseThrow().unsettledSalesCredits(); }
    private static double chainCash(GameState state) { return ownerCash(state, "supplier-owner") + ownerCash(state, "trader-owner") + account(state, "depot-market"); }
    private static double totalCash(GameState state) { return state.empires().stream().mapToDouble(Empire::treasuryCredits).sum()
            + state.marketAccounts().stream().mapToDouble(MarketAccount::unsettledSalesCredits).sum(); }
    private static double lunarDelivered(GameState state) { return state.tradeRoutes().stream().filter(route -> route.id().startsWith("lunar-"))
            .mapToDouble(TradeRoute::totalVolumeMovedKg).sum(); }
    private static double fuelStock(GameState state) { return List.of("liquid_hydrogen", "liquid_oxygen").stream()
            .mapToDouble(id -> hub(state, "depot-market").activeOrders().get(id).supplyKg()).sum(); }
    private static double inventory(GameState state) { return (state.fleets().stream().flatMap(fleet -> fleet.ships().stream())
            .mapToDouble(ShipInstance::currentFuelKg).sum() + fuelStock(state)) * RAW_FUEL_PRICE; }

    private String report(Run run) {
        var text = new StringBuilder("# Depot replenishment audit\n\n")
                .append("An initially empty 2,000 km Earth depot receives 360,000 kg hydrolox propellant and 7,500 kg mixed cargo from ")
                .append("finite stocks at a 500 km source port. A validated RP-1 supplier with a 30,000 kg cargo vault and 30,000 kg main tank ")
                .append("flies fifteen paid deliveries and fifteen funded empty returns. Three initially empty catalog hydrolox freighters ")
                .append("each make one explicit one-way lunar shipment. No ship is teleported or replenished by a fixture after opening.\n\n")
                .append("Source fuel is 0.005 credits/kg and goods are 0.8 credits/kg; depot quotes are 0.01 and 1.0. Lunar posted goods prices ")
                .append("are 3 credits/kg with the existing 80% wholesale share. Source stocks, grid storage and buyer cash are finite. ")
                .append("The upstream supply contracts are explicitly dispatched; lunar departures are explicit one-way probes with automatic current-port refueling. Source production, surface lift, ")
                .append("ship and station construction, wages, depreciation, taxes and lunar refueling or return trips are excluded.\n\n")
                .append("The chain combines supplier and trader treasuries with the depot market account. Internal depot purchases cancel. ")
                .append("Consumption result adds fuel retained aboard and in public depot stock at its source acquisition price to the chain cash change. ")
                .append("Unused opening source stock is outside the chain and is not credited as profit.\n\n")
                .append("| Wave | Campaign day | Lunar cargo delivered kg | Supplier fuel consumed kg | Lunar fuel consumed kg | Chain cash change | Retained fuel value | Consumption result |\n")
                .append("|---|---:|---:|---:|---:|---:|---:|---:|\n");
        for (var wave : run.waves) text.append(String.format(Locale.ROOT, "| %d | %d | %.2f | %.2f | %.2f | %.2f | %.2f | %.2f |%n",
                wave.number(), wave.turn(), wave.deliveredKg(), wave.supplierFuel(), wave.lunarFuel(), wave.chainCash(), wave.inventory(), wave.result()));
        return text.append(String.format(Locale.ROOT, "\nCommissioning electricity costs %.2f credits. Each new carrier waits three days at the depleted depot. ", run.chargingCost))
                .append("Fuel deliveries alone do not load cargo. Paid cargo arrival restarts the waiting trade. Every supply flight and each completed ")
                .append("wave survives save/load. Total money, source stock, cargo and protected fuel are checked throughout.\n\n")
                .append(String.format(Locale.ROOT, "At fixed measured fuel use and goods prices, the combined consumption result breaks even at %.5f credits/kg ",
                        (18000 - 7500 * .8 - run.chargingCost) / (run.supplierFuel + run.lunarFuel)))
                .append("for source propellant. This arithmetic sensitivity excludes construction and ongoing overhead. Separate rejection checks cover ")
                .append("an incomplete source mixture, exhausted depot buyer funds and an insolvent supplier without committing partial stock or cash.\n").toString();
    }
}
