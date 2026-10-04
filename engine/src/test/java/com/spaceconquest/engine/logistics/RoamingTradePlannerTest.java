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
