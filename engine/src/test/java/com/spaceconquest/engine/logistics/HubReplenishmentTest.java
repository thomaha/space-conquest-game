package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.market.HubStockTarget;
import com.spaceconquest.engine.market.MarketDemandProcessor;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class HubReplenishmentTest {
    @TempDir Path directory;

    private GameState world(double stock, double threshold, double target) {
        var state = new RoamingTradePlannerTest().state(false, false);
        var buyer = state.commercialHubs().get(1);
        var orders = new HashMap<>(buyer.activeOrders());
        orders.put("steel", new MarketOrder("steel", stock, 0, 20, 0));
        buyer = new CommercialHub(buyer.id(), buyer.entityId(), 0, buyer.storageCapacityKg(),
                buyer.currentStoredWeightKg() + stock, buyer.logisticsRangeUnits(), orders,
                Map.of("steel", new HubStockTarget(threshold, target)));
        return state.withCommercialHubs(List.of(state.commercialHubs().getFirst(), buyer, state.commercialHubs().getLast()));
    }

    private GameState tick(GameState state) {
        state = state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), List.of(), List.of()));
        return new LogisticsProcessor().processTradeRoutes(state).state();
    }

    @Test void startsBeforeConsumptionAndFillsAcrossLoadsWithoutStoppingAtThreshold() throws Exception {
        var state = world(9, 10, 40);
        var processor = new LogisticsProcessor();
        state = processor.processTradeRoutes(state).state();
        assertEquals(20, state.tradeRoutes().getFirst().onboardKg());
        assertTrue(state.commercialHubs().get(1).stockTargets().get("steel").refillRequested());
        var manager = new SaveGameManager(directory);
        var file = directory.resolve("refill.scsave").toFile();
        manager.save(file, state, 1, "2027-01-01T08:00:00");
        var loaded = manager.load(file).toGameState(0, "RUNNING");
        assertEquals(state.commercialHubs(), loaded.commercialHubs());
        for (int i = 0; i < 12; i++) loaded = tick(loaded);
        assertEquals(40, loaded.commercialHubs().get(1).activeOrders().get("steel").supplyKg(), 1e-6);
        assertEquals(31, loaded.tradeRoutes().getFirst().totalVolumeMovedKg(), 1e-6);
        assertFalse(loaded.commercialHubs().get(1).stockTargets().get("steel").refillRequested());
        assertEquals(0, loaded.tradeRoutes().getFirst().onboardKg());
        var hub = loaded.commercialHubs().get(1);
        var orders = new HashMap<>(hub.activeOrders());
        orders.put("steel", new MarketOrder("steel", 9, 0, 20, 0));
        var consumed = new CommercialHub(hub.id(), hub.entityId(), 0, hub.storageCapacityKg(),
                hub.currentStoredWeightKg() - 31, 10, orders, hub.stockTargets());
        var depleted = loaded.withCommercialHubs(List.of(loaded.commercialHubs().getFirst(), consumed, loaded.commercialHubs().getLast()));
        assertEquals(20, processor.processTradeRoutes(depleted).state().tradeRoutes().getFirst().onboardKg());
    }

    @Test void thresholdUsesStrictComparisonAndDoesNotFollowDailyDemand() {
        for (double stock : List.of(10.0, 15.0, 40.0)) {
            var state = world(stock, 10, 40);
            var result = new LogisticsProcessor().processTradeRoutes(state).state();
            assertEquals(state.fleets(), result.fleets());
            assertEquals(0, result.tradeRoutes().getFirst().onboardKg());
        }
        var state = world(9, 10, 40);
        var refreshed = new MarketDemandProcessor().refresh(state, List.of());
        assertTrue(refreshed.get(1).activeOrders().get("steel").demandKg() > 0);
        assertEquals(state.commercialHubs().get(1).stockTargets(), refreshed.get(1).stockTargets());
    }

    @Test void sameTickSuppliersCountCommittedCargoAndAvoidDuplicateDeliveries() {
        var state = world(9, 10, 30);
        var original = state.fleets().getFirst();
        var ship = original.ships().getFirst();
        var second = new ShipInstance("second", ship.designId(), ship.ownerEntityId(), 100, 0,
                ship.currentFuelKg(), Map.of()).withPowerState(ship.powerState());
        var fleet = new Fleet("second-fleet", "Supplier", original.ownerEntityId(), original.currentSystemId(),
                "", 0, 0, 0, false, "PASSIVE", List.of(second), original.location());
        var route = new TradeRoute("second-route", "Supplier", "owner", "source", "buyer", "steel",
                20, 0, 100, List.of("second"), 0, true);
        state = state.withFleets(List.of(original, fleet)).withTradeRoutes(List.of(state.tradeRoutes().getFirst(), route));
        var result = new LogisticsProcessor().processTradeRoutes(state).state();
        assertEquals(21, result.tradeRoutes().stream().mapToDouble(TradeRoute::onboardKg).sum(), 1e-6);
        assertEquals(21, HubReplenishment.inboundKg(result, result.commercialHubs().get(1), "steel", null), 1e-6);
        for (int i = 0; i < 8; i++) result = tick(result);
        assertEquals(30, result.commercialHubs().get(1).activeOrders().get("steel").supplyKg(), 1e-6);
    }

    @Test void unfundedBuyerWaitsAndResumesWhenCashReturns() {
        var state = world(9, 10, 40);
        var accounts = state.marketAccounts();
        state = state.withMarketAccounts(accounts.stream().map(account -> account.hubId().equals("buyer")
                ? new MarketAccount("buyer", 0) : account).toList());
        var waiting = new LogisticsProcessor().processTradeRoutes(state).state();
        assertEquals(state.empires(), waiting.empires());
        assertEquals(state.fleets(), waiting.fleets());
        assertEquals(0, waiting.tradeRoutes().getFirst().onboardKg());
        var resumed = new LogisticsProcessor().processTradeRoutes(waiting.withMarketAccounts(accounts)).state();
        assertEquals(20, resumed.tradeRoutes().getFirst().onboardKg());
    }

    @Test void cancelledLoadedCargoStillCountsButMissingCargoDoesNot() {
        var state = new LogisticsProcessor().processTradeRoutes(world(9, 10, 30)).state();
        var route = state.tradeRoutes().getFirst();
        state = state.withTradeRoutes(List.of(route.withActive(false)));
        var buyer = state.commercialHubs().get(1);
        assertEquals(20, HubReplenishment.inboundKg(state, buyer, "steel", null));
        var fleet = state.fleets().getFirst();
        var ship = fleet.ships().getFirst();
        var lost = new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), 100, 0,
                ship.currentFuelKg(), Map.of()).withPowerState(ship.powerState());
        state = state.withFleets(List.of(fleet.withShips(List.of(lost))));
        assertEquals(0, HubReplenishment.inboundKg(state, buyer, "steel", null));
        assertEquals(21, HubReplenishment.requestedKg(state, buyer, "steel", null));
    }

    @Test void legacyHubJsonDefaultsToAutomaticPolicyAndTargetsAreImmutable() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var hub = world(9, 10, 40).commercialHubs().get(1);
        var tree = mapper.valueToTree(hub);
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree).remove("stockTargets");
        assertTrue(mapper.treeToValue(tree, CommercialHub.class).stockTargets().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> hub.stockTargets().clear());
        assertThrows(IllegalArgumentException.class, () -> new HubStockTarget(20, 10));
        assertThrows(IllegalArgumentException.class, () -> new HubStockTarget(0, Double.NaN));
    }

    @Test void shipsFuelWithNoWaitingConsumer() {
        var state = world(40, 10, 40);
        var buyer = state.commercialHubs().get(1);
        var orders = new HashMap<>(buyer.activeOrders());
        orders.put("rp1_kerosene", new MarketOrder("rp1_kerosene", 500, 0, 20, 0));
        buyer = new CommercialHub(buyer.id(), buyer.entityId(), 0, buyer.storageCapacityKg(),
                buyer.currentStoredWeightKg() - 9500, 10, orders,
                Map.of("rp1_kerosene", new HubStockTarget(800, 1500)));
        state = state.withCommercialHubs(List.of(state.commercialHubs().getFirst(), buyer, state.commercialHubs().getLast()));
        state = state.withTradeRoutes(List.of(new TradeRoute("route", "Fuel supplier", "owner", "source", "buyer",
                "rp1_kerosene", 20, 0, 2000, List.of("ship"), 0, true)));
        var initial = state;
        assertTrue(initial.fleets().stream().noneMatch(fleet -> FleetPositioning.atHub(initial, fleet, initial.commercialHubs().get(1))));
        var loaded = new LogisticsProcessor().processTradeRoutes(state).state();
        assertEquals("rp1_kerosene", loaded.tradeRoutes().getFirst().materialId());
        assertEquals(20, loaded.tradeRoutes().getFirst().onboardKg());
    }

    @Test void inboundCargoReservesSharedSpaceAndBuyerCashForLaterSuppliers() {
        var loaded = new LogisticsProcessor().processTradeRoutes(world(9, 10, 40)).state();
        var buyer = loaded.commercialHubs().get(1);
        assertEquals(buyer.storageCapacityKg() - buyer.currentStoredWeightKg() - 20,
                HubReplenishment.storageRoomKg(loaded, buyer, "other"), 1e-6);
        assertEquals(10000 - 20 * 16, HubReplenishment.buyerCashAvailable(loaded, buyer, "other"), 1e-6);
        assertEquals(11, HubReplenishment.requestedKg(loaded, buyer, "steel", "other"), 1e-6);
    }
}
