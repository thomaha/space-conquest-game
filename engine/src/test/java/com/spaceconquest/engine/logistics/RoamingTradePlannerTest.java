package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RoamingTradePlannerTest {
    @TempDir Path directory;

    private GameState mixedState(boolean roaming) {
        var state = state(false, roaming);
        var source = state.commercialHubs().getFirst(); var buyer = state.commercialHubs().get(1);
        var sourceOrders = new java.util.HashMap<>(source.activeOrders());
        sourceOrders.put("refined_copper", new MarketOrder("refined_copper", 100, 0, 1, 0));
        var buyerOrders = new java.util.HashMap<>(buyer.activeOrders());
        buyerOrders.put("steel", new MarketOrder("steel", 0, 5, 20, 1));
        buyerOrders.put("refined_copper", new MarketOrder("refined_copper", 0, 15, 20, 1));
        var third = state.commercialHubs().getLast();
        var thirdOrders = new java.util.HashMap<>(third.activeOrders());
        thirdOrders.put("refined_copper", new MarketOrder("refined_copper", 0, 0, 20, 0));
        return state.withCommercialHubs(List.of(
                new CommercialHub(source.id(), source.entityId(), 0, 100000, 20200, 10, sourceOrders),
                new CommercialHub(buyer.id(), buyer.entityId(), 0, 100000, 20000, 10, buyerOrders),
                new CommercialHub(third.id(), third.entityId(), 0, 100000, 20000, 10, thirdOrders)));
    }

    @Test void fixedAndRoamingTradersCarryAndSellSeveralGoodsOnOneJourney() {
        for (boolean roaming : List.of(true, false)) {
            var initial = mixedState(roaming);
            var processor = new LogisticsProcessor();
            var loaded = processor.processTradeRoutes(initial).state();
            var manifest = loaded.tradeRoutes().getFirst().cargoManifest();
            assertEquals(new TradeCargo(5, 5), manifest.get("steel"));
            assertEquals(new TradeCargo(15, 15), manifest.get("refined_copper"));
            assertEquals(20, loaded.tradeRoutes().getFirst().onboardKg());
            assertEquals(20, loaded.tradeRoutes().getFirst().transferAmountPerTurnKg());
            double paid = initial.empires().getFirst().treasuryCredits() - loaded.empires().getFirst().treasuryCredits();
            assertEquals(-(paid - loaded.tradeRoutes().getFirst().onboardCostCredits()),
                    loaded.tradeRoutes().getFirst().cumulativeOperatingResultCredits(), 1e-6,
                    "Only the selected preview's real fuel purchase may enter the ledger.");
            assertEquals(95, loaded.commercialHubs().getFirst().activeOrders().get("steel").supplyKg());
            assertEquals(85, loaded.commercialHubs().getFirst().activeOrders().get("refined_copper").supplyKg());
            var arrived = loaded.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(loaded), List.of(), List.of()));
            var sold = processor.processTradeRoutes(arrived).state();
            assertEquals(20, sold.tradeRoutes().getFirst().totalVolumeMovedKg());
            assertTrue(sold.tradeRoutes().getFirst().cargoManifest().isEmpty());
            assertEquals(roaming ? TradeRoute.LOADING : TradeRoute.RETURNING, sold.tradeRoutes().getFirst().phase());
            assertEquals(5, sold.commercialHubs().get(1).activeOrders().get("steel").supplyKg());
            assertEquals(15, sold.commercialHubs().get(1).activeOrders().get("refined_copper").supplyKg());
        }
    }

    @Test void mixedPartialSalesCancellationAndSaveLoadKeepEveryGoodsCostBasis() throws Exception {
        var processor = new LogisticsProcessor();
        var loaded = processor.processTradeRoutes(mixedState(true)).state();
        var arrived = loaded.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(loaded), List.of(), List.of()))
                .withMarketAccounts(List.of(new MarketAccount("source", 10000), new MarketAccount("buyer", 80), new MarketAccount("third", 10000)));
        arrived = arrived.withTradeRoutes(List.of(arrived.tradeRoutes().getFirst().withActive(false)));
        var partial = processor.processTradeRoutes(arrived).state();
        var route = partial.tradeRoutes().getFirst();
        assertEquals(15, route.onboardKg());
        assertEquals(new TradeCargo(10, 10), route.cargoManifest().get("refined_copper"));
        assertEquals(new TradeCargo(5, 5), route.cargoManifest().get("steel"));
        assertEquals(15, route.onboardCostCredits());
        assertFalse(partial.fleets().getFirst().location().inTransit());
        var manager = new SaveGameManager(directory); var file = directory.resolve("mixed.scsave").toFile();
        manager.save(file, partial, 1, "2027-01-01T08:00:00");
        var restored = manager.load(file).toGameState(0, "RUNNING");
        assertEquals(route, restored.tradeRoutes().getFirst());
        var funded = restored.withMarketAccounts(List.of(new MarketAccount("source", 10000), new MarketAccount("buyer", 400), new MarketAccount("third", 10000)));
        var sold = processor.processTradeRoutes(funded).state();
        assertEquals(20, sold.tradeRoutes().getFirst().totalVolumeMovedKg());
        assertEquals(0, sold.tradeRoutes().getFirst().onboardCostCredits());
        assertFalse(sold.tradeRoutes().getFirst().isActive());
        var idle = processor.processTradeRoutes(sold).state();
        assertEquals(sold.fleets(), idle.fleets());
        assertTrue(idle.tradeRoutes().getFirst().cargoManifest().isEmpty());
    }

    @Test void missingMixedCargoWritesOffOnlyTheAffectedLotAndCopiesRemainImmutable() {
        var route = new LogisticsProcessor().processTradeRoutes(mixedState(true)).state().tradeRoutes().getFirst();
        var retained = route.withAvailableCargo(Map.of("steel", 5.0, "refined_copper", 7.5));
        assertEquals(12.5, retained.onboardKg());
        assertEquals(12.5, retained.onboardCostCredits());
        assertEquals(route.cumulativeOperatingResultCredits() - 7.5, retained.cumulativeOperatingResultCredits(), 1e-6);
        assertEquals(route.cargoManifest(), route.resetDailyResult().withOperatingCost(2).withStatus("Wait")
                .withDestination("third").withRoaming(false).withActive(false).cargoManifest());
        assertThrows(UnsupportedOperationException.class, () -> route.cargoManifest().clear());
        assertEquals(20, route.onboardKg());
    }

    @Test void mixedBudgetIsSharedAcrossGoodsAndRejectedBasketsSpendNothing() {
        var state = mixedState(true).withMarketAccounts(List.of(new MarketAccount("source", 10000),
                new MarketAccount("buyer", 160), new MarketAccount("third", 10000)));
        var selected = RoamingTradePlanner.choose(state, state.tradeRoutes().getFirst(), state.fleets().getFirst());
        assertNotNull(selected.shipment());
        assertTrue(selected.shipment().route().onboardKg() <= 10);
        assertNull(new LogisticsProcessor().previewBasket(state, state.tradeRoutes().getFirst(), Map.of("steel", 5.0, "refined_copper", 15.0)));
        assertEquals(100, state.commercialHubs().getFirst().activeOrders().get("steel").supplyKg());
        assertEquals(10000, state.empires().getFirst().treasuryCredits());
    }

    @Test void mixedDestinationStorageIsSharedAndUnrelatedCargoIsNotSold() {
        var initial = mixedState(true); var buyer = initial.commercialHubs().get(1);
        var state = initial.withCommercialHubs(List.of(initial.commercialHubs().getFirst(),
                new CommercialHub(buyer.id(), buyer.entityId(), 0, buyer.currentStoredWeightKg() + 15,
                        buyer.currentStoredWeightKg(), 10, buyer.activeOrders()), initial.commercialHubs().getLast()));
        var fleet = state.fleets().getFirst(); var ship = fleet.ships().getFirst();
        ship = new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(), ship.currentShieldHealth(),
                ship.currentFuelKg(), Map.of("food_matrix", 100.0), ship.passengerCount(), ship.passengerRaceId(), ship.transitMode(), ship.powerState(), ship.supplyState());
        state = state.withFleets(List.of(fleet.withShips(List.of(ship))));
        var processor = new LogisticsProcessor(); var loaded = processor.processTradeRoutes(state).state();
        assertEquals(15, loaded.tradeRoutes().getFirst().onboardKg());
        var arrived = loaded.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(loaded), List.of(), List.of()));
        var sold = processor.processTradeRoutes(arrived).state();
        assertEquals(15, sold.tradeRoutes().getFirst().totalVolumeMovedKg());
        assertEquals(100, sold.fleets().getFirst().ships().getFirst().storedCargoKg().get("food_matrix"));
        assertEquals(buyer.currentStoredWeightKg() + 15, sold.commercialHubs().get(1).currentStoredWeightKg());
    }

    @Test void anOlderSingleCargoSaveLoadsItsManifestFromLegacyFields() throws Exception {
        var route = state(false, true).tradeRoutes().getFirst().withLoadedCargo(8, 24);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var tree = mapper.valueToTree(route);
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree).remove("cargoManifest");
        var loaded = mapper.treeToValue(tree, TradeRoute.class);
        assertEquals(Map.of("steel", new TradeCargo(8, 24)), loaded.cargoManifest());
        assertEquals(route, loaded);
    }

    @Test void mixedOrbitalPickupPaysOnePhysicalLaunchAndRetainsItsCostBasis() {
        var initial = mixedState(true); var source = initial.commercialHubs().getFirst();
        var earth = new Planet("earth", "Earth", "", 1, 9.81, SolarRadiation.AU_KM,
                0, 12000, "terrestrial", "air", false, 0, List.of(), List.of(), List.of());
        var fleet = initial.fleets().getFirst().withLocation(FleetLocation.at(FleetLocation.Site.orbit("earth")));
        var state = initial.toBuilder().solarSystems(List.of(new SolarSystem("sol", "Sol", "", 0, 0, 0,
                        SolarRadiation.SOLAR_MASS_KG, 1392000, "yellow", List.of(earth), List.of())))
                .orbitalStations(initial.orbitalStations().stream().map(station -> new OrbitalStation(station.id(), station.name(),
                        station.systemId(), "earth", station.ownerEntityId(), "PUBLIC_STATE", 10, List.of(), Map.of(),
                        0, 0, 0, 0, 100, 100, "steel", 0, true)).toList())
                .empires(List.of(initial.empires().getFirst(), new Empire("provider", "Launch provider", "human", "Individualist",
                        0, 0, List.of(), List.of(), Map.of(), List.of(), List.of())))
                .commercialHubs(List.of(new CommercialHub(source.id(), "earth", 0, 100000, 20200, 10, source.activeOrders()),
                        initial.commercialHubs().get(1), initial.commercialHubs().getLast()))
                .fleets(List.of(fleet)).spaceElevators(List.of(new com.spaceconquest.engine.macrostructure.SpaceElevator(
                        "lift", "earth", "provider", 10000, .95, 100, true)))
                .powerGrids(List.of(new com.spaceconquest.engine.industry.PowerGridState("earth", 100, 0, 100, 0, 0, false))).build();
        var selected = new LogisticsProcessor().previewBasket(state, state.tradeRoutes().getFirst(), Map.of("steel", 5.0, "refined_copper", 15.0));
        assertNotNull(selected);
        assertEquals(20, selected.route().onboardKg());
        assertEquals(2, selected.route().cargoManifest().size());
        assertTrue(selected.route().onboardCostCredits() > 20);
        assertEquals(1, selected.state().launchActivities().size());
        assertTrue(state.launchActivities().isEmpty());
    }

    @Test void smallerLoadLeavesCashForPaidFuelAndKeepsTheConfiguredLimit() {
        for (boolean roaming : List.of(true, false)) {
            var state = state(true, roaming).withEmpires(List.of(new Empire("owner", "Owner", "human", "Individualist", 20, 0,
                    List.of("sol"), List.of(), Map.of(), List.of("rocketry"), List.of())));
            assertNull(new LogisticsProcessor().previewShipment(state, state.tradeRoutes().getFirst(), 20));
            var next = new LogisticsProcessor().processTradeRoutes(state).state();
            var route = next.tradeRoutes().getFirst();
            assertTrue(route.onboardKg() > 0 && route.onboardKg() < 20, "Must shrink the cargo purchase to fund departure.");
            assertEquals(20, route.transferAmountPerTurnKg());
            assertTrue(next.fleets().getFirst().location().inTransit());
            assertTrue(next.empires().getFirst().treasuryCredits() >= 0);
            assertEquals(100, state.commercialHubs().getFirst().activeOrders().get("steel").supplyKg());
            assertEquals(0, state.fleets().getFirst().ships().getFirst().currentFuelKg());
            assertEquals(100 - route.onboardKg(), next.commercialHubs().getFirst().activeOrders().get("steel").supplyKg(), 1e-6);
        }
    }

    @Test void aLargeConfiguredLimitStillFindsASmallFundedShipment() {
        var state = state(true, true).withEmpires(List.of(new Empire("owner", "Owner", "human", "Individualist", 20, 0,
                List.of("sol"), List.of(), Map.of(), List.of("rocketry"), List.of())));
        var route = new TradeRoute("route", "Trader", "owner", "source", "buyer", "steel", 1e9, 0, 1000,
                List.of("ship"), 0, true).withRoaming(true);
        var selected = TradeShipmentSizing.choose(state, route, 1e9);
        assertNotNull(selected);
        assertTrue(selected.shipment().route().onboardKg() > 0 && selected.shipment().route().onboardKg() < 20);
        assertEquals(1e9, selected.shipment().route().transferAmountPerTurnKg());
        assertEquals(100, state.commercialHubs().getFirst().activeOrders().get("steel").supplyKg());
    }

    @Test void reducedCandidateStillRequiresAProfitableLeg() {
        var state = state(true, true);
        var source = state.commercialHubs().getFirst();
        var orders = new java.util.HashMap<>(source.activeOrders());
        orders.put("rp1_kerosene", new MarketOrder("rp1_kerosene", 10000, 0, 10000, 0));
        orders.put("liquid_oxygen", new MarketOrder("liquid_oxygen", 10000, 0, 10000, 0));
        state = state.withCommercialHubs(List.of(new CommercialHub(source.id(), source.entityId(), 0, 100000, 20100, 10, orders),
                state.commercialHubs().get(1), state.commercialHubs().getLast()));
        assertNull(TradeShipmentSizing.choose(state, state.tradeRoutes().getFirst(), 20));
        assertEquals(100, state.commercialHubs().getFirst().activeOrders().get("steel").supplyKg());
        assertEquals(10000, state.empires().getFirst().treasuryCredits());
    }
    private OrbitalStation station(String id) {
        return new OrbitalStation(id, id, "sol", "", "owner", "PUBLIC_STATE", 10, List.of(), Map.of(),
                0, 0, 0, 0, 100, 100, "steel", 0, true);
    }
    private CommercialHub hub(String id, String goods, double supply, double demand, double price) {
        return new CommercialHub(id, id + "_station", 0, 100000, supply + 20000, 10, Map.of(
                goods, new MarketOrder(goods, supply, demand, price, 1),
                "rp1_kerosene", new MarketOrder("rp1_kerosene", 10000, 0, .001, 0),
                "liquid_oxygen", new MarketOrder("liquid_oxygen", 10000, 0, .001, 0)));
    }
    private GameState state(boolean empty, boolean roaming) {
        var p = new ShipPowerProfile(0, 100, 0, 0, 0, 0, 5000, 0, 1, 2, 0, .65,
                Map.of("rp1", new ShipPowerProfile.Fuel("rp1_kerosene", "liquid_oxygen", .28, 1.008)));
        var design = new ShipDesign("design", "Freighter", "owner", ShipRole.CARGO_TRANSPORT, "steel",
                List.of("mod_chemical_rocket"), "steel", 0, 1000, 1000, 1000, 100, 1, 0, 2000,
                false, false, ShipManufacturingProfile.baseline(), p);
        var power = new ShipPowerState(Map.of("rp1_kerosene", empty ? 0.0 : 1120.0, "liquid_oxygen", empty ? 0.0 : 2880.0),
                "rp1", "uranium", 0, true, 1, 1, 0, 0, 0, 0, 0);
        var ship = new ShipInstance("ship", "design", "owner", 100, 0, empty ? 0 : 1000, Map.of()).withPowerState(power);
        var fleet = new Fleet("fleet", "Freighter", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship),
                FleetLocation.at(FleetLocation.Site.docked("source_station")));
        var buyer = hub("buyer", "steel", 0, 100, 20);
        var buyerOrders = new java.util.HashMap<>(buyer.activeOrders());
        buyerOrders.put("refined_copper", new MarketOrder("refined_copper", 100, 0, 1, 0));
        buyer = new CommercialHub(buyer.id(), buyer.entityId(), 0, 100000, 20100, 10, buyerOrders);
        return GameState.builder().solarSystems(List.of(new SolarSystem("sol", "Sol", "", 0, 0, 0, 1, 1, "yellow", List.of(), List.of())))
                .orbitalStations(List.of(station("source_station"), station("buyer_station"), station("third_station")))
                .commercialHubs(List.of(hub("source", "steel", 100, 0, 1), buyer, hub("third", "refined_copper", 0, 100, 20)))
                .marketAccounts(List.of(new MarketAccount("source", 10000), new MarketAccount("buyer", 10000), new MarketAccount("third", 10000)))
                .empires(List.of(new Empire("owner", "Owner", "human", "Individualist", 10000, 0,
                        List.of("sol"), List.of(), Map.of(), List.of("rocketry"), List.of())))
                .shipDesigns(List.of(design)).fleets(List.of(fleet)).tradeRoutes(List.of(new TradeRoute("route", "Trader", "owner",
                        "source", "buyer", "steel", 20, 0, 100, List.of("ship"), 0, true).withRoaming(roaming))).build();
    }

    @Test void sellsThenBuysDifferentCargoAndTravelsToAThirdPort() {
        var state = state(false, true);
        var processor = new LogisticsProcessor();
        var movement = new FleetProcessor();
        state = processor.processTradeRoutes(state).state();
        assertEquals("buyer", state.tradeRoutes().getFirst().destinationEntityId());
        state = state.withFleets(movement.processFleetMovements(ShipPowerProcessor.advanceDay(state), List.of(), List.of()));
        state = processor.processTradeRoutes(state).state();
        assertEquals(20, state.tradeRoutes().getFirst().totalVolumeMovedKg());
        assertEquals("buyer", state.tradeRoutes().getFirst().originEntityId());
        assertEquals(TradeRoute.LOADING, state.tradeRoutes().getFirst().phase());
        assertFalse(state.fleets().getFirst().location().inTransit());
        state = processor.processTradeRoutes(state).state();
        assertEquals("third", state.tradeRoutes().getFirst().destinationEntityId());
        assertEquals("refined_copper", state.tradeRoutes().getFirst().materialId());
        assertEquals(20, state.fleets().getFirst().ships().getFirst().storedCargoKg().get("refined_copper"));
        assertTrue(state.fleets().getFirst().location().inTransit());
    }

    @Test void priceChangesLeaveTraderSafelyAtPortWithoutBuyingCargo() {
        var initial = state(false, true);
        var loss = hub("buyer", "steel", 0, 100, 1);
        var state = initial.withCommercialHubs(List.of(initial.commercialHubs().getFirst(), loss, initial.commercialHubs().getLast()));
        var next = new LogisticsProcessor().processTradeRoutes(state).state();
        assertEquals(state.fleets(), next.fleets());
        assertEquals(state.commercialHubs(), next.commercialHubs());
        assertEquals(state.empires(), next.empires());
        assertTrue(next.tradeRoutes().getFirst().status().contains("Waiting at port"));
    }

    @Test void scarceFuelAndUnaffordableSuppliesDoNotCommitCandidatePurchases() {
        var initial = state(true, true);
        var source = initial.commercialHubs().getFirst();
        var missingOxidizer = new java.util.HashMap<>(source.activeOrders());
        missingOxidizer.remove("liquid_oxygen");
        var state = initial.withCommercialHubs(List.of(new CommercialHub(source.id(), source.entityId(), 0, 100000, 10000, 10,
                missingOxidizer), initial.commercialHubs().get(1), initial.commercialHubs().getLast()));
        var next = new LogisticsProcessor().processTradeRoutes(state).state();
        assertEquals(state.fleets(), next.fleets());
        assertEquals(state.commercialHubs(), next.commercialHubs());
        assertEquals(state.empires(), next.empires());
        var poor = initial.withEmpires(List.of(new Empire("owner", "Owner", "human", "Individualist", 0, 0,
                List.of("sol"), List.of(), Map.of(), List.of("rocketry"), List.of())));
        next = new LogisticsProcessor().processTradeRoutes(poor).state();
        assertEquals(poor.fleets(), next.fleets());
        assertEquals(poor.commercialHubs(), next.commercialHubs());
    }

    @Test void previewIncludesRealFuelCostsAndDoesNotMutateTheInitialState() {
        var state = state(true, true);
        var selected = RoamingTradePlanner.choose(state, state.tradeRoutes().getFirst(), state.fleets().getFirst());
        assertNotNull(selected.shipment());
        assertTrue(selected.profitCredits() < 300);
        assertEquals(1, selected.days());
        assertEquals(0, state.fleets().getFirst().ships().getFirst().currentFuelKg());
        assertEquals(100, state.commercialHubs().getFirst().activeOrders().get("steel").supplyKg());
        assertEquals(0, state.tradeRoutes().getFirst().onboardKg());
    }

    @Test void fixedRouteStillReturnsToItsConfiguredOriginAfterSale() {
        var state = state(false, false);
        var processor = new LogisticsProcessor();
        state = processor.processTradeRoutes(state).state();
        state = state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), List.of(), List.of()));
        state = processor.processTradeRoutes(state).state();
        assertEquals("source", state.tradeRoutes().getFirst().originEntityId());
        assertEquals(TradeRoute.RETURNING, state.tradeRoutes().getFirst().phase());
        assertTrue(state.fleets().getFirst().location().inTransit());
    }

    @Test void modeStatusAndContinuationSurviveSaveLoadAndAllFinancialCopies() throws Exception {
        var state = new LogisticsProcessor().processTradeRoutes(state(false, true)).state();
        var route = state.tradeRoutes().getFirst();
        assertTrue(route.resetDailyResult().withOperatingCost(1).withDestination("third").withAvailableCargo(10).roaming());
        var manager = new SaveGameManager(directory);
        var file = directory.resolve("roaming.scsave").toFile();
        manager.save(file, state, 1, "2027-01-01T08:00:00");
        var loaded = manager.load(file).toGameState(0, "RUNNING");
        assertEquals(route, loaded.tradeRoutes().getFirst());
        var expected = new LogisticsProcessor().processTradeRoutes(state).state();
        var actual = new LogisticsProcessor().processTradeRoutes(loaded).state();
        assertEquals(expected.fleets(), actual.fleets());
        assertEquals(expected.tradeRoutes(), actual.tradeRoutes());
    }

    @Test void buyerCashAndDemandLimitTheShipmentWithoutChangingItsConfiguredLimit() {
        var state = state(false, true).withMarketAccounts(List.of(new MarketAccount("source", 10000),
                new MarketAccount("buyer", 80), new MarketAccount("third", 10000)));
        var selected = RoamingTradePlanner.choose(state, state.tradeRoutes().getFirst(), state.fleets().getFirst());
        assertNotNull(selected.shipment());
        assertEquals(5, selected.shipment().route().onboardKg(), 1e-6);
        assertEquals(20, selected.shipment().route().transferAmountPerTurnKg());
        var cashless = state.withMarketAccounts(List.of(new MarketAccount("source", 10000),
                new MarketAccount("buyer", 0), new MarketAccount("third", 10000)));
        assertNull(RoamingTradePlanner.choose(cashless, cashless.tradeRoutes().getFirst(), cashless.fleets().getFirst()).shipment());
    }

    @Test void aPositiveCargoSpreadCanStillBeRejectedByPhysicalFuelCosts() {
        var state = state(false, true);
        var source = state.commercialHubs().getFirst();
        var orders = new java.util.HashMap<>(source.activeOrders());
        orders.put("rp1_kerosene", new MarketOrder("rp1_kerosene", 10000, 0, 100, 0));
        orders.put("liquid_oxygen", new MarketOrder("liquid_oxygen", 10000, 0, 100, 0));
        state = state.withCommercialHubs(List.of(new CommercialHub(source.id(), source.entityId(), 0, 100000, 20100, 10, orders),
                state.commercialHubs().get(1), state.commercialHubs().getLast()));
        var next = new LogisticsProcessor().processTradeRoutes(state).state();
        assertEquals(state.fleets(), next.fleets());
        assertEquals(state.empires(), next.empires());
        assertTrue(next.tradeRoutes().getFirst().status().contains("Waiting at port"));
    }

    @Test void actualCrossingTimeCanMakeALowerPricedLocalMarketTheBetterTrade() {
        var state = state(false, true);
        var far = hub("far", "steel", 0, 100, 30);
        var stations = new java.util.ArrayList<>(state.orbitalStations());
        stations.add(new OrbitalStation("far_station", "Far", "far_system", "", "owner", "PUBLIC_STATE", 10,
                List.of(), Map.of(), 0, 0, 0, 0, 100, 100, "steel", 0, true));
        var ports = new java.util.ArrayList<>(state.commercialHubs()); ports.add(far);
        var accounts = new java.util.ArrayList<>(state.marketAccounts()); accounts.add(new MarketAccount("far", 10000));
        state = state.withOrbitalStations(stations).withCommercialHubs(ports).withMarketAccounts(accounts)
                .withSolarSystems(List.of(state.solarSystems().getFirst(), new SolarSystem("far_system", "Far", "",
                        1e6 / InterstellarTravel.METERS_PER_LIGHT_YEAR, 0, 0, 1, 1, "yellow", List.of(), List.of())));
        var selected = RoamingTradePlanner.choose(state, state.tradeRoutes().getFirst(), state.fleets().getFirst());
        assertEquals("buyer", selected.shipment().route().destinationEntityId());
        var distant = new LogisticsProcessor().previewShipment(state, state.tradeRoutes().getFirst().withMarketChoice("steel", "far"));
        assertNotNull(distant);
        var quote = RoamingTradeCost.quote(state, distant.preparedFleet(), state.commercialHubs().getFirst(), far);
        assertNotNull(quote);
        assertTrue(quote.days() > selected.days());
    }
}
