package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TradeLegReadinessTest {
    private OrbitalStation station(String id, String system) {
        return new OrbitalStation(id, id, system, "", "owner", "PUBLIC_STATE", 10, List.of(), Map.of(),
                0, 0, 0, 0, 100, 100, "steel", 0, true);
    }

    private CommercialHub port(String id, String station, boolean stock, double price) {
        var orders = new java.util.HashMap<String, MarketOrder>();
        orders.put("steel", new MarketOrder("steel", id.equals("source") ? 100 : 0, 100, price, 1));
        if (stock) {
            orders.put("rp1_kerosene", new MarketOrder("rp1_kerosene", 10000, 0, 1, 0));
            orders.put("liquid_oxygen", new MarketOrder("liquid_oxygen", 10000, 0, 1, 0));
        }
        return new CommercialHub(id, station, 0, 100000, stock ? 20100 : 0, 10, orders);
    }

    private GameState state(double mainFuel, double electricalFuel, boolean sourceStock) {
        var profile = new ShipPowerProfile(0, 100, 0, 0, 0, 0, 5000, 0, 1, 2, 0, .65,
                Map.of("rp1", new ShipPowerProfile.Fuel("rp1_kerosene", "liquid_oxygen", .28, 1.008)));
        var design = new ShipDesign("design", "Freighter", "owner", ShipRole.CARGO_TRANSPORT, "steel",
                List.of("mod_chemical_rocket"), "steel", 0, 1000, 1000, 1000, 100, 1, 0,
                2000, false, false, ShipManufacturingProfile.baseline(), profile);
        var power = new ShipPowerState(Map.of("rp1_kerosene", electricalFuel * .28, "liquid_oxygen", electricalFuel * .72),
                "rp1", "uranium", 0, true, 1, 1, 0, 0, 0, 0, 0);
        var ship = new ShipInstance("ship", "design", "owner", 100, 0, mainFuel, Map.of()).withPowerState(power);
        var fleet = new Fleet("fleet", "Freighter", "owner", "a", "", 0, 0, 0, false, "PASSIVE", List.of(ship),
                FleetLocation.at(FleetLocation.Site.docked("station_a")));
        return GameState.builder().solarSystems(List.of(
                new SolarSystem("a", "A", "", 0, 0, 0, 1, 1, "yellow", List.of(), List.of()),
                new SolarSystem("b", "B", "", 1e6 / InterstellarTravel.METERS_PER_LIGHT_YEAR, 0, 0, 1, 1, "yellow", List.of(), List.of())))
                .orbitalStations(List.of(station("station_a", "a"), station("station_b", "b"), station("station_c", "a")))
                .commercialHubs(List.of(port("source", "station_a", sourceStock, 1), port("destination", "station_b", false, 10)))
                .marketAccounts(List.of(new MarketAccount("source", 0), new MarketAccount("destination", 10000)))
                .empires(List.of(new Empire("owner", "Owner", "human", "Individualist", 100000, 0,
                        List.of("a", "b"), List.of(), Map.of(), List.of("rocketry"), List.of())))
                .shipDesigns(List.of(design)).fleets(List.of(fleet)).build();
    }

    @Test void nextPortNeedsNoKnownReturnAndRetainsRealApproachFuel() {
        var state = state(1000, 4000, false);
        var fleet = state.fleets().getFirst();
        var destination = state.commercialHubs().getLast();
        assertTrue(TradeLegReadiness.inspect(state, fleet, destination).ready());
        var local = LocalTravel.plan(state, fleet, FleetLocation.Site.deepSpace());
        var departed = LocalTravel.depart(fleet, FleetLocation.Site.deepSpace(), local)
                .withLocation(FleetLocation.at(FleetLocation.Site.deepSpace()));
        var plan = TradeLegReadiness.crossing(state, departed, destination);
        assertNotNull(plan);
        double remaining = departed.ships().getFirst().currentFuelKg() - plan.fuelBudgetKg().get("ship");
        assertTrue(remaining >= LocalTravel.requiredPropellantKg(state.shipDesigns().getFirst(), departed.ships().getFirst(),
                FleetLocation.Site.deepSpace(), FleetLocation.Site.docked("station_b")));
        assertFalse(FleetPortReadiness.inspect(state, fleet, destination).available());
        assertEquals(1000, fleet.ships().getFirst().currentFuelKg());
    }

    @Test void failedPortPurchasesPreserveCashStockAndTravel() {
        var dry = state(0, 0, false);
        assertSame(dry, TradeLegReadiness.prepare(dry, dry.fleets().getFirst(), dry.commercialHubs().getLast()));
        assertFalse(TradeLegReadiness.inspect(dry, dry.fleets().getFirst(), dry.commercialHubs().getLast()).ready());
        var stocked = state(0, 0, true);
        var ready = TradeLegReadiness.prepare(stocked, stocked.fleets().getFirst(), stocked.commercialHubs().getLast());
        assertTrue(TradeLegReadiness.inspect(ready, ready.fleets().getFirst(), ready.commercialHubs().getLast()).ready());
        assertEquals(1000, ready.fleets().getFirst().ships().getFirst().currentFuelKg());
        assertTrue(ready.empires().getFirst().treasuryCredits() < stocked.empires().getFirst().treasuryCredits());
        assertTrue(ready.commercialHubs().getFirst().activeOrders().get("liquid_oxygen").supplyKg() < 10000);
        assertFalse(ready.fleets().getFirst().hasInterstellarOrder());
        var poor = stocked.withEmpires(List.of(new Empire("owner", "Owner", "human", "Individualist", 0, 0,
                List.of(), List.of(), Map.of(), List.of("rocketry"), List.of())));
        assertSame(poor, TradeLegReadiness.prepare(poor, poor.fleets().getFirst(), poor.commercialHubs().getLast()));
    }

    @Test void marketChoicePrefersAStockedProfitablePortAndChargesResupplyToTheRoute() {
        var state = state(0, 0, true);
        var stocked = port("stocked", "station_c", true, 5);
        var route = new TradeRoute("route", "Trade", "owner", "source", "destination", "steel", 20, 0, 100,
                List.of("ship"), 0, true);
        state = state.withCommercialHubs(List.of(state.commercialHubs().getFirst(), state.commercialHubs().getLast(), stocked))
                .withMarketAccounts(List.of(new MarketAccount("source", 0), new MarketAccount("destination", 10000),
                        new MarketAccount("stocked", 10000))).withTradeRoutes(List.of(route));
        var next = new LogisticsProcessor().processTradeRoutes(state).state();
        assertEquals("stocked", next.tradeRoutes().getFirst().destinationEntityId());
        assertTrue(next.fleets().getFirst().location().inTransit());
        assertTrue(next.tradeRoutes().getFirst().dailyOperatingResultCredits() < 0);
    }

    @Test void unsafeCrossingDoesNotBuyCargoOrLaunchWhileWaitingForSupplies() {
        var state = state(0, 0, false);
        state = state.withTradeRoutes(List.of(new TradeRoute("route", "Trade", "owner", "source", "destination", "steel", 20, 0, 100,
                List.of("ship"), 0, true)));
        var blocked = new LogisticsProcessor().processTradeRoutes(state).state();
        assertEquals(0, blocked.tradeRoutes().getFirst().onboardKg());
        assertEquals(state.empires(), blocked.empires());
        assertEquals(state.commercialHubs(), blocked.commercialHubs());
        assertFalse(blocked.fleets().getFirst().location().inTransit());
    }

    @Test void electricityMustLastThroughDockingRatherThanOnlyTheCrossing() {
        boolean found = false;
        for (double electrical = 100; electrical <= 500; electrical += 5) {
            var state = state(1000, electrical, false);
            var report = TradeLegReadiness.inspect(state, state.fleets().getFirst(), state.commercialHubs().getLast());
            if (report.explanation().contains("destination approach electricity")) {
                assertFalse(report.ready());
                found = true;
                break;
            }
        }
        assertTrue(found, "A crossing-funded ship must still be rejected when its port approach is not funded.");
    }

    @Test void automatedOneWayShipmentActuallyDocksAndSellsBeforeConsideringAnotherLeg() {
        var state = state(0, 0, true).withTradeRoutes(List.of(new TradeRoute("route", "Trade", "owner",
                "source", "destination", "steel", 20, 0, 100, List.of("ship"), 0, true)));
        var logistics = new LogisticsProcessor();
        var movement = new FleetProcessor();
        for (int day = 0; day < 20 && state.tradeRoutes().getFirst().totalVolumeMovedKg() == 0; day++) {
            state = logistics.processTradeRoutes(state).state();
            state = state.withFleets(movement.processFleetMovements(ShipPowerProcessor.advanceDay(state), List.of(), List.of()));
        }
        assertEquals(20, state.tradeRoutes().getFirst().totalVolumeMovedKg(), 1e-6);
        assertEquals(0, state.tradeRoutes().getFirst().onboardKg());
        assertTrue(state.fleets().getFirst().location().isAt(FleetLocation.Site.docked("station_b")));
        assertFalse(state.fleets().getFirst().hasInterstellarOrder());
        assertEquals(TradeRoute.RETURNING, state.tradeRoutes().getFirst().phase());
        assertTrue(state.fleets().getFirst().ships().getFirst().generatorFuelMassKg() > 0);
    }
}
