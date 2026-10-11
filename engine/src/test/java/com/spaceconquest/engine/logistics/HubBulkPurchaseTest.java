package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.market.HubStockTarget;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HubBulkPurchaseTest {
    private GameState atBuyer(boolean configured, boolean active) {
        var state = new RoamingTradePlannerTest().state(false, true).withTurn(4);
        var fleet = state.fleets().getFirst();
        var original = fleet.ships().getFirst();
        var ship = new ShipInstance(original.id(), original.designId(), original.ownerEntityId(), 100, 0,
                original.currentFuelKg(), Map.of("steel", 20.0, "refined_copper", .1)).withPowerState(original.powerState());
        var buyer = state.commercialHubs().get(1);
        var orders = new HashMap<>(buyer.activeOrders());
        orders.put("steel", new MarketOrder("steel", 100, 0, 20, 0));
        orders.put("refined_copper", new MarketOrder("refined_copper", 100, 0, 20, 0));
        buyer = new CommercialHub(buyer.id(), buyer.entityId(), 0, buyer.storageCapacityKg(),
                orders.values().stream().mapToDouble(MarketOrder::supplyKg).sum(), buyer.logisticsRangeUnits(), orders,
                configured ? Map.of("steel", new HubStockTarget(100, 100), "refined_copper", new HubStockTarget(100, 100)) : Map.of());
        var route = state.tradeRoutes().getFirst().withLoadedManifest(Map.of(
                "steel", new TradeCargo(20, 20), "refined_copper", new TradeCargo(.1, .1))).withActive(active);
        return state.withCommercialHubs(List.of(state.commercialHubs().getFirst(), buyer, state.commercialHubs().getLast()))
                .withFleets(List.of(fleet.withShips(List.of(ship)).withLocation(FleetLocation.at(FleetLocation.Site.docked(buyer.entityId())))))
                .withTradeRoutes(List.of(route));
    }

    private void assertBulkSale(GameState state) {
        double cash = totalCash(state);
        double seller = state.empires().getFirst().treasuryCredits();
        double buyer = state.marketAccounts().get(1).unsettledSalesCredits();
        var result = new LogisticsProcessor().processTradeRoutes(state);
        var after = result.state();
        var route = after.tradeRoutes().getFirst();
        assertEquals(20.1, result.deliveredKg(), 1e-9);
        assertEquals(0, route.onboardKg());
        assertTrue(route.cargoManifest().isEmpty());
        assertEquals(0, after.fleets().getFirst().ships().getFirst().storedCargoKg().values().stream().mapToDouble(Double::doubleValue).sum());
        assertEquals(TradeRoute.LOADING, route.phase());
        assertEquals("buyer", route.originEntityId());
        assertEquals(120, after.commercialHubs().get(1).activeOrders().get("steel").supplyKg());
        assertEquals(100.1, after.commercialHubs().get(1).activeOrders().get("refined_copper").supplyKg(), 1e-9);
        assertEquals(seller + 321.6, after.empires().getFirst().treasuryCredits(), 1e-9);
        assertEquals(buyer - 321.6, after.marketAccounts().stream().filter(account -> account.hubId().equals("buyer"))
                .findFirst().orElseThrow().unsettledSalesCredits(), 1e-9);
        assertEquals(cash, totalCash(after), 1e-9);
        assertEquals(20, after.commercialHubs().get(1).stockHistory().average("steel", 5).deliveredKg());
        assertEquals(.1, after.commercialHubs().get(1).stockHistory().average("refined_copper", 5).deliveredKg(), 1e-9);
    }

    @Test void buysWholeMixedLoadAboveTargetsAndRouteStockCapWithNoConsumerDemand() {
        assertBulkSale(atBuyer(true, true));
    }

    @Test void automaticStockPolicyDoesNotMakeArrivalsWaitForEndConsumers() {
        assertBulkSale(atBuyer(false, true));
    }

    @Test void cancelledCommittedLoadStillSellsWholeCargoAndClearsTheDock() {
        assertBulkSale(atBuyer(true, false));
    }

    @Test void shrinkingTargetsInFlightDoesNotStrandPaidCargoOnArrival() {
        var state = new RoamingTradePlannerTest().state(false, false);
        var buyer = state.commercialHubs().get(1).withStockTargets(Map.of("steel", new HubStockTarget(10, 20)));
        state = state.withCommercialHubs(List.of(state.commercialHubs().getFirst(), buyer, state.commercialHubs().getLast()));
        var processor = new LogisticsProcessor();
        state = processor.processTradeRoutes(state).state();
        assertEquals(20, state.tradeRoutes().getFirst().onboardKg());
        buyer = state.commercialHubs().get(1).withStockTargets(Map.of("steel", new HubStockTarget(1, 1)));
        state = state.withCommercialHubs(List.of(state.commercialHubs().getFirst(), buyer, state.commercialHubs().getLast()));
        for (int day = 0; day < 10 && state.tradeRoutes().getFirst().totalVolumeMovedKg() == 0; day++) {
            state = state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), List.of(), List.of()))
                    .withTurn(state.turn() + 1);
            state = processor.processTradeRoutes(state).state();
        }
        assertEquals(20, state.tradeRoutes().getFirst().totalVolumeMovedKg());
        assertEquals(20, state.commercialHubs().get(1).activeOrders().get("steel").supplyKg());
        assertEquals(0, state.tradeRoutes().getFirst().onboardKg());
        assertNotEquals(TradeRoute.DELIVERING, state.tradeRoutes().getFirst().phase());
    }

    @Test void purchasesOnlyPhysicallyRemainingCargoWhenSomeWasLostBeforeArrival() {
        var state = atBuyer(true, true);
        var fleet = state.fleets().getFirst();
        var ship = fleet.ships().getFirst();
        state = state.withFleets(List.of(fleet.withShips(List.of(new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(),
                100, 0, ship.currentFuelKg(), Map.of("steel", 5.0)).withPowerState(ship.powerState())))));
        var result = new LogisticsProcessor().processTradeRoutes(state);
        assertEquals(5, result.deliveredKg());
        assertEquals(105, result.state().commercialHubs().get(1).activeOrders().get("steel").supplyKg());
        assertEquals(100, result.state().commercialHubs().get(1).activeOrders().get("refined_copper").supplyKg());
        assertTrue(result.state().tradeRoutes().getFirst().cargoManifest().isEmpty());
        assertEquals(totalCash(state), totalCash(result.state()), 1e-9);
    }

    private double totalCash(GameState state) {
        return state.empires().stream().mapToDouble(Empire::treasuryCredits).sum()
                + state.marketAccounts().stream().mapToDouble(account -> account.unsettledSalesCredits()).sum();
    }
}
