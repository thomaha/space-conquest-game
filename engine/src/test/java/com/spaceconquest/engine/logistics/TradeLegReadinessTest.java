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
        var local = TradeLegReadiness.departureLocal(state, fleet);
        var departed = LocalTravel.projectedArrival(fleet, FleetLocation.Site.deepSpace(), local);
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

    private GameState localState(double mainFuel, boolean stock) {
        var state = state(mainFuel, 4000, stock);
        return state.withCommercialHubs(List.of(state.commercialHubs().getFirst(),
                port("destination", "station_c", false, 10)));
    }

    @Test void safeSlowLocalLegBuysRealFuelOnlyWhenItImprovesArrivalDay() {
        var state = localState(51, true); var fleet = state.fleets().getFirst();
        var target = state.commercialHubs().getLast(); var site = FleetPositioning.hubSite(state, target);
        assertTrue(TradeLegReadiness.inspect(state, fleet, target).ready());
        double oldDays = Math.ceil(LocalTravel.plan(state, fleet, site).days());
        assertTrue(oldDays > 1);
        var paid = TradeLegReadiness.prepare(state, fleet, target);
        var prepared = paid.fleets().getFirst();
        assertTrue(TradeLegReadiness.inspect(paid, prepared, target).ready());
        assertTrue(Math.ceil(LocalTravel.plan(paid, prepared, site).days()) < oldDays);
        double purchased = prepared.ships().getFirst().currentFuelKg() - 51;
        assertTrue(purchased > 0);
        assertEquals(purchased * .28, 10000 - paid.commercialHubs().getFirst().activeOrders().get("rp1_kerosene").supplyKg(), 1e-6);
        assertEquals(purchased * .72, 10000 - paid.commercialHubs().getFirst().activeOrders().get("liquid_oxygen").supplyKg(), 1e-6);
        assertEquals(purchased, state.empires().getFirst().treasuryCredits() - paid.empires().getFirst().treasuryCredits(), 1e-6);
        assertSame(paid, TradeLegReadiness.prepare(paid, prepared, target));
    }

    @Test void unavailableOptionalTopUpPreservesAnAlreadySafeDeparture() {
        var dry = localState(51, false);
        assertTrue(TradeLegReadiness.inspect(dry, dry.fleets().getFirst(), dry.commercialHubs().getLast()).ready());
        assertSame(dry, TradeLegReadiness.prepare(dry, dry.fleets().getFirst(), dry.commercialHubs().getLast()));
        var stocked = localState(51, true);
        var poor = stocked.withEmpires(List.of(new Empire("owner", "Owner", "human", "Individualist", 0, 0,
                List.of(), List.of(), Map.of(), List.of("rocketry"), List.of())));
        assertSame(poor, TradeLegReadiness.prepare(poor, poor.fleets().getFirst(), poor.commercialHubs().getLast()));
    }

    @Test void emptyReturnRefillsAgainstCompletePhysicalLegAndChargesItsRoute() {
        var state = localState(50.01, true);
        assertNotNull(LocalTravel.plan(state, state.fleets().getFirst(), FleetLocation.Site.docked("station_c")));
        assertFalse(TradeLegReadiness.inspect(state, state.fleets().getFirst(), state.commercialHubs().getLast()).ready());
        var route = new TradeRoute("route", "Trade", "owner", "destination", "source", "steel", 20, 0, 100,
                List.of("ship"), 0, true).withTrip(TradeRoute.RETURNING, 0, 0);
        state = state.withTradeRoutes(List.of(route));
        var next = new LogisticsProcessor().processTradeRoutes(state).state();
        var fleet = next.fleets().getFirst();
        assertTrue(fleet.location().inTransit());
        assertEquals(FleetLocation.Site.docked("station_c"), fleet.location().destination());
        assertTrue(next.tradeRoutes().getFirst().dailyOperatingResultCredits() < 0);
        assertTrue(next.empires().getFirst().treasuryCredits() < state.empires().getFirst().treasuryCredits());
        for (int day = 0; day < Math.ceil(fleet.location().travelDays()); day++)
            next = next.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(next), List.of(), List.of()));
        assertTrue(next.fleets().getFirst().location().isAt(FleetLocation.Site.docked("station_c")));
        assertEquals(0, next.tradeRoutes().getFirst().totalVolumeMovedKg());
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

    @Test void shortDepartureToDryPortCarriesDwellElectricityOrRollsBackPaidTrials() {
        var state = state(1000, 100, false).withCommercialHubs(List.of(port("source", "station_a", false, 1),
                port("destination", "station_c", false, 10)));
        var fleet = state.fleets().getFirst(); var target = state.commercialHubs().getLast();
        var site = FleetPositioning.hubSite(state, target);
        var plan = LocalTravel.plan(state, fleet, site);
        assertTrue(ShipPowerForecast.ready(ShipPowerForecast.departure(state, fleet, site, plan, null)));
        var blocked = TradeLegReadiness.inspect(state, fleet, target);
        assertFalse(blocked.ready());
        assertTrue(blocked.explanation().contains("60-day"));
        assertSame(state, TradeLegReadiness.prepare(state, fleet, target));

        var stockedSource = state.withCommercialHubs(List.of(port("source", "station_a", true, 1), target));
        var funded = TradeLegReadiness.prepare(stockedSource, fleet, target);
        assertTrue(TradeLegReadiness.inspect(funded, funded.fleets().getFirst(), target).ready());
        var prepared = funded.fleets().getFirst();
        plan = LocalTravel.plan(funded, prepared, site);
        var arrival = LocalTravel.projectedArrival(LocalSpacePowerForecast.consume(funded, prepared, site, plan), site, plan);
        var ship = arrival.ships().getFirst(); var profile = state.shipDesigns().getFirst().powerProfile();
        assertTrue(ShipArrivalReserve.check(profile, ship.powerState(), 1, 0, ShipSolarEnvironment.DARK,
                TradePortMaintenance.DWELL_RESERVE_HOURS).ready());
        assertTrue(funded.empires().getFirst().treasuryCredits() < stockedSource.empires().getFirst().treasuryCredits());
        assertEquals(stockedSource.commercialHubs().getLast(), funded.commercialHubs().getLast());
        assertEquals(fleet.fuelPolicy(), prepared.fuelPolicy());
        assertFalse(prepared.location().inTransit());
    }

    @Test void shortStockedDestinationKeepsNormalArrivalRequirement() {
        var state = state(1000, 100, false).withCommercialHubs(List.of(port("source", "station_a", false, 1),
                port("destination", "station_c", true, 10)));
        var fleet = state.fleets().getFirst();
        assertTrue(TradeLegReadiness.inspect(state, fleet, state.commercialHubs().getLast()).ready());
        assertSame(state, TradeLegReadiness.prepare(state, fleet, state.commercialHubs().getLast()));
    }

    @Test void longerJourneyCarriesDwellInsuranceEvenWithPostedDestinationStock() {
        var state = state(1000, 4000, false);
        var arrival = state.fleets().getFirst().withLocation(FleetLocation.at(FleetLocation.Site.docked("station_c")));
        var ship = arrival.ships().getFirst();
        var limited = new ShipPowerState(Map.of("rp1_kerosene", 28.0, "liquid_oxygen", 72.0),
                "rp1", "uranium", 0, true, 1, 1, 0, 0, 0, 0, 0);
        arrival = arrival.withShips(List.of(ship.withPowerState(limited)));
        var target = port("destination", "station_c", true, 10);
        assertTrue(TradeArrivalReadiness.inspect(state, state.fleets().getFirst(), arrival, target, 2).ready());
        assertFalse(TradeArrivalReadiness.inspect(state, state.fleets().getFirst(), arrival, target, 3).ready());
        assertTrue(TradeArrivalReadiness.inspect(state, state.fleets().getFirst(),
                arrival.withShips(List.of(ship)), target, 3).ready());
    }

    @Test void discoveredShortageDoesNotBlockAnAlreadyFundedApproachFromSystemSpace() {
        var state = state(1000, 100, false);
        var fleet = state.fleets().getFirst().withLocation(FleetLocation.at(FleetLocation.Site.deepSpace()));
        var arrival = fleet.withLocation(FleetLocation.at(FleetLocation.Site.docked("station_b")));
        assertTrue(TradeArrivalReadiness.inspect(state, fleet, arrival, state.commercialHubs().getLast(), 10).ready());
        assertFalse(TradeArrivalReadiness.inspect(state, state.fleets().getFirst(), arrival,
                state.commercialHubs().getLast(), 10).ready());
    }

    @Test void queuedCrossingUsesDeparturePowerConsumptionAndMatchesItsPreview() {
        var state = state(1000, 4000, false).withTradeRoutes(List.of(new TradeRoute("route", "Trade", "owner",
                "source", "destination", "steel", 20, 0, 100, List.of("ship"), 0, true)));
        var departed = new LogisticsProcessor().processTradeRoutes(state).state();
        var actual = departed.fleets().getFirst();
        assertTrue(actual.hasInterstellarOrder());
        assertTrue(actual.location().inTransit());
        var launch = state.fleets().getFirst().withShips(actual.ships());
        var local = TradeLegReadiness.departureLocal(departed, launch);
        var powerConsumed = LocalSpacePowerForecast.consume(departed, launch, FleetLocation.Site.deepSpace(), local);
        assertTrue(powerConsumed.ships().getFirst().generatorFuelMassKg() < launch.ships().getFirst().generatorFuelMassKg());
        var crossingFleet = LocalTravel.projectedArrival(powerConsumed, FleetLocation.Site.deepSpace(), local);
        var expected = TradeLegReadiness.crossing(departed, crossingFleet, departed.commercialHubs().getLast());
        assertNotNull(expected);
        assertEquals(expected.days(), actual.interstellarTravelDays(), 1e-9);
        assertEquals(expected.accelerationMps2(), actual.interstellarAccelerationMps2(), 1e-9);
        assertEquals(expected.peakSpeedMps(), actual.interstellarPeakSpeedMps(), 1e-9);
        assertEquals(expected.fuelBudgetKg(), actual.interstellarFuelBudgetKg());
    }

    @Test void automatedOneWayShipmentActuallyDocksAndSellsBeforeConsideringAnotherLeg() {
        var state = state(0, 0, true).withTradeRoutes(List.of(new TradeRoute("route", "Trade", "owner",
                "source", "destination", "steel", 20, 0, 100, List.of("ship"), 0, true)));
        var logistics = new LogisticsProcessor();
        var movement = new FleetProcessor();
        boolean dockedBeforeSale = false;
        for (int day = 0; day < 180 && state.tradeRoutes().getFirst().totalVolumeMovedKg() == 0; day++) {
            if (state.fleets().getFirst().location().isAt(FleetLocation.Site.docked("station_b"))) {
                dockedBeforeSale = true;
                assertTrue(state.fleets().getFirst().ships().getFirst().currentFuelKg() >= 50 - 1e-6,
                        "Departure, crossing and approach must leave the contingency reserve aboard.");
                assertEquals(20, state.tradeRoutes().getFirst().onboardKg());
                assertEquals(0, state.tradeRoutes().getFirst().totalVolumeMovedKg());
            }
            state = logistics.processTradeRoutes(state).state();
            state = state.withFleets(movement.processFleetMovements(ShipPowerProcessor.advanceDay(state), List.of(), List.of()));
        }
        assertEquals(20, state.tradeRoutes().getFirst().totalVolumeMovedKg(), 1e-6);
        assertEquals(0, state.tradeRoutes().getFirst().onboardKg());
        assertTrue(dockedBeforeSale);
        assertEquals(TradeRoute.RETURNING, state.tradeRoutes().getFirst().phase());
        assertTrue(state.fleets().getFirst().ships().getFirst().generatorFuelMassKg() > 0);
    }
}
