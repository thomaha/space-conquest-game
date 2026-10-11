package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.spaceconquest.engine.logistics.LunarRoundTripAuditTest.*;
import static org.junit.jupiter.api.Assertions.*;

class TradeReturnFuelSizingTest {
    private static final Map<String, Double> GOODS = Map.of("steel", 1250.0, "food_matrix", 1250.0);

    private GameState opening() throws Exception {
        var state = new ThresholdLunarSupplyAuditTest().opening(.8, false).state;
        return state.withTradeRoutes(List.of(route(state, "lunar").withRoaming(false).withActive(false)));
    }

    @Test void safeReturnUsesLessPropellantThanAFullWorkingTank() throws Exception {
        var state = opening();
        var shipment = new LogisticsProcessor().previewBasket(state, route(state, "lunar"), GOODS);
        assertNotNull(shipment);
        var loaded = shipment.preparedFleet();
        var target = hub(state, "moon-market");
        var site = FleetPositioning.hubSite(state, target);
        var arrival = LocalTravel.arrivalPreview(state, loaded, site, LocalTravel.plan(state, loaded, site));
        arrival = arrival.withShips(arrival.ships().stream().map(ship -> {
            var cargo = new HashMap<>(ship.storedCargoKg());
            GOODS.forEach((id, mass) -> cargo.computeIfPresent(id, (key, kg) -> Math.max(0, kg - mass)));
            return new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(),
                    ship.currentShieldHealth(), ship.currentFuelKg(), cargo, ship.passengerCount(),
                    ship.passengerRaceId(), ship.transitMode(), ship.powerState(), ship.supplyState());
        }).toList());
        var quote = RoamingTradeCost.quote(state, loaded, hub(state, "depot-market"), target);
        var projectedArrival = arrival;
        var projected = shipment.state().withTurn(state.turn() + (long) Math.ceil(quote.days()))
                .withFleets(shipment.state().fleets().stream()
                        .map(fleet -> fleet.id().equals(projectedArrival.id()) ? projectedArrival : fleet).toList());
        var full = TradeLegReadiness.prepareFull(projected, arrival, hub(state, "depot-market"));
        var selected = TradeReturnFuelSizing.prepare(projected, arrival, hub(state, "depot-market"));
        assertNotNull(selected);
        assertTrue(TradeLegReadiness.inspect(projected, selected, hub(state, "depot-market")).ready());
        assertFalse(TradeFuelEconomy.preferFull(projected, arrival, hub(projected, "moon-market")));
        assertTrue(selected.ships().getFirst().currentFuelKg() < ship(full, "trader").currentFuelKg() - 1e-6,
                "An expensive port supplies a proven smaller allowance without a fixed savings target.");
        var undersupplied = selected.withShips(selected.ships().stream().map(ship -> new ShipInstance(ship.id(), ship.designId(),
                ship.ownerEntityId(), ship.currentHullHealth(), ship.currentShieldHealth(), ship.currentFuelKg() / 2,
                ship.storedCargoKg(), ship.passengerCount(), ship.passengerRaceId(), ship.transitMode(),
                ship.powerState(), ship.supplyState())).toList());
        assertFalse(TradeLegReadiness.inspect(projected, undersupplied, hub(state, "depot-market")).ready(),
                "Conservation must still reject an unfunded physical return.");
        assertEquals(projectedArrival, fleet(projected, "trader"), "Pure sizing leaves the input snapshot untouched");
    }

    @Test void finiteStockThatCannotFillTheTankCanStillFundAProtectedReturn() throws Exception {
        var state = opening();
        var preview = new LogisticsProcessor().previewBasket(state, route(state, "lunar"), GOODS, true);
        assertNotNull(preview);
        var reservation = hub(preview.state(), "moon-market").reservedTradeFuel().get("trader");
        var target = hub(state, "moon-market");
        var orders = new HashMap<>(target.activeOrders());
        reservation.materialsKg().forEach((id, mass) -> {
            var order = orders.get(id);
            orders.put(id, new MarketOrder(id, mass * 1.02, order.demandKg(), order.pricePerKg(), 0));
        });
        var limited = target.withInventory(state.turn(), orders.values().stream().mapToDouble(MarketOrder::supplyKg).sum(), orders);
        state = state.withCommercialHubs(state.commercialHubs().stream()
                .map(hub -> hub.id().equals(limited.id()) ? limited : hub).toList());
        double cash = totalCash(state), hydrogen = stock(state, "liquid_hydrogen"), oxygen = stock(state, "liquid_oxygen");
        var shipment = new LogisticsProcessor().previewBasket(state, route(state, "lunar"), GOODS, true);
        assertNotNull(shipment);
        assertEquals(cash, totalCash(shipment.state()), 1e-5);
        assertEquals(hydrogen, stock(shipment.state(), "liquid_hydrogen"), 1e-5);
        assertEquals(oxygen, stock(shipment.state(), "liquid_oxygen"), 1e-5);
        assertTrue(hub(state, "moon-market").reservedTradeFuel().isEmpty());
    }
}
