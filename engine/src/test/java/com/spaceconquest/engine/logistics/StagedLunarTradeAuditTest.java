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

/** Paid low-orbit access and depot loading precede the existing lunar shipment planner. */
class StagedLunarTradeAuditTest {
    @TempDir Path directory;
    private static final double CAPACITY = 120000;
    private static final Map<String, Double> BASKET = Map.of("steel", 1250.0, "food_matrix", 1250.0);
    private record Scenario(String drive, double reserve, double price) {}
    private record Row(Scenario scenario, boolean delivered, String reason, int stagingDays, int lunarDays,
                       double stagingFuel, double lunarFuel, double remaining, double cashChange, double result) {}

    @Test void automaticDepotSelectionCommitsOnlyThePaidFirstLegAndSurvivesReload() throws Exception {
        var initial = automaticWorld(.01);
        assertNull(RoamingTradePlanner.choose(initial, initial.tradeRoutes().getFirst(), initial.fleets().getFirst()).shipment());
        var selected = IntermediateTradePlanner.choose(initial, initial.tradeRoutes().getFirst(), initial.fleets().getFirst());
        assertNotNull(selected);
        assertTrue(selected.profitCredits() > 0);
        assertEquals(7, selected.days());
        assertEquals(0, ship(initial).currentFuelKg());
        assertEquals(120000, hub(initial, "low-market").currentStoredWeightKg());
        assertEquals(initial.commercialHubs().get(1), selected.state().commercialHubs().get(1), "Remote depot stock is not reserved or purchased.");
        assertEquals(initial.marketAccounts().stream().filter(account -> !account.hubId().equals("low-market")).toList(),
                selected.state().marketAccounts().stream().filter(account -> !account.hubId().equals("low-market")).toList());
        var processor = new LogisticsProcessor();
        var state = processor.processTradeRoutes(initial).state();
        assertEquals(TradeRoute.REPOSITIONING, state.tradeRoutes().getFirst().phase());
        assertEquals("low-market", state.tradeRoutes().getFirst().originEntityId());
        assertEquals("depot-market", state.tradeRoutes().getFirst().destinationEntityId());
        assertEquals(0, state.tradeRoutes().getFirst().onboardKg());
        assertEquals(0, state.tradeRoutes().getFirst().totalVolumeMovedKg());
        assertEquals(1200, cash(initial) - cash(state), 1e-5);
        assertEquals(-1200, state.tradeRoutes().getFirst().cumulativeOperatingResultCredits(), 1e-5);
        assertEquals(0, hub(state, "low-market").currentStoredWeightKg(), 1e-5);
        state = reload(state, "automatic-depot");
        state = processor.processTradeRoutes(day(state)).state();
        assertEquals("depot-market", state.tradeRoutes().getFirst().originEntityId());
        assertEquals("moon-market", state.tradeRoutes().getFirst().destinationEntityId());
        assertEquals(TradeRoute.DELIVERING, state.tradeRoutes().getFirst().phase());
        assertEquals(2500, state.tradeRoutes().getFirst().onboardKg());
        assertEquals(0, hub(state, "depot-market").activeOrders().get("steel").supplyKg(), 1e-5);
        state = reload(day(state), "automatic-lunar");
        int days = 2;
        while (state.tradeRoutes().getFirst().totalVolumeMovedKg() < 2500 && days++ < 30)
            state = processor.processTradeRoutes(day(state)).state();
        assertEquals(7, days);
        assertTrue(state.fleets().getFirst().location().isAt(FleetLocation.Site.docked("moon-port")));
        assertEquals(2500, state.tradeRoutes().getFirst().totalVolumeMovedKg());
        assertEquals(TradeRoute.LOADING, state.tradeRoutes().getFirst().phase());
        assertEquals("moon-market", state.tradeRoutes().getFirst().originEntityId());
        assertTrue(ship(state).currentFuelKg() >= 24000);
        assertTrue(cash(state) > cash(initial));
        assertFalse(processor.processTradeRoutes(state).state().fleets().getFirst().location().inTransit());
    }

    @Test void accessCostRejectsAnOtherwiseProfitableDepotTradeEvenWithPrepaidFuel() throws Exception {
        var state = automaticWorld(.01);
        var orders = new HashMap<>(hub(state, "low-market").activeOrders());
        orders.replaceAll((id, order) -> new MarketOrder(id, order.supplyKg(), order.demandKg(), 1, 0));
        state = replaceHub(state, new CommercialHub("low-market", "low-port", 0, 1e6, CAPACITY, 10, orders));
        state = ShipFueling.refuel(state, "ship", "low-port", CAPACITY);
        var depotSite = FleetLocation.Site.docked("depot-port");
        var fleet = state.fleets().getFirst();
        var plan = LocalTravel.plan(state, fleet, depotSite);
        assertNotNull(plan);
        var arrival = LocalTravel.arrivalPreview(state, fleet, depotSite, plan);
        var projected = state.withFleets(List.of(arrival)).withTurn(1);
        assertNotNull(RoamingTradePlanner.choose(projected, state.tradeRoutes().getFirst().atNewOrigin("depot-market"), arrival).shipment());
        assertNull(IntermediateTradePlanner.choose(state, state.tradeRoutes().getFirst(), fleet));
        var waiting = new LogisticsProcessor().processTradeRoutes(state).state();
        assertEquals(cash(state), cash(waiting));
        assertEquals(state.commercialHubs(), waiting.commercialHubs());
        assertFalse(waiting.fleets().getFirst().location().inTransit());
    }

    @Test void depotQuotesRespectFiniteStockOwnerCashAndBuyerCash() throws Exception {
        var initial = automaticWorld(.01);
        var depleted = replaceHub(initial, new CommercialHub("depot-market", "depot-port", 0, 1e6, 0, 10, Map.of()));
        assertNull(IntermediateTradePlanner.choose(depleted, depleted.tradeRoutes().getFirst(), depleted.fleets().getFirst()));
        var buyerEmpty = initial.withMarketAccounts(initial.marketAccounts().stream()
                .map(account -> account.hubId().equals("moon-market") ? new MarketAccount(account.hubId(), 0) : account).toList());
        assertNull(IntermediateTradePlanner.choose(buyerEmpty, buyerEmpty.tradeRoutes().getFirst(), buyerEmpty.fleets().getFirst()));
        var broke = initial.withEmpires(initial.empires().stream().map(empire -> empire.id().equals("owner")
                ? new Empire(empire.id(), empire.name(), empire.raceId(), empire.societyStructure(), 0, 0,
                empire.controlledSystemIds(), List.of(), Map.of(), empire.unlockedTechIds(), List.of()) : empire).toList());
        assertNull(IntermediateTradePlanner.choose(broke, broke.tradeRoutes().getFirst(), broke.fleets().getFirst()));
        assertEquals(0, ship(initial).currentFuelKg());
        assertEquals(120000, hub(initial, "low-market").currentStoredWeightKg());
    }

    @Test void changedDepotMarketsAndCancelledRoutesDoNotCommitAnOnwardJourney() throws Exception {
        for (boolean cancelled : List.of(false, true)) {
            var processor = new LogisticsProcessor();
            var state = processor.processTradeRoutes(automaticWorld(.01)).state();
            assertEquals(TradeRoute.REPOSITIONING, state.tradeRoutes().getFirst().phase());
            if (cancelled) state = state.withTradeRoutes(List.of(state.tradeRoutes().getFirst().withActive(false)));
            else state = replaceHub(state, new CommercialHub("depot-market", "depot-port", 0, 1e6, 0, 10, Map.of()));
            state = reload(state, cancelled ? "cancelled-stop" : "changed-stop");
            double before = cash(state);
            var depot = hub(state, "depot-market");
            state = processor.processTradeRoutes(day(state)).state();
            assertEquals(TradeRoute.LOADING, state.tradeRoutes().getFirst().phase());
            assertEquals("depot-market", state.tradeRoutes().getFirst().originEntityId());
            assertTrue(state.fleets().getFirst().location().isAt(FleetLocation.Site.docked("depot-port")));
            assertEquals(before, cash(state));
            assertEquals(depot, hub(state, "depot-market"));
            assertTrue(ship(state).storedCargoKg().isEmpty());
            assertEquals(0, state.tradeRoutes().getFirst().totalVolumeMovedKg());
            assertFalse(state.tradeRoutes().getFirst().isActive() && cancelled);
        }
    }

    private GameState automaticWorld(double fuelPrice) throws Exception {
        var state = world(new Scenario("mod_hydrolox_rocket", .20, fuelPrice));
        state = state.withTradeRoutes(List.of(state.tradeRoutes().getFirst().atNewOrigin("low-market")));
        return ShipBatteryCharging.charge(state, "ship", "low-port", 500 / .9);
    }

    @Test void directShipmentsTakePrecedenceOverIntermediateStops() throws Exception {
        var state = automaticWorld(.01);
        var lowOrders = new HashMap<>(hub(state, "low-market").activeOrders());
        lowOrders.put("steel", new MarketOrder("steel", 1250, 0, 1, 0));
        state = replaceHub(state, new CommercialHub("low-market", "low-port", 0, 1e6, CAPACITY + 1250, 10, lowOrders));
        var stations = new ArrayList<>(state.orbitalStations());
        stations.add(port("near-port", "earth", 500));
        var hubs = new ArrayList<>(state.commercialHubs());
        hubs.add(new CommercialHub("near-market", "near-port", 0, 1e6, 0, 10,
                Map.of("steel", new MarketOrder("steel", 0, 1250, 3, 0))));
        var accounts = new ArrayList<>(state.marketAccounts());
        accounts.add(new MarketAccount("near-market", 100000));
        state = state.withOrbitalStations(stations).withCommercialHubs(hubs).withMarketAccounts(accounts);
        var selected = RoamingTradePlanner.choose(state, state.tradeRoutes().getFirst(), state.fleets().getFirst());
        assertNotNull(selected.shipment());
        assertEquals("near-market", selected.shipment().route().destinationEntityId());
        var dispatched = new LogisticsProcessor().processTradeRoutes(state).state();
        assertEquals(TradeRoute.DELIVERING, dispatched.tradeRoutes().getFirst().phase());
        assertEquals("near-market", dispatched.tradeRoutes().getFirst().destinationEntityId());
        assertEquals(1250, dispatched.tradeRoutes().getFirst().onboardKg());
    }

    @Test void equivalentDepotsChooseDeterministicallyAndLoadedShipsCannotReposition() throws Exception {
        var state = automaticWorld(.01);
        var hubs = new ArrayList<>(state.commercialHubs());
        var depot = hub(state, "depot-market");
        hubs.addFirst(new CommercialHub("alternate-market", "alternate-port", 0, 1e6,
                depot.currentStoredWeightKg(), 10, depot.activeOrders()));
        var stations = new ArrayList<>(state.orbitalStations());
        stations.add(port("alternate-port", "earth", 2000));
        var accounts = new ArrayList<>(state.marketAccounts());
        accounts.add(new MarketAccount("alternate-market", 100000));
        state = state.withCommercialHubs(hubs).withOrbitalStations(stations).withMarketAccounts(accounts);
        var selected = IntermediateTradePlanner.choose(state, state.tradeRoutes().getFirst(), state.fleets().getFirst());
        assertNotNull(selected);
        assertEquals("alternate-market", selected.route().destinationEntityId());
        var reversed = new ArrayList<>(hubs); java.util.Collections.reverse(reversed);
        var reordered = state.withCommercialHubs(reversed);
        var reorderedChoice = IntermediateTradePlanner.choose(reordered, reordered.tradeRoutes().getFirst(), reordered.fleets().getFirst());
        assertNotNull(reorderedChoice);
        assertEquals(selected.route().destinationEntityId(), reorderedChoice.route().destinationEntityId());
        var original = ship(state);
        var loaded = new ShipInstance(original.id(), original.designId(), original.ownerEntityId(), 100, 0, 0,
                Map.of("steel", 1.0), 0, "", original.transitMode(), original.powerState(), original.supplyState());
        var loadedState = state.withFleets(List.of(state.fleets().getFirst().withShips(List.of(loaded))));
        assertNull(IntermediateTradePlanner.choose(loadedState, loadedState.tradeRoutes().getFirst(), loadedState.fleets().getFirst()));
    }

    @Test void failedStagingBurnCannotAwardDepotArrivalOrPurchaseRemoteCargo() throws Exception {
        var processor = new LogisticsProcessor();
        var state = processor.processTradeRoutes(automaticWorld(.01)).state();
        var fleet = state.fleets().getFirst(); var original = ship(state);
        var empty = new ShipInstance(original.id(), original.designId(), original.ownerEntityId(), 100, 0, 0,
                Map.of(), 0, "", original.transitMode(), original.powerState(), original.supplyState());
        state = state.withFleets(List.of(fleet.withShips(List.of(empty))));
        var depot = hub(state, "depot-market"); double before = cash(state);
        var powered = ShipPowerProcessor.advanceDay(state);
        state = state.withFleets(new FleetProcessor().processFleetMovements(powered, state.orbitalStations(), List.of()))
                .withTurn(state.turn() + 1);
        state = processor.processTradeRoutes(state).state();
        assertTrue(state.fleets().getFirst().location().orbitalFlight().failed());
        assertEquals(TradeRoute.REPOSITIONING, state.tradeRoutes().getFirst().phase());
        assertTrue(state.tradeRoutes().getFirst().status().contains("interrupted"));
        assertEquals("low-market", state.tradeRoutes().getFirst().originEntityId());
        assertEquals(depot, hub(state, "depot-market"));
        assertEquals(before, cash(state));
        assertEquals(0, state.tradeRoutes().getFirst().onboardKg());
        assertEquals(0, state.tradeRoutes().getFirst().totalVolumeMovedKg());
    }

    @Test void paidLowOrbitAccessAndDepotLoadingExposeCompleteVoyageEconomics() throws Exception {
        var rows = new ArrayList<Row>();
        for (String drive : ChemicalFreighterCatalog.DRIVES)
            for (double reserve : List.of(.05, .10, .20))
                for (double price : List.of(1.0, .01)) rows.add(run(new Scenario(drive, reserve, price)));
        assertEquals(18, rows.size());
        assertEquals(14, rows.stream().filter(Row::delivered).count());
        assertTrue(rows.stream().allMatch(row -> row.stagingDays() == 1));
        assertTrue(rows.stream().filter(Row::delivered).allMatch(row -> row.lunarDays() == 6));
        assertTrue(rows.stream().filter(row -> row.scenario().drive().equals("mod_hydrolox_rocket"))
                .allMatch(Row::delivered));
        assertTrue(rows.stream().filter(row -> !row.delivered()).allMatch(row ->
                row.scenario().reserve() == .20 && row.reason().contains("protected reserve")));
        assertTrue(rows.stream().filter(row -> row.delivered() && row.scenario().price() == 1)
                .allMatch(row -> row.result() < 0));
        assertTrue(rows.stream().anyMatch(row -> row.delivered() && row.result() > 0));
        var output = Path.of("target/staged-lunar-trade-report.md");
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, report(rows));
    }

    private Row run(Scenario scenario) throws Exception {
        var opening = world(scenario);
        double openingCash = cash(opening);
        var fueled = refuel(opening, "low-port", CAPACITY, scenario.price());
        var charged = ShipBatteryCharging.charge(fueled, "ship", "low-port", 500 / .9);
        double chargingCost = cash(fueled) - cash(charged);
        assertTrue(chargingCost > 0);
        assertEquals(500, ship(charged).powerState().batteryChargeKwh(), 1e-6);
        assertEquals(2000 - 500 / .9, charged.powerGrids().getFirst().currentStoredKwh(), 1e-6);
        var target = FleetLocation.Site.docked("depot-port");
        var preview = OrbitalTravel.preview(charged, charged.fleets().getFirst(), target);
        assertNotNull(preview.plan(), preview.problem());
        assertTrue(preview.plan().orbital().parkingTransfer());
        assertTrue(ShipPowerForecast.ready(ShipPowerForecast.departure(charged,
                charged.fleets().getFirst(), target, preview.plan(), null)));
        var expected = LocalTravel.arrivalPreview(charged, charged.fleets().getFirst(), target, preview.plan());
        var staging = charged.withFleets(List.of(LocalTravel.depart(charged.fleets().getFirst(), target, preview.plan())));
        staging = reload(staging, "staging-" + scenario.drive() + "-" + scenario.reserve() + "-" + scenario.price());
        int stagingDays = 0;
        while (staging.fleets().getFirst().location().inTransit() && stagingDays++ < 30) staging = day(staging);
        assertTrue(staging.fleets().getFirst().location().isAt(target));
        assertEquals(Math.ceil(preview.plan().days()), stagingDays);
        assertEquals(expected.ships().getFirst().currentFuelKg(), ship(staging).currentFuelKg(), 1e-5);
        assertEquals(expected.ships().getFirst().powerState().batteryChargeKwh(),
                ship(staging).powerState().batteryChargeKwh(), 1e-5);
        assertTrue(ship(staging).storedCargoKg().isEmpty());
        double stagingFuel = CAPACITY - ship(staging).currentFuelKg();
        assertTrue(stagingFuel > 0);
        assertTrue(ship(staging).currentFuelKg() >= scenario.reserve() * CAPACITY);
        assertEquals(cash(charged), cash(staging), 1e-6, "Maneuvers consume paid fuel without a second cash charge.");
        var logistics = new LogisticsProcessor();
        var automatic = logistics.previewBasket(staging, staging.tradeRoutes().getFirst(), BASKET);
        var toppedUp = refuel(staging, "depot-port", stagingFuel, scenario.price());
        var shipment = logistics.previewBasket(toppedUp, toppedUp.tradeRoutes().getFirst(), BASKET);
        assertEquals(1250, hub(toppedUp, "depot-market").activeOrders().get("steel").supplyKg());
        assertTrue(ship(toppedUp).storedCargoKg().isEmpty(), "A shipment preview cannot mutate its input.");
        if (shipment == null) {
            assertTrue(automatic == null, "Automatic top-ups cannot bypass a protected reserve rejection.");
            var original = ship(toppedUp);
            var loaded = new ShipInstance(original.id(), original.designId(), original.ownerEntityId(), 100, 0,
                    original.currentFuelKg(), BASKET, 0, "", original.transitMode(), original.powerState(), original.supplyState());
            var rejected = OrbitalTravel.preview(toppedUp, toppedUp.fleets().getFirst().withShips(List.of(loaded)),
                    FleetLocation.Site.docked("moon-port"));
            assertNull(rejected.plan());
            return new Row(scenario, false, rejected.problem(), stagingDays, 0, stagingFuel, 0, CAPACITY,
                    cash(toppedUp) - openingCash, -stagingFuel * scenario.price() - chargingCost);
        }
        checkAutomaticTopUp(staging, automatic, stagingFuel, scenario.price());
        var state = shipment.state().withTradeRoutes(List.of(shipment.route()));
        assertEquals(2500, cash(toppedUp) - cash(state), 1e-6);
        for (String material : BASKET.keySet()) {
            assertEquals(0, hub(state, "depot-market").activeOrders().get(material).supplyKg(), 1e-6);
            assertEquals(BASKET.get(material), ship(state).storedCargoKg().get(material));
        }
        var quote = RoamingTradeCost.quote(state, shipment.preparedFleet(), hub(toppedUp, "depot-market"), hub(state, "moon-market"));
        assertNotNull(quote);
        state = day(state);
        assertTrue(state.fleets().getFirst().location().inTransit());
        assertEquals(2500, state.tradeRoutes().getFirst().onboardKg());
        state = reload(state, "lunar-" + scenario.drive() + "-" + scenario.reserve() + "-" + scenario.price());
        int lunarDays = 1;
        double sales = 0;
        while (state.tradeRoutes().getFirst().onboardKg() > 1e-6 && lunarDays++ < 60) {
            state = day(state);
            boolean docked = state.fleets().getFirst().location().isAt(FleetLocation.Site.docked("moon-port"));
            double before = cash(state);
            var result = logistics.processTradeRoutes(state);
            if (!docked) assertEquals(0, result.deliveredKg(), "No cargo sale before docking.");
            state = result.state();
            sales += cash(state) - before;
        }
        assertTrue(state.fleets().getFirst().location().isAt(FleetLocation.Site.docked("moon-port")));
        assertEquals(quote.days(), lunarDays);
        assertEquals(6000, sales, 1e-6);
        assertEquals(2500, state.tradeRoutes().getFirst().totalVolumeMovedKg());
        assertTrue(state.tradeRoutes().getFirst().cargoManifest().isEmpty());
        assertEquals(0, ship(state).storedCargoKg().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-6);
        double remaining = ship(state).currentFuelKg(), lunarFuel = CAPACITY - remaining;
        assertEquals(lunarFuel * scenario.price(), quote.fuelCredits(), 1e-5);
        assertTrue(remaining >= scenario.reserve() * CAPACITY, "The lunar journey retains the selected reserve.");
        double cashChange = cash(state) - openingCash;
        double result = sales - 2500 - (stagingFuel + lunarFuel) * scenario.price() - chargingCost;
        assertEquals(result, cashChange + remaining * scenario.price(), 1e-5);
        assertEquals(3500, state.tradeRoutes().getFirst().cumulativeOperatingResultCredits(), 1e-6);
        assertFalse(logistics.processTradeRoutes(state).state().fleets().getFirst().location().inTransit());
        return new Row(scenario, true, "Funded docking and mixed sale", stagingDays, lunarDays, stagingFuel,
                lunarFuel, remaining, cashChange, result);
    }

    private void checkAutomaticTopUp(GameState staging, LogisticsProcessor.ShipmentPreview automatic,
                                     double stagingFuel, double price) {
        assertNotNull(automatic, "The trader can buy its supported next leg's fuel at the depot.");
        double purchased = automatic.preparedFleet().ships().getFirst().currentFuelKg() - ship(staging).currentFuelKg();
        assertTrue(purchased >= 0 && purchased <= stagingFuel + 1e-5);
        assertEquals(2500 + purchased * price, cash(staging) - cash(automatic.state()), 1e-5);
        assertEquals(purchased + 2500, hub(staging, "depot-market").currentStoredWeightKg()
                - hub(automatic.state(), "depot-market").currentStoredWeightKg(), 1e-5);
        assertEquals(-purchased * price, automatic.route().cumulativeOperatingResultCredits(), 1e-5);
        var destination = FleetLocation.Site.docked("moon-port");
        var plan = LocalTravel.plan(automatic.state(), automatic.preparedFleet(), destination);
        assertNotNull(plan);
        assertTrue(ShipPowerForecast.ready(ShipPowerForecast.departure(automatic.state(),
                automatic.preparedFleet(), destination, plan, null)));
        assertTrue(ship(staging).storedCargoKg().isEmpty());
        assertFalse(staging.fleets().getFirst().location().inTransit());
    }

    @Test void remoteAndInsufficientDepotStockCannotProvideFreeStagingFuel() throws Exception {
        var scenario = new Scenario("mod_hydrolox_rocket", .20, .01);
        var state = refuel(world(scenario), "low-port", 60000, scenario.price());
        assertSame(state, ShipFueling.refuel(state, "ship", "depot-port", 1000), "Remote depot stock is inaccessible.");
        state = ShipBatteryCharging.charge(state, "ship", "low-port", 500 / .9);
        var target = FleetLocation.Site.docked("depot-port");
        var plan = LocalTravel.plan(state, state.fleets().getFirst(), target);
        assertNotNull(plan);
        state = state.withFleets(List.of(LocalTravel.depart(state.fleets().getFirst(), target, plan)));
        int ticks = 0;
        while (state.fleets().getFirst().location().inTransit() && ticks++ < 30) state = day(state);
        assertTrue(state.fleets().getFirst().location().isAt(target));
        var orders = new HashMap<>(hub(state, "depot-market").activeOrders());
        var drive = PropulsionCatalog.drive(scenario.drive());
        drive.propellantMaterials(CAPACITY).keySet().forEach(id -> orders.put(id, new MarketOrder(id, 0, 0, scenario.price(), 0)));
        var depleted = replaceHub(state, new CommercialHub("depot-market", "depot-port", 0, 1e6, 2500, 10, orders));
        assertSame(depleted, ShipFueling.refuel(depleted, "ship", "depot-port", CAPACITY - ship(depleted).currentFuelKg()));
        assertNull(new LogisticsProcessor().previewBasket(depleted, depleted.tradeRoutes().getFirst(), BASKET));
        assertTrue(ship(depleted).storedCargoKg().isEmpty());
        assertFalse(depleted.fleets().getFirst().location().inTransit());
    }

    private GameState refuel(GameState state, String port, double quantity, double price) {
        double beforeCash = cash(state), beforeFuel = ship(state).currentFuelKg();
        var before = state.commercialHubs().stream().filter(hub -> hub.entityId().equals(port)).findFirst().orElseThrow();
        var paid = ShipFueling.refuel(state, "ship", port, quantity);
        assertEquals(beforeFuel + quantity, ship(paid).currentFuelKg(), 1e-5);
        assertEquals(quantity * price, beforeCash - cash(paid), 1e-5);
        var after = hub(paid, before.id());
        assertEquals(quantity, before.currentStoredWeightKg() - after.currentStoredWeightKg(), 1e-5);
        PropulsionCatalog.drive(PropulsionCatalog.mainDrive(state.shipDesigns().getFirst().equippedModuleIds()).moduleId())
                .propellantMaterials(quantity).forEach((id, kg) ->
                        assertEquals(kg, before.activeOrders().get(id).supplyKg() - after.activeOrders().get(id).supplyKg(), 1e-5));
        return paid;
    }

    private GameState reload(GameState state, String name) throws Exception {
        var manager = new SaveGameManager(directory);
        var file = directory.resolve(name + ".scsave").toFile();
        manager.save(file, state, 1, "2027-01-01T08:00:00");
        // The save API takes the calendar-derived turn from the caller.
        var restored = manager.load(file).toGameState(state.turn(), "RUNNING");
        assertEquals(state.turn(), restored.turn());
        assertEquals(state.fleets(), restored.fleets());
        assertEquals(state.tradeRoutes(), restored.tradeRoutes());
        assertEquals(state.commercialHubs(), restored.commercialHubs());
        assertEquals(state.empires(), restored.empires());
        assertEquals(state.powerGrids(), restored.powerGrids());
        return restored;
    }

    private GameState day(GameState state) {
        var powered = ShipPowerProcessor.advanceDay(state);
        var next = state.withFleets(new FleetProcessor().processFleetMovements(powered, state.orbitalStations(), List.of()))
                .withTurn(state.turn() + 1);
        assertEquals(0, ship(next).powerState().lastUnmetEssentialKwh(), 1e-6);
        var flight = next.fleets().getFirst().location().orbitalFlight();
        assertFalse(flight != null && flight.failed());
        return next;
    }

    private GameState world(Scenario scenario) throws Exception {
        var owner = new Empire("owner", "Trader", "human", "Individualist", 1e6, 0, List.of("sol"), List.of(), Map.of(),
                List.of("rocketry", "methalox_propulsion", "hydrolox_propulsion", "electricity", "solar_power",
                        "industrial_production", "space_stations"), List.of());
        var provider = new Empire("provider", "Port operator", "human", "Individualist", 1e6, 0,
                List.of(), List.of(), Map.of(), List.of(), List.of());
        var fuel = new HashMap<String, MarketOrder>();
        PropulsionCatalog.drive(scenario.drive()).propellantMaterials(CAPACITY)
                .forEach((id, kg) -> fuel.put(id, new MarketOrder(id, kg, 0, scenario.price(), 0)));
        var depot = new HashMap<>(fuel);
        BASKET.forEach((id, kg) -> depot.put(id, new MarketOrder(id, kg, 0, 1, 0)));
        var state = GameState.builder().solarSystems(DataModelLoader.loadSolarSystems()).empires(List.of(owner, provider))
                .orbitalStations(List.of(port("low-port", "earth", 500), port("depot-port", "earth", 2000), port("moon-port", "moon", 500)))
                .commercialHubs(List.of(new CommercialHub("low-market", "low-port", 0, 1e6, CAPACITY, 10, fuel),
                        new CommercialHub("depot-market", "depot-port", 0, 1e6, CAPACITY + 2500, 10, depot),
                        new CommercialHub("moon-market", "moon-port", 0, 1e6, 0, 10, Map.of(
                                "steel", new MarketOrder("steel", 0, 1250, 3, 0),
                                "food_matrix", new MarketOrder("food_matrix", 0, 1250, 3, 0)))))
                .marketAccounts(List.of(new MarketAccount("low-market", 100000), new MarketAccount("depot-market", 100000),
                        new MarketAccount("moon-market", 100000)))
                .powerGrids(List.of(new PowerGridState("low-port", 1000, 0, 1000, 2000, 2000, false))).build();
        var blueprint = ShipBlueprintFactory.evaluate(state, new ShipDesignSpecification("design", "Freighter", "owner",
                ShipRole.CARGO_TRANSPORT, "steel", ChemicalFreighterCatalog.modules(scenario.drive()), "steel", .5));
        assertTrue(blueprint.valid(), blueprint.errors().toString());
        var ship = new ShipInstance("ship", "design", "owner", 100, 0, 0, Map.of()).withPowerState(ShipPowerState.empty());
        var fleet = new Fleet("fleet", "Trader", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship),
                FleetLocation.at(FleetLocation.Site.docked("low-port")))
                .withFuelPolicy(new FleetFuelPolicy(scenario.reserve(), scenario.reserve(), scenario.reserve()));
        var route = new TradeRoute("route", "Mixed lunar shipment", "owner", "depot-market", "moon-market", "steel",
                2500, 0, 1e6, List.of("ship"), 0, true).withRoaming(true);
        return state.withShipDesigns(List.of(blueprint.design())).withFleets(List.of(fleet)).withTradeRoutes(List.of(route));
    }

    private OrbitalStation port(String id, String body, double altitude) {
        var yard = new StationModule("yard-" + id, "Yard", StationModule.TYPE_CAPITAL_SLIPWAY,
                4, 10000, 10, 0, Map.of(), "engineer", 0, true);
        return new OrbitalStation(id, id, "sol", body, "provider", OrbitalStation.OWNERSHIP_PUBLIC_STATE,
                20, List.of(yard), Map.of(), 100, 10, 0, 0, 100, 100, "steel", 0, true).withParkingAltitudeKm(altitude);
    }

    private GameState replaceHub(GameState state, CommercialHub replacement) {
        return state.withCommercialHubs(state.commercialHubs().stream()
                .map(hub -> hub.id().equals(replacement.id()) ? replacement : hub).toList());
    }
    private CommercialHub hub(GameState state, String id) {
        return state.commercialHubs().stream().filter(hub -> hub.id().equals(id)).findFirst().orElseThrow();
    }
    private ShipInstance ship(GameState state) { return state.fleets().getFirst().ships().getFirst(); }
    private double cash(GameState state) {
        return state.empires().stream().filter(empire -> empire.id().equals("owner")).findFirst().orElseThrow().treasuryCredits();
    }

    private String report(List<Row> rows) {
        var text = new StringBuilder("# Staged lunar trade audit\n\n")
                .append("An empty freighter buys 120,000 kg main fuel and 500 kWh battery charge at a finite-stock 500 km Earth port. ")
                .append("It pays all parking maneuvers to a fixture-built 2,000 km depot, buys replacement fuel there and loads ")
                .append("1,250 kg steel plus 1,250 kg food at 1 credit/kg. A 500 km Moon port pays 6,000 credits for docking and sale. ")
                .append("The active parking order and the lunar flight after its first daily tick survive reload and resume ordinary power and movement ticks.\n\n")
                .append("Fuel prices are sensitivity inputs. Consumption result counts cargo, both legs' consumed fuel and commissioning charge. ")
                .append("Cash change additionally pays for fuel remaining aboard. Rejected lunar shipments retain purchased fuel without buying cargo. ")
                .append("The separate cargo route ledger omits staging and supplies bought before loading. Ship and port construction, wages, ")
                .append("depreciation, stock replenishment, taxes and exact rendezvous geometry are excluded. Separate lifecycle tests exercise automatic one-stop ")
                .append("parking-port selection with combined access economics, live arrival revalidation and no remote stock reservation.\n\n")
                .append("| Drive | Reserve | Fuel price | Delivered | Staging days | Lunar days | Staging fuel kg | Lunar fuel kg | Remaining kg | Cash change | Consumption result |\n")
                .append("|---|---:|---:|---|---:|---:|---:|---:|---:|---:|---:|\n");
        for (var row : rows) text.append(String.format(Locale.ROOT,
                "| %s | %.0f%% | %.2f | %s | %d | %d | %.2f | %.2f | %.2f | %.2f | %.2f |%n",
                row.scenario().drive(), row.scenario().reserve() * 100, row.scenario().price(), row.delivered(),
                row.stagingDays(), row.lunarDays(), row.stagingFuel(), row.lunarFuel(), row.remaining(), row.cashChange(), row.result()));
        text.append("\n## Rejected lunar legs\n\n");
        rows.stream().filter(row -> !row.delivered()).forEach(row -> text.append("- ").append(row.scenario())
                .append(": ").append(row.reason()).append(".\n"));
        return text.toString();
    }
}
