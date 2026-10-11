package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.spaceconquest.engine.logistics.LunarRoundTripAuditTest.*;
import static org.junit.jupiter.api.Assertions.*;

class TradeNetFuelTest {
    private GameState opening() throws Exception {
        var state = new ThresholdLunarSupplyAuditTest().opening(.8, false).state;
        var target = hub(state, "moon-market");
        var orders = new HashMap<>(target.activeOrders());
        double removed = 0;
        for (var entry : Map.of("hydrogen_gas", 80000.0, "refined_uranium", 300.0).entrySet()) {
            var order = orders.get(entry.getKey());
            removed += order.supplyKg() - entry.getValue();
            orders.put(entry.getKey(), new MarketOrder(entry.getKey(), entry.getValue(), order.demandKg(), order.pricePerKg(), 0));
        }
        var reduced = target.withInventory(state.turn(), target.currentStoredWeightKg() - removed, orders);
        return state.withCommercialHubs(state.commercialHubs().stream()
                .map(hub -> hub.id().equals(reduced.id()) ? reduced : hub).toList());
    }

    @Test void supplierChoosesHighestNetReliefAmongProfitableProtectedLoads() throws Exception {
        var state = opening();
        var route = route(state, "moon-supply").withMarketChoice("hydrogen_gas", "moon-market");
        var full = new LogisticsProcessor().previewShipment(state, route, 45000, true);
        assertNotNull(full);
        assertTrue(TradeFuelPriority.consumesUrgentStock(state, full));
        var selected = TradeShipmentSizing.choose(state, route, 45000);
        assertNotNull(selected);
        assertTrue(selected.profit() > 0);
        for (int reduction = 0; reduction <= 3; reduction++) {
            var alternative = new LogisticsProcessor().previewShipment(state, route, 45000.0 / (1 << reduction), true);
            if (alternative != null) assertTrue(TradeFuelPriority.relief(state, selected.shipment())
                    >= TradeFuelPriority.relief(state, alternative) - 1e-9);
        }
        assertEquals(totalCash(state), totalCash(selected.shipment().state()), 1e-5);
        for (String material : List.of("hydrogen_gas", "refined_uranium"))
            assertEquals(stock(state, material), stock(selected.shipment().state(), material), 1e-5);
        assertTrue(hub(state, "moon-market").reservedTradeFuel().isEmpty());
        var dispatched = new LogisticsProcessor().processTradeRoutes(state.withTradeRoutes(List.of(route))).state();
        assertTrue(route(dispatched, route.id()).onboardKg() > 0);
        assertTrue(TradeFuelPriority.relief(state, dispatched, route(dispatched, route.id()))
                >= TradeFuelPriority.relief(state, selected.shipment()) - 1e-9);
    }

    @Test void reservingAnotherUrgentFuelReducesReliefEvenWhenItIsNotFreight() throws Exception {
        var state = opening();
        var target = hub(state, "moon-market");
        var route = route(state, "moon-supply").withMarketChoice("hydrogen_gas", target.id());
        var shipment = new LogisticsProcessor().previewShipment(state, route, 25000, true);
        assertNotNull(shipment);
        var prepared = shipment.state();
        var after = hub(prepared, target.id());
        var orders = new HashMap<>(after.activeOrders());
        var uranium = orders.get("refined_uranium");
        orders.put("refined_uranium", new MarketOrder("refined_uranium", uranium.supplyKg() - 10,
                uranium.demandKg(), uranium.pricePerKg(), 0));
        var reduced = after.withInventory(state.turn(), after.currentStoredWeightKg(), orders);
        prepared = prepared.withCommercialHubs(prepared.commercialHubs().stream()
                .map(hub -> hub.id().equals(reduced.id()) ? reduced : hub).toList());
        double original = TradeFuelPriority.relief(state, shipment);
        double changed = TradeFuelPriority.relief(state, prepared, shipment.route());
        assertEquals(10 / target.stockTargets().get("refined_uranium").reorderThresholdKg(), original - changed, 1e-9);
    }
}
