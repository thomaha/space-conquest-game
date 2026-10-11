package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.spaceconquest.engine.logistics.LunarRoundTripAuditTest.*;
import static org.junit.jupiter.api.Assertions.*;

class TradeFuelEconomyTest {
    @Test void unknownEquipmentCannotAuthorizeRefuelingOrCreateForecastStores() throws Exception {
        var state = world(false).withShipDesigns(List.of());
        assertFalse(TradeFuelEconomy.preferFull(state, fleet(state, "trader"), hub(state, "depot-market")));
        assertNull(TradeReturnFuelSizing.prepare(state, fleet(state, "trader"), hub(state, "moon-market")));
        assertSame(state, TradeLegReadiness.prepare(state, fleet(state, "trader"), hub(state, "moon-market")));
    }

    private GameState world(boolean cheap) throws Exception {
        var state = new ThresholdLunarSupplyAuditTest().opening(.8, false).state;
        var source = hub(state, "depot-market");
        var orders = new HashMap<>(source.activeOrders());
        if (cheap) for (String id : List.of("liquid_hydrogen", "liquid_oxygen")) {
            var order = orders.get(id);
            orders.put(id, new MarketOrder(id, order.supplyKg(), order.demandKg(), .000001, 0));
        }
        var port = source.withStockTargets(Map.of()).withInventory(state.turn(), source.currentStoredWeightKg(), orders);
        return state.withCommercialHubs(state.commercialHubs().stream()
                .map(hub -> hub.id().equals(port.id()) ? port : hub).toList());
    }

    @Test void cheapWellStockedPortFillsMainTankWhileExpensivePortBuysSafeSmallerAllowance() throws Exception {
        var cheap = world(true);
        var expensive = world(false);
        assertTrue(TradeFuelEconomy.preferFull(cheap, fleet(cheap, "trader"), hub(cheap, "depot-market")));
        assertFalse(TradeFuelEconomy.preferFull(expensive, fleet(expensive, "trader"), hub(expensive, "depot-market")));
        var filled = TradeLegReadiness.prepare(cheap, fleet(cheap, "trader"), hub(cheap, "moon-market"));
        var saved = TradeLegReadiness.prepare(expensive, fleet(expensive, "trader"), hub(expensive, "moon-market"));
        var design = FleetSupplySimulation.design(cheap, ship(cheap, "trader"));
        assertEquals(design.fuelCapacityKg(), ship(filled, "trader").currentFuelKg(), 1e-6);
        assertTrue(ship(saved, "trader").currentFuelKg() < design.fuelCapacityKg() - 1000);
        assertTrue(TradeLegReadiness.inspect(saved, fleet(saved, "trader"), hub(saved, "moon-market")).ready());
        for (var pair : List.of(List.of(cheap, filled), List.of(expensive, saved))) {
            assertEquals(totalCash(pair.getFirst()), totalCash(pair.getLast()), 1e-5);
            for (String material : List.of("liquid_hydrogen", "liquid_oxygen"))
                assertEquals(stock(pair.getFirst(), material), stock(pair.getLast(), material), 1e-5);
        }
    }

    @Test void cheapPriceDoesNotJustifyDrainingTheConfiguredStockBuffer() throws Exception {
        var state = world(true);
        var source = hub(state, "depot-market");
        var buffer = new HashMap<>(source.stockTargets());
        var oxygen = source.activeOrders().get("liquid_oxygen");
        buffer.put("liquid_oxygen", new com.spaceconquest.engine.market.HubStockTarget(oxygen.supplyKg(), oxygen.supplyKg()));
        assertFalse(TradeFuelEconomy.preferFull(state, fleet(state, "trader"), source.withStockTargets(buffer)));
    }
}
