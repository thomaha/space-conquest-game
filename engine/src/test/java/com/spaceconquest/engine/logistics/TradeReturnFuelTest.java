package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static com.spaceconquest.engine.logistics.LunarRoundTripAuditTest.*;
import static org.junit.jupiter.api.Assertions.*;

class TradeReturnFuelTest {
    @TempDir Path directory;
    private static final Map<String, Double> GOODS = Map.of("steel", 1250.0, "food_matrix", 1250.0);

    private GameState opening() throws Exception {
        var state = new ThresholdLunarSupplyAuditTest().opening(.8, false).state;
        return state.withTradeRoutes(List.of(route(state, "lunar").withRoaming(false).withActive(false)));
    }

    private LogisticsProcessor.ShipmentPreview dispatch(GameState state) {
        return new LogisticsProcessor().previewBasket(state, route(state, "lunar"), GOODS, true);
    }

    @Test void prepurchasesPhysicalReturnFuelOutsidePublicStockWithoutChangingFreight() throws Exception {
        var state = opening();
        var shipment = dispatch(state);
        assertNotNull(shipment);
        var after = shipment.state();
        var reservation = hub(after, "moon-market").reservedTradeFuel().get("trader");
        assertNotNull(reservation);
        assertTrue(reservation.massKg() > 50000);
        assertEquals("trader-owner", reservation.ownerEntityId());
        assertEquals(GOODS.keySet(), shipment.route().cargoManifest().keySet());
        assertEquals(2500, shipment.route().onboardKg());
        assertEquals(hub(state, "moon-market").currentStoredWeightKg(), hub(after, "moon-market").currentStoredWeightKg(), 1e-6);
        assertEquals(totalCash(state), totalCash(after), 1e-5);
        reservation.materialsKg().forEach((id, kg) -> {
            assertEquals(kg, hub(state, "moon-market").activeOrders().get(id).supplyKg()
                    - hub(after, "moon-market").activeOrders().get(id).supplyKg(), 1e-5);
            assertEquals(stock(state, id), stock(after, id), 1e-5);
        });
        assertTrue(hub(state, "moon-market").reservedTradeFuel().isEmpty());
    }

    @Test void returnStillLeavesAfterOtherCustomersBuyEveryPublicFuelIngredient() throws Exception {
        var state = opening();
        var shipment = dispatch(state);
        assertNotNull(shipment);
        state = shipment.state().withTradeRoutes(List.of(shipment.route()));
        double paidBalance = state.empires().stream().filter(owner -> owner.id().equals("trader-owner"))
                .findFirst().orElseThrow().treasuryCredits();
        var request = new HashMap<String, Double>();
        hub(state, "moon-market").activeOrders().forEach((id, order) -> {
            if (!GOODS.containsKey(id)) request.put(id, order.supplyKg());
        });
        var bought = ConstructionMaterials.buyUpTo(state, "moon-port", "supplier-owner", request);
        assertTrue(bought.covers(request));
        state = bought.state();
        for (int day = 0; day < 60 && route(state, "lunar").onboardKg() > 0; day++) {
            state = state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), List.of(), List.of()))
                    .withTurn(state.turn() + 1);
            state = new LogisticsProcessor().processTradeRoutes(state).state();
        }
        assertEquals(2500, route(state, "lunar").totalVolumeMovedKg(), 1e-4);
        assertEquals(0, route(state, "lunar").onboardKg());
        assertEquals(paidBalance + 6000, state.empires().stream().filter(owner -> owner.id().equals("trader-owner"))
                .findFirst().orElseThrow().treasuryCredits(), 1e-5, "Unloading and protected refueling charge no second fuel payment");
        var before = state;
        state = TradeLegReadiness.prepare(state, fleet(state, "trader"), hub(state, "depot-market"));
        assertEquals(totalCash(before), totalCash(state), 1e-5, "Claim does not pay for fuel a second time");
        var carrier = fleet(state, "trader");
        assertTrue(carrier.location().inTransit() || TradeLegReadiness.inspect(state, carrier, hub(state, "depot-market")).ready());
        for (int day = 0; day < 60 && !FleetPositioning.atHub(state, fleet(state, "trader"), hub(state, "depot-market")); day++) {
            state = state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), List.of(), List.of()))
                    .withTurn(state.turn() + 1);
            state = new LogisticsProcessor().processTradeRoutes(state).state();
        }
        assertTrue(FleetPositioning.atHub(state, fleet(state, "trader"), hub(state, "depot-market")));
    }

    @Test void absentDestinationOxidizerRejectsAndRollsBackCargoAndSourceFuelPurchases() throws Exception {
        var state = opening();
        var target = hub(state, "moon-market");
        var orders = new HashMap<>(target.activeOrders());
        var oxygen = orders.get("liquid_oxygen");
        orders.put(oxygen.resourceId(), new MarketOrder(oxygen.resourceId(), 0, 0, oxygen.pricePerKg(), 0));
        var empty = target.withInventory(state.turn(), target.currentStoredWeightKg() - oxygen.supplyKg(), orders);
        state = state.withCommercialHubs(state.commercialHubs().stream().map(hub -> hub.id().equals(empty.id()) ? empty : hub).toList());
        assertNull(dispatch(state));
        assertEquals(0, ship(state, "trader").currentFuelKg());
        assertTrue(ship(state, "trader").storedCargoKg().isEmpty());
        assertTrue(hub(state, "moon-market").reservedTradeFuel().isEmpty());
        assertNotNull(new LogisticsProcessor().previewBasket(state, route(state, "lunar"), GOODS), "Explicit one-way preview remains available");
    }

    @Test void reservationsSurviveSaveReloadAndOrdinaryHubUpdates() throws Exception {
        var shipment = dispatch(opening());
        assertNotNull(shipment);
        var fixture = new LunarRoundTripAuditTest();
        fixture.directory = directory;
        var state = fixture.reload(shipment.state().withTradeRoutes(List.of(shipment.route())), "return-reserved");
        var hub = hub(state, "moon-market");
        assertFalse(hub.reservedTradeFuel().isEmpty());
        assertEquals(hub.reservedTradeFuel(), hub.observeDay(state.turn() + 1).withStockTargets(Map.of())
                .withInventory(state.turn() + 1, hub.currentStoredWeightKg(), hub.activeOrders()).reservedTradeFuel());
        assertEquals(shipment.state().commercialHubs(), state.commercialHubs());
    }

    @Test void anotherShipCannotClaimFuelReservedForTheTrader() throws Exception {
        var shipment = dispatch(opening());
        assertNotNull(shipment);
        var state = shipment.state();
        var tanker = fleet(state, "tanker").withLocation(FleetLocation.at(FleetLocation.Site.docked("moon-port")));
        state = state.withFleets(List.of(tanker));
        assertSame(state, TradeReturnFuel.claim(state, tanker));
        assertTrue(hub(state, "moon-market").reservedTradeFuel().containsKey("trader"));
    }

    @Test void everyAccompanyingShipGetsItsOwnPaidReservation() throws Exception {
        var state = opening();
        var trader = fleet(state, "trader");
        var original = trader.ships().getFirst();
        var escort = new ShipInstance("escort", original.designId(), original.ownerEntityId(), 100, 0, 0, Map.of())
                .withPowerState(original.powerState());
        var group = trader.withShips(List.of(original, escort));
        state = state.withFleets(state.fleets().stream().map(fleet -> fleet.id().equals(group.id()) ? group : fleet).toList());
        var shipment = dispatch(state);
        assertNotNull(shipment);
        var reservations = hub(shipment.state(), "moon-market").reservedTradeFuel();
        assertTrue(reservations.containsKey("trader"));
        assertTrue(reservations.containsKey("escort"));
        for (String id : List.of("liquid_hydrogen", "liquid_oxygen"))
            assertEquals(stock(state, id), stock(shipment.state(), id), 1e-5);
        assertEquals(totalCash(state), totalCash(shipment.state()), 1e-5);
    }

    @Test void insufficientCashForDestinationInsuranceRejectsTheWholeCandidate() throws Exception {
        var state = opening();
        state = state.withEmpires(state.empires().stream().map(owner -> owner.id().equals("trader-owner")
                ? new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), 3100, owner.corporateTaxRate(),
                owner.controlledSystemIds(), owner.ministries(), owner.systemGovernorAssignments(), owner.unlockedTechIds(), owner.activeShipDesignIds()) : owner).toList());
        assertNull(dispatch(state));
        assertNotNull(new LogisticsProcessor().previewBasket(state, route(state, "lunar"), GOODS), "The outbound trip alone is affordable");
        assertEquals(3100, state.empires().stream().filter(owner -> owner.id().equals("trader-owner")).findFirst().orElseThrow().treasuryCredits());
        assertTrue(hub(state, "moon-market").reservedTradeFuel().isEmpty());
    }
}
