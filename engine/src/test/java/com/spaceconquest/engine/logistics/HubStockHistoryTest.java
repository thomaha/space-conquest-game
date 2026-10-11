package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import com.spaceconquest.engine.market.HubStockHistory;
import com.spaceconquest.engine.market.MarketProcessor;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HubStockHistoryTest {
    @TempDir Path directory;

    private CommercialHub hub(double kg) {
        return new CommercialHub("hub", "body", 0, 10000, kg, 10, orders(kg));
    }
    private Map<String, MarketOrder> orders(double kg) {
        return Map.of("liquid_hydrogen", new MarketOrder("liquid_hydrogen", kg, 0, 1, 0));
    }
    private CommercialHub stock(CommercialHub hub, long day, double kg) {
        return hub.withInventory(day, kg, orders(kg));
    }

    @Test void equalNetStockStillRecordsEveryWithdrawalAndDelivery() {
        var opening = hub(10).observeDay(1);
        var updated = stock(opening, 1, 3);
        updated = stock(updated, 1, 10);
        updated = stock(updated, 1, 7);
        updated = stock(updated, 1, 10).observeDay(1);
        var average = updated.stockHistory().average("liquid_hydrogen", 2);
        assertEquals(10, average.withdrawnKg());
        assertEquals(10, average.deliveredKg());
        assertEquals(10, average.dailyWithdrawalKg());
        assertEquals(1, average.observedDays());
        assertEquals(0, average.stockoutDays());
        assertTrue(opening.stockHistory().average("liquid_hydrogen", 2).withdrawnKg() == 0);
        assertEquals(0, updated.stockHistory().average("liquid_hydrogen", 1).observedDays());
    }

    @Test void rollingWindowAdaptsToTrafficAndKeepsQuietDaysWithoutUnboundedGrowth() {
        var hub = hub(1000);
        for (long day = 1; day <= 60; day++) {
            hub = hub.observeDay(day);
            hub = stock(hub, day, 1000 - (day <= 30 ? 10 : 20));
            hub = stock(hub, day, 1000);
        }
        hub = hub.observeDay(61); // Incomplete current day is excluded.
        var average = hub.stockHistory().average("liquid_hydrogen", 61);
        assertEquals(30, average.observedDays());
        assertEquals(20, average.dailyWithdrawalKg());
        assertEquals(20, average.dailyDeliveryKg());
        assertEquals(31, hub.stockHistory().days().size());
        hub = hub.observeDay(62);
        assertEquals(580.0 / 30, hub.stockHistory().average("liquid_hydrogen", 62).dailyWithdrawalKg(), 1e-9);
    }

    @Test void missingDaysAndUnknownMaterialsDoNotBecomeZeroUsageObservations() {
        var hub = hub(100).observeDay(10);
        hub = stock(hub, 10, 90).observeDay(13);
        var average = hub.stockHistory().average("liquid_hydrogen", 14);
        assertEquals(2, average.observedDays());
        assertEquals(5, average.dailyWithdrawalKg());
        assertEquals(0, hub.stockHistory().average("liquid_oxygen", 14).observedDays());
        assertEquals(0, hub.stockHistory().average("liquid_hydrogen", 44).observedDays());
    }

    @Test void outagesWarnEvenWhenRefilledOnTheSameDayAndWhenNoWithdrawalWasPossible() {
        var hub = hub(17).observeDay(1);
        hub = stock(hub, 1, 0).observeDay(2).observeDay(3);
        hub = stock(hub, 3, 17);
        var average = hub.stockHistory().average("liquid_hydrogen", 4);
        assertEquals(3, average.stockoutDays());
        assertEquals(17.0 / 3, average.dailyWithdrawalKg(), 1e-9);
        var state = GameState.builder().turn(4).commercialHubs(List.of(hub)).build();
        var fuel = new HubFuelStockSuggestions.Fuel("hydrogen", "Hydrogen", Map.of("liquid_hydrogen", 1.0));
        var estimate = HubFuelStockSuggestions.estimate(state, hub, fuel);
        assertEquals(17.0 / 3, estimate.dailyKg(), 1e-9);
        assertTrue(estimate.explanation().contains("3 stockout days; usage may understate demand"));
        assertTrue(HubFuelCoverage.inspect(state, hub, fuel).warnings().stream().anyMatch(text -> text.contains("understate demand")));
    }

    @Test void mixtureEstimateUsesMostDemandingIngredientAndRetainsModeledDemandFloor() {
        var hub = new CommercialHub("hub", "body", 0, 10000, 1000, 10, Map.of(
                "liquid_hydrogen", new MarketOrder("liquid_hydrogen", 170, 0, 1, 0),
                "liquid_oxygen", new MarketOrder("liquid_oxygen", 830, 166, 1, 0)));
        hub = hub.withInventory(1, 830, Map.of(
                "liquid_hydrogen", new MarketOrder("liquid_hydrogen", 0, 0, 1, 0),
                "liquid_oxygen", hub.activeOrders().get("liquid_oxygen")));
        var fuel = HubFuelStockSuggestions.fuels().stream().filter(item -> item.id().equals("mod_hydrolox_rocket")).findFirst().orElseThrow();
        assertEquals(1000, HubFuelStockSuggestions.estimate(GameState.builder().turn(2).build(), hub, fuel).dailyKg(), 1e-6);
        assertTrue(hub.stockTargets().isEmpty());
    }

    @Test void paidFuelPurchasesRecordAcquiredQuantityOnlyAndPriceUpdatesPreserveHistory() {
        var state = new RoamingTradePlannerTest().state(false, false).withTurn(5);
        var source = state.commercialHubs().getFirst();
        var purchase = ConstructionMaterials.buyUpTo(state, source.entityId(), "owner", Map.of("rp1_kerosene", 25.0));
        assertEquals(25, purchase.acquiredKg().get("rp1_kerosene"));
        var hub = purchase.state().commercialHubs().getFirst();
        assertEquals(25, hub.stockHistory().average("rp1_kerosene", 6).withdrawnKg());
        assertEquals(hub.stockHistory(), new MarketProcessor().updateHub(hub).stockHistory());
        assertTrue(state.commercialHubs().getFirst().stockHistory().days().isEmpty());
        var denied = ConstructionMaterials.buyUpTo(purchase.state(), source.entityId(), "unknown", Map.of("rp1_kerosene", 500.0));
        assertEquals(hub.stockHistory(), denied.state().commercialHubs().getFirst().stockHistory());
    }

    @Test void physicalFreightCountsSourceWithdrawalAndDestinationDeliveryWithoutCountingInboundTwice() {
        var state = new RoamingTradePlannerTest().state(false, false);
        var processor = new LogisticsProcessor();
        state = processor.processTradeRoutes(state.withTurn(1)).state();
        assertEquals(20, state.commercialHubs().getFirst().stockHistory().average("steel", 2).withdrawnKg());
        assertEquals(0, state.commercialHubs().get(1).stockHistory().average("steel", 2).deliveredKg());
        for (long day = 2; day <= 15 && state.tradeRoutes().getFirst().totalVolumeMovedKg() == 0; day++) {
            state = state.withTurn(day);
            state = state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), List.of(), List.of()));
            state = processor.processTradeRoutes(state).state();
        }
        assertEquals(20, state.tradeRoutes().getFirst().totalVolumeMovedKg());
        assertEquals(20, state.commercialHubs().get(1).stockHistory().average("steel", state.turn() + 1).deliveredKg());
    }

    @Test void saveReloadAndLegacyDefaultsRetainImmutableHistory() throws Exception {
        var hub = stock(hub(100), 7, 40).observeDay(8);
        var state = GameState.builder().turn(8).commercialHubs(List.of(hub)).build();
        var manager = new SaveGameManager(directory);
        var file = directory.resolve("history.scsave").toFile();
        manager.save(file, state, 1, "2027-01-01T08:00:00");
        var save = manager.load(file);
        assertEquals(SaveGame.CURRENT_VERSION, save.version());
        assertEquals(hub, save.toGameState(8, "RUNNING").commercialHubs().getFirst());
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var tree = mapper.valueToTree(hub);
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree).remove("stockHistory");
        assertEquals(HubStockHistory.empty(), mapper.treeToValue(tree, CommercialHub.class).stockHistory());
        assertThrows(UnsupportedOperationException.class, () -> hub.stockHistory().days().clear());
        assertThrows(UnsupportedOperationException.class, () -> hub.stockHistory().days().getFirst().materials().clear());
    }

    @Test void authoritativeTickObservesQuietDaysWithoutFabricatingTransactions() {
        var engine = new SpaceConquestEngine();
        engine.applyGameState(GameState.builder().commercialHubs(List.of(hub(100))).build());
        engine.stepTurn();
        engine.stepTurn();
        var state = engine.getGameState();
        var average = state.commercialHubs().getFirst().stockHistory().average("liquid_hydrogen", state.turn());
        assertEquals(1, average.observedDays());
        assertEquals(0, average.withdrawnKg());
        assertEquals(0, average.deliveredKg());
    }
}
