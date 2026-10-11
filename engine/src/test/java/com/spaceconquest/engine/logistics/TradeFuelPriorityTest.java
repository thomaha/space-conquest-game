package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.market.HubStockTarget;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TradeFuelPriorityTest {
    @Test void rejectedGreedyFuelBasketFindsAnotherFuelTogetherWithSmallGoodsLoad() {
        var state = world(false, 500, 1500, 2);
        var source = state.commercialHubs().getFirst();
        var buyer = state.commercialHubs().get(1);
        var sellers = new HashMap<>(source.activeOrders());
        sellers.put("liquid_hydrogen", new MarketOrder("liquid_hydrogen", 1000, 0, 1, 0));
        var buyers = new HashMap<>(buyer.activeOrders());
        buyers.put("liquid_hydrogen", new MarketOrder("liquid_hydrogen", 0, 0, 1.25000000001, 0));
        buyers.put("steel", new MarketOrder("steel", 0, 3, 20, 0));
        var targets = new HashMap<>(buyer.stockTargets());
        targets.put("liquid_hydrogen", new HubStockTarget(1000, 2000));
        state = state.withCommercialHubs(List.of(new CommercialHub(source.id(), source.entityId(), 0,
                source.storageCapacityKg(), source.currentStoredWeightKg() + 1000, 10, sellers),
                new CommercialHub(buyer.id(), buyer.entityId(), 0, buyer.storageCapacityKg(),
                        buyer.currentStoredWeightKg(), 10, buyers, targets), state.commercialHubs().getLast()));
        var selected = TradeBasketPlanner.choose(state, state.tradeRoutes().getFirst(), state.fleets().getFirst(), state.commercialHubs().get(1));
        assertNotNull(selected);
        assertTrue(selected.profit() > 0);
        var cargo = selected.shipment().route().cargoManifest();
        assertEquals(17, cargo.get("rp1_kerosene").massKg(), 1e-6);
        assertEquals(3, cargo.get("steel").massKg(), 1e-6);
        var dispatched = new LogisticsProcessor().processTradeRoutes(state).state().tradeRoutes().getFirst();
        assertEquals(17, dispatched.cargoManifest().get("rp1_kerosene").massKg(), 1e-6);
        assertEquals(3, dispatched.cargoManifest().get("steel").massKg(), 1e-6);
        assertEquals(100, source.activeOrders().get("steel").supplyKg());
    }

    @Test void partialFuelAndGoodsCanDepartWithoutFillingTheHold() {
        var state = world(false, 795, 800, 2);
        var buyer = state.commercialHubs().get(1);
        var orders = new HashMap<>(buyer.activeOrders());
        orders.put("steel", new MarketOrder("steel", 0, 3, 20, 0));
        state = state.withCommercialHubs(List.of(state.commercialHubs().getFirst(), new CommercialHub(buyer.id(),
                buyer.entityId(), 0, buyer.storageCapacityKg(), buyer.currentStoredWeightKg(), 10, orders, buyer.stockTargets()),
                state.commercialHubs().getLast()));
        var route = new LogisticsProcessor().processTradeRoutes(state).state().tradeRoutes().getFirst();
        assertEquals(5, route.cargoManifest().get("rp1_kerosene").massKg(), 1e-6);
        assertEquals(3, route.cargoManifest().get("steel").massKg(), 1e-6);
        assertEquals(8, route.onboardKg(), 1e-6);
    }

    @Test void roamingFuelReliefCanSelectADifferentDestinationFromTheBestGoodsMarket() {
        var state = world(true, 1500, 1500, 2);
        var third = state.commercialHubs().getLast();
        var orders = new HashMap<>(third.activeOrders());
        orders.put("rp1_kerosene", new MarketOrder("rp1_kerosene", 500, 0, 2, 0));
        third = new CommercialHub(third.id(), third.entityId(), 0, third.storageCapacityKg(),
                third.currentStoredWeightKg() - 9500, 10, orders,
                Map.of("rp1_kerosene", new HubStockTarget(800, 1500)));
        state = state.withCommercialHubs(List.of(state.commercialHubs().getFirst(), state.commercialHubs().get(1), third));
        var selected = RoamingTradePlanner.choose(state, state.tradeRoutes().getFirst(), state.fleets().getFirst());
        assertEquals("third", selected.shipment().route().destinationEntityId());
        assertEquals(20, selected.shipment().route().cargoManifest().get("rp1_kerosene").massKg());
    }

    @Test void ordinaryMaterialTargetsDoNotAcquireFuelPriority() {
        var state = world(true, 1500, 1500, 2);
        var source = state.commercialHubs().getFirst();
        var buyer = state.commercialHubs().get(1);
        var sourceOrders = new HashMap<>(source.activeOrders());
        sourceOrders.put("refined_copper", new MarketOrder("refined_copper", 1000, 0, .001, 0));
        var buyerOrders = new HashMap<>(buyer.activeOrders());
        buyerOrders.put("refined_copper", new MarketOrder("refined_copper", 100, 0, 2, 0));
        var third = state.commercialHubs().getLast();
        var thirdOrders = new HashMap<>(third.activeOrders());
        thirdOrders.put("refined_copper", new MarketOrder("refined_copper", 0, 0, 20, 0));
        state = state.withCommercialHubs(List.of(new CommercialHub(source.id(), source.entityId(), 0,
                        source.storageCapacityKg(), source.currentStoredWeightKg() + 1000, 10, sourceOrders),
                new CommercialHub(buyer.id(), buyer.entityId(), 0, buyer.storageCapacityKg(),
                        buyer.currentStoredWeightKg(), 10, buyerOrders,
                        Map.of("refined_copper", new HubStockTarget(800, 1500))),
                new CommercialHub(third.id(), third.entityId(), 0, third.storageCapacityKg(),
                        third.currentStoredWeightKg(), 10, thirdOrders)));
        var selected = RoamingTradePlanner.choose(state, state.tradeRoutes().getFirst(), state.fleets().getFirst());
        assertEquals(20, selected.shipment().route().cargoManifest().get("steel").massKg());
        assertEquals(0, TradeFuelPriority.urgency(state, state.commercialHubs().get(1), "refined_copper", "route"));
    }

    private GameState world(boolean roaming, double stock, double target, double fuelPrice) {
        var state = new RoamingTradePlannerTest().state(false, roaming);
        var hub = state.commercialHubs().get(1);
        var orders = new HashMap<>(hub.activeOrders());
        orders.put("rp1_kerosene", new MarketOrder("rp1_kerosene", stock, 0, fuelPrice, 0));
        hub = new CommercialHub(hub.id(), hub.entityId(), 0, hub.storageCapacityKg(),
                hub.currentStoredWeightKg() - 10000 + stock, 10, orders,
                Map.of("rp1_kerosene", new HubStockTarget(800, target)));
        return state.withCommercialHubs(List.of(state.commercialHubs().getFirst(), hub, state.commercialHubs().getLast()))
                .withTradeRoutes(List.of(new TradeRoute("route", "Supplier", "owner", "source", "buyer", "steel",
                        20, 0, 2000, List.of("ship"), 0, true).withRoaming(roaming)));
    }

    @Test void scarceConfiguredFuelWinsOverMoreProfitableGoodsForFixedAndRoamingSuppliers() {
        for (boolean roaming : List.of(false, true)) {
            var state = world(roaming, 500, 1500, 2);
            var result = new LogisticsProcessor().processTradeRoutes(state).state();
            var route = result.tradeRoutes().getFirst();
            assertEquals(20, route.cargoManifest().get("rp1_kerosene").massKg(), 1e-6);
            assertFalse(route.cargoManifest().containsKey("steel"));
            assertTrue(result.fleets().getFirst().location().inTransit());
            assertEquals(100, result.commercialHubs().getFirst().activeOrders().get("steel").supplyKg());
            if (roaming) assertTrue(route.status().contains("Prioritized configured fuel shortage"));
        }
    }

    @Test void spareCapacityCarriesProfitableGoodsAfterCoveringTheFuelTarget() {
        for (boolean roaming : List.of(false, true)) {
            var state = world(roaming, 795, 800, 2);
            var result = new LogisticsProcessor().processTradeRoutes(state).state();
            var cargo = result.tradeRoutes().getFirst().cargoManifest();
            assertEquals(5, cargo.get("rp1_kerosene").massKg(), 1e-6);
            assertEquals(15, cargo.get("steel").massKg(), 1e-6);
        }
    }

    @Test void coveredThresholdAndRefillIntentAboveItRestoreNormalProfitRanking() {
        var state = world(true, 800, 1500, 2);
        var hub = state.commercialHubs().get(1);
        state = state.withCommercialHubs(List.of(state.commercialHubs().getFirst(),
                hub.withStockTargets(Map.of("rp1_kerosene", new HubStockTarget(800, 1500, true))),
                state.commercialHubs().getLast()));
        var selected = RoamingTradePlanner.choose(state, state.tradeRoutes().getFirst(), state.fleets().getFirst());
        assertEquals(20, selected.shipment().route().cargoManifest().get("steel").massKg());
        assertEquals(0, TradeFuelPriority.relief(state, selected.shipment()));
    }

    @Test void actualCommittedInboundFuelRemovesUrgencyForAnotherSupplier() {
        var state = world(true, 780, 800, 2);
        var loaded = new LogisticsProcessor().processTradeRoutes(state).state();
        var fleet = state.fleets().getFirst();
        var ship = fleet.ships().getFirst();
        var second = new ShipInstance("second", ship.designId(), ship.ownerEntityId(), 100, 0,
                ship.currentFuelKg(), Map.of()).withPowerState(ship.powerState());
        var secondFleet = new Fleet("second-fleet", "Supplier", "owner", "sol", "", 0, 0, 0, false,
                "PASSIVE", List.of(second), fleet.location());
        var secondRoute = new TradeRoute("second-route", "Supplier", "owner", "source", "buyer", "steel",
                20, 0, 2000, List.of("second"), 0, true).withRoaming(true);
        loaded = loaded.withFleets(List.of(loaded.fleets().getFirst(), secondFleet))
                .withTradeRoutes(List.of(loaded.tradeRoutes().getFirst(), secondRoute));
        assertEquals(0, TradeFuelPriority.urgency(loaded, loaded.commercialHubs().get(1), "rp1_kerosene", secondRoute.id()));
        var selected = RoamingTradePlanner.choose(loaded, secondRoute, secondFleet);
        assertEquals(20, selected.shipment().route().cargoManifest().get("steel").massKg());
    }

    @Test void unprofitableFuelDoesNotBlockViableGoodsOrSpendPreviewFunds() {
        var state = world(true, 500, 1500, .0005);
        var selected = RoamingTradePlanner.choose(state, state.tradeRoutes().getFirst(), state.fleets().getFirst());
        assertEquals(20, selected.shipment().route().cargoManifest().get("steel").massKg());
        assertFalse(selected.shipment().route().cargoManifest().containsKey("rp1_kerosene"));
        assertEquals(10000, state.empires().getFirst().treasuryCredits());
        assertEquals(10000, state.commercialHubs().getFirst().activeOrders().get("rp1_kerosene").supplyKg());
    }

    @Test void cashlessBuyerAndUnsafeTravelStillRejectUrgentFuel() {
        var state = world(true, 500, 1500, 2).withMarketAccounts(List.of(new MarketAccount("buyer", 0)));
        var result = new LogisticsProcessor().processTradeRoutes(state).state();
        assertEquals(0, result.tradeRoutes().getFirst().onboardKg());
        assertEquals(state.empires(), result.empires());
        assertEquals(state.fleets(), result.fleets());
        state = world(true, 500, 1500, 2);
        var fleet = state.fleets().getFirst();
        var ship = fleet.ships().getFirst();
        state = state.withFleets(List.of(fleet.withShips(List.of(new ShipInstance(ship.id(), ship.designId(),
                ship.ownerEntityId(), 100, 0, 0, Map.of()).withPowerState(ShipPowerState.empty())))))
                .withEmpires(List.of(new Empire("owner", "Owner", "human", "Individualist", 0, 0,
                        List.of("sol"), List.of(), Map.of(), List.of("rocketry"), List.of())));
        assertNull(RoamingTradePlanner.choose(state, state.tradeRoutes().getFirst(), state.fleets().getFirst()).shipment());
    }
}
