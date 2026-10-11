package com.spaceconquest.control;

import com.fasterxml.jackson.databind.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.logistics.*;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TradeEscapeTest {
    private Fleet convoy(GameState state) { return FleetOrganization.find(state, "hunter"); }
    private TradeRoute route(GameState state) { return state.tradeRoutes().getFirst(); }
    private CommercialHub hub(String id, String port, boolean source) {
        var orders = new HashMap<String, MarketOrder>();
        orders.put("steel", new MarketOrder("steel", source ? 100 : 0, source ? 0 : 5, source ? 1 : 20, 0));
        orders.put("refined_copper", new MarketOrder("refined_copper", source ? 100 : 0, source ? 0 : 15, source ? 1 : 20, 0));
        orders.put("fusion_fuel_pellets", new MarketOrder("fusion_fuel_pellets", 10000, 0, .00001, 0));
        orders.put("refined_uranium", new MarketOrder("refined_uranium", 100, 0, .00001, 0));
        orders.put("hydrogen_gas", new MarketOrder("hydrogen_gas", 1_000_000, 0, .00001, 0));
        return new CommercialHub(id, port, 0, 2_000_000, orders.values().stream().mapToDouble(MarketOrder::supplyKg).sum(), 10, orders);
    }
    private GameState loaded() throws Exception {
        var state = new OrbitalRendezvousTest().world();
        var modules = new ArrayList<>(ShipComponentCatalog.workbenchModules("mod_fusion_drive", false, ShipComponentCatalog.FISSION_REACTOR_ID));
        modules.add(SensorModuleCatalog.ADVANCED);
        var evaluated = ShipBlueprintFactory.evaluate(state, new ShipDesignSpecification("cargo", "Cargo", "owner", ShipRole.CARGO_TRANSPORT,
                "steel", modules, "steel", .5)); assertTrue(evaluated.valid(), evaluated.errors().toString());
        var designs = new ArrayList<>(state.shipDesigns()); designs.add(evaluated.design()); state = state.withShipDesigns(designs);
        var stations = new ArrayList<>(state.orbitalStations()); var prototype = stations.getFirst();
        for (String id : List.of("source-port", "buyer-port")) stations.add(new OrbitalStation(id, id, "sol", "earth", "owner",
                prototype.ownershipType(), 10, List.of(), Map.of(), 100, 1, 0, 0, 100, 100, "steel", 0, true)
                .withParkingAltitudeKm(id.equals("source-port") ? 500.0 : 1000.0).withOrbitalPhaseRadians(0));
        var escort = convoy(state).ships().getFirst();
        var ship = new ShipInstance("trader", "cargo", "owner", 100, 0, evaluated.design().fuelCapacityKg(),
                Map.of("fusion_fuel_pellets", 200.0)).withPowerState(escort.powerState());
        var convoy = convoy(state).withShips(List.of(ship, escort)).withLocation(FleetLocation.at(FleetLocation.Site.docked("source-port")))
                .withEscortOrder(new FleetEscortOrder("owner", "escort", "Escort", "PASSIVE", List.of(escort.id())));
        state = state.toBuilder().orbitalStations(stations).fleets(List.of(convoy))
                .commercialHubs(List.of(hub("source", "source-port", true), hub("buyer", "buyer-port", false)))
                .marketAccounts(List.of(new MarketAccount("source", 100000), new MarketAccount("buyer", 100000)))
                .tradeRoutes(List.of(new TradeRoute("trade", "Trade", "owner", "source", "buyer", "steel", 20, 0, 100,
                        List.of(ship.id()), 0, true).withRoaming(true))).build();
        var loaded = new LogisticsProcessor().processTradeRoutes(state).state();
        assertEquals(20, route(loaded).onboardKg(), .000001, route(loaded).status()); assertEquals(2, route(loaded).cargoManifest().size());
        assertEquals(TradeRoute.DELIVERING, route(loaded).phase());
        loaded = day(loaded);
        assertNotNull(convoy(loaded).location().localFlight(), "Native capture starts its funded station approach.");
        var enemy = FleetOrganization.find(new OrbitalRendezvousTest().world(), "target"); var enemyShip = enemy.ships().getFirst();
        var enemies = new ArrayList<ShipInstance>();
        for (int i = 0; i < 20; i++) enemies.add(new ShipInstance("enemy-" + i, enemyShip.designId(), "enemy", 100, 0,
                enemyShip.currentFuelKg(), enemyShip.storedCargoKg()).withPowerState(enemyShip.powerState()));
        var paid = convoy(loaded).location().localFlight();
        // An interrupted paid approach needs a new escape when a nearby hostile coast is in cannon range.
        var interrupted = new LocalFlight(paid.geometry(), paid.motion(), paid.propellantKg(), paid.propulsion(), true, false, null, paid.vectorFlight());
        loaded = loaded.withFleets(List.of(convoy(loaded).withLocation(convoy(loaded).location().withFlight(interrupted))));
        var hostileCoast = new LocalFlight(paid.geometry(), paid.motion(), Map.of(), Map.of(), true, false, null, paid.vectorFlight());
        enemy = enemy.withShips(enemies).withLocation(convoy(loaded).location().withFlight(hostileCoast));
        var threatened = loaded.withFleets(List.of(convoy(loaded), enemy));
        assertTrue(ShipWeaponRange.reaches(threatened, enemy, convoy(threatened)),
                "Threat separation: " + ShipWeaponRange.separation(threatened, enemy, convoy(threatened)));
        return threatened;
    }
    private GameState day(GameState state) {
        return state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), state.orbitalStations(), List.of()))
                .withTurn(state.turn() + 1);
    }
    private GameState safeArrival(GameState escaping) {
        var state = day(escaping.withFleets(List.of(convoy(escaping))));
        assertTrue(RecoveredParkingTravel.ready(convoy(state)) || !convoy(state).location().inTransit());
        return state;
    }
    private double cash(GameState state) {
        return state.empires().stream().mapToDouble(Empire::treasuryCredits).sum()
                + state.marketAccounts().stream().mapToDouble(MarketAccount::unsettledSalesCredits).sum();
    }

    @Test void realPaidMixedShipmentPausesPersistsAndResumesOneSaleWithoutRepurchase() throws Exception {
        var initial = loaded(); var original = route(initial); double money = cash(initial);
        var decision = FleetProtectionProcessor.decide(initial, convoy(initial), List.of());
        assertEquals(FleetProtectionProcessor.Outcome.ESCAPING, decision.outcome());
        var escaping = FleetProtectionProcessor.advance(initial, List.of());
        assertEquals(TradeRoute.PAUSED_DELIVERY, route(escaping).phase());
        assertEquals(original.cargoManifest(), route(escaping).cargoManifest()); assertEquals(money, cash(escaping), .000001);
        assertEquals(original.cumulativeOperatingResultCredits(), route(escaping).cumulativeOperatingResultCredits());
        var waiting = new LogisticsProcessor().processTradeRoutes(escaping);
        assertEquals(0, waiting.deliveredKg()); assertEquals(TradeRoute.PAUSED_DELIVERY, route(waiting.state()).phase());
        assertEquals(escaping.commercialHubs(), waiting.state().commercialHubs());
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var restored = mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(escaping, "now", 1, "now")), SaveGame.class)
                .toGameState(escaping.turn(), "RUNNING"); assertEquals(route(escaping), route(restored));
        var state = safeArrival(restored);
        for (int i = 0; i < 10 && route(state).escapePaused(); i++) {
            state = new LogisticsProcessor().processTradeRoutes(state).state();
            if (route(state).escapePaused()) state = day(state);
        }
        assertEquals(TradeRoute.DELIVERING, route(state).phase(), route(state).status()); assertEquals(original.cargoManifest(), route(state).cargoManifest());
        for (int i = 0; i < 10 && route(state).onboardKg() > 0; i++) state = new LogisticsProcessor().processTradeRoutes(day(state)).state();
        assertEquals(0, route(state).onboardKg()); assertEquals(20, route(state).totalVolumeMovedKg(), .000001);
        assertEquals(money, cash(state), .000001); assertEquals(100 - original.cargoManifest().get("steel").massKg(),
                state.commercialHubs().getFirst().activeOrders().get("steel").supplyKg(), .000001);
        var sold = state.withTradeRoutes(List.of(route(state).withActive(false))); double volume = route(sold).totalVolumeMovedKg();
        var repeated = new LogisticsProcessor().processTradeRoutes(sold).state();
        assertEquals(volume, route(repeated).totalVolumeMovedKg()); assertEquals(sold.marketAccounts(), repeated.marketAccounts());
        assertEquals(convoy(initial).escortOrder(), convoy(state).escortOrder());
    }

    @Test void canceledOrUnavailableBuyerReturnsPaidLoadWithoutRefundOrNewPurchase() throws Exception {
        for (boolean canceled : List.of(true, false)) {
            var initial = loaded(); var original = route(initial); var escaping = FleetProtectionProcessor.advance(initial, List.of());
            var safe = safeArrival(escaping);
            // Complete the funded resumed leg without running settlement before testing paid-cargo return.
            for (int i = 0; i < 10 && route(safe).escapePaused(); i++) {
                safe = new LogisticsProcessor().processTradeRoutes(safe).state();
                if (route(safe).escapePaused()) safe = day(safe);
            }
            for (int i = 0; i < 10 && !FleetPositioning.atHub(safe, convoy(safe), safe.commercialHubs().getLast()); i++) safe = day(safe);
            assertTrue(FleetPositioning.atHub(safe, convoy(safe), safe.commercialHubs().getLast()));
            safe = TradeEscape.pause(safe, List.of(convoy(safe).id()));
            if (canceled) safe = safe.withTradeRoutes(List.of(route(safe).withActive(false)));
            else safe = safe.toBuilder().commercialHubs(safe.commercialHubs().stream().filter(hub -> !hub.id().equals("buyer")).toList()).build();
            var state = safe;
            for (int i = 0; i < 10 && route(state).escapePaused(); i++) {
                state = new LogisticsProcessor().processTradeRoutes(state).state();
                if (route(state).escapePaused()) state = day(state);
            }
            assertEquals(TradeRoute.RETURNING_CARGO, route(state).phase(), route(state).status());
            for (int i = 0; i < 10 && !FleetPositioning.atHub(state, convoy(state), state.commercialHubs().getFirst()); i++)
                state = new LogisticsProcessor().processTradeRoutes(day(state)).state();
            assertTrue(FleetPositioning.atHub(state, convoy(state), state.commercialHubs().getFirst()));
            assertEquals(original.cargoManifest(), route(state).cargoManifest()); assertEquals(0, route(state).totalVolumeMovedKg());
            assertEquals(cash(initial), cash(state), .000001); assertEquals(initial.commercialHubs().getFirst().activeOrders(), state.commercialHubs().getFirst().activeOrders());
            var held = new LogisticsProcessor().processTradeRoutes(state).state();
            assertEquals(TradeRoute.RETURNING_CARGO, route(held).phase()); assertEquals(route(state).cargoManifest(), route(held).cargoManifest());
            assertEquals(0, route(held).totalVolumeMovedKg());
            assertEquals(!canceled, route(held).isActive());
        }
    }

    @Test void unavailablePortsHoldPaidCargoWithoutSettlement() throws Exception {
        var initial = loaded(); var original = route(initial);
        var state = safeArrival(FleetProtectionProcessor.advance(initial, List.of()));
        state = state.toBuilder().orbitalStations(state.orbitalStations().stream()
                .filter(station -> !List.of("source-port", "buyer-port").contains(station.id())).toList()).build();
        var waiting = new LogisticsProcessor().processTradeRoutes(state).state();
        assertEquals(TradeRoute.PAUSED_DELIVERY, route(waiting).phase());
        assertEquals(original.cargoManifest(), route(waiting).cargoManifest());
        assertEquals(0, route(waiting).totalVolumeMovedKg()); assertEquals(cash(state), cash(waiting));
        assertEquals(state.marketAccounts(), waiting.marketAccounts()); assertEquals(state.commercialHubs(), waiting.commercialHubs());
    }

    @Test void unfundedEscapeDoesNotPauseOrChangeExistingPurchase() throws Exception {
        var state = loaded(); var fleet = convoy(state);
        var dry = fleet.ships().stream().map(ship -> new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), 100, 0, 0,
                ship.storedCargoKg()).withPowerState(ship.powerState())).toList();
        state = state.withFleets(List.of(fleet.withShips(dry), state.fleets().getLast()));
        assertEquals(FleetProtectionProcessor.Outcome.ESCAPE_BLOCKED, FleetProtectionProcessor.decide(state, convoy(state), List.of()).outcome());
        assertEquals(state, FleetProtectionProcessor.advance(state, List.of())); assertEquals(TradeRoute.DELIVERING, route(state).phase());
    }
}
