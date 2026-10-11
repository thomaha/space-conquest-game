package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.market.HubStockTarget;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HubFuelCoverageTest {
    private final HubFuelStockSuggestions.Fuel hydrolox = HubFuelStockSuggestions.fuels().stream()
            .filter(fuel -> fuel.id().equals("mod_hydrolox_rocket")).findFirst().orElseThrow();

    private GameState world() {
        var state = new RoamingTradePlannerTest().state(false, false);
        var source = state.commercialHubs().getFirst();
        var orders = new HashMap<>(source.activeOrders());
        orders.put("liquid_hydrogen", new MarketOrder("liquid_hydrogen", 1000, 0, 1, 0));
        var target = state.commercialHubs().get(1);
        target = new CommercialHub(target.id(), target.entityId(), 0, 100000, 183, 10, Map.of(
                "liquid_hydrogen", new MarketOrder("liquid_hydrogen", 17, 0, 2, 0),
                "liquid_oxygen", new MarketOrder("liquid_oxygen", 166, 0, 2, 0)), Map.of(
                "liquid_hydrogen", new HubStockTarget(34, 51, true),
                "liquid_oxygen", new HubStockTarget(166, 249, true)));
        var updated = new CommercialHub(source.id(), source.entityId(), 0, source.storageCapacityKg(),
                source.currentStoredWeightKg() + 1000, 10, orders);
        return state.withCommercialHubs(List.of(updated, target, state.commercialHubs().getLast()));
    }

    @Test void mixtureCoverageUsesScarceIngredientAndPhysicalPaidInboundEvenOnPausedRoute() {
        var state = world();
        var original = state.fleets().getFirst().ships().getFirst();
        var ship = new com.spaceconquest.engine.ship.ShipInstance(original.id(), original.designId(), original.ownerEntityId(),
                original.currentHullHealth(), original.currentShieldHealth(), original.currentFuelKg(), Map.of("liquid_hydrogen", 17.0),
                original.passengerCount(), original.passengerRaceId(), original.transitMode(), original.powerState(), original.supplyState());
        var route = state.tradeRoutes().getFirst().withLoadedManifest(Map.of("liquid_hydrogen", new TradeCargo(30, 30))).withActive(false);
        state = state.withFleets(List.of(state.fleets().getFirst().withShips(List.of(ship)))).withTradeRoutes(List.of(route));
        var coverage = HubFuelCoverage.inspect(state, state.commercialHubs().get(1), hydrolox);
        assertEquals(100, coverage.usableKg(), 1e-6);
        assertEquals(200, coverage.positionUsableKg(), 1e-6);
        assertEquals(List.of("liquid_hydrogen"), coverage.limitingIngredients());
        var hydrogen = coverage.ingredients().getFirst();
        assertEquals(17, hydrogen.inboundKg());
        assertEquals(17, hydrogen.requestedKg(), 1e-6);
        assertEquals(34, hydrogen.reorderKg());
        assertEquals(51, hydrogen.targetKg());
        assertTrue(hydrogen.refillRequested());
        assertEquals(1, coverage.suppliers().unavailableRoutes());
        assertEquals(30, state.tradeRoutes().getFirst().onboardKg());
        assertThrows(UnsupportedOperationException.class, () -> coverage.ingredients().clear());
        assertThrows(UnsupportedOperationException.class, () -> coverage.warnings().clear());
    }

    @Test void inaccessibleWarehouseDoesNotHideMissingSourceStock() {
        var state = world();
        var source = state.commercialHubs().getFirst();
        var orders = new HashMap<>(source.activeOrders());
        orders.put("liquid_hydrogen", new MarketOrder("liquid_hydrogen", 0, 0, 1, 0));
        state = state.withCommercialHubs(List.of(new CommercialHub(source.id(), source.entityId(), 0,
                        source.storageCapacityKg(), source.currentStoredWeightKg() - 1000, 10, orders),
                state.commercialHubs().get(1), new CommercialHub("reserve", "private-warehouse", 0, 10000, 10000, 10,
                        Map.of("liquid_hydrogen", new MarketOrder("liquid_hydrogen", 10000, 0, 1, 0)))));
        var coverage = HubFuelCoverage.inspect(state, state.commercialHubs().get(1), hydrolox);
        assertEquals(0, coverage.ingredients().getFirst().sourceStockKg());
        assertTrue(coverage.warnings().stream().anyMatch(message -> message.contains("No stock") && message.contains("liquid_hydrogen")));
        assertEquals(1, coverage.suppliers().directedRoutes());
    }

    @Test void unavailableCarrierAndPotentialRoamingServiceAreDistinct() {
        var state = world();
        var missing = new TradeRoute("missing", "Supplier", "owner", "source", "buyer", "steel", 20, 0, 1000,
                List.of("absent"), 0, true);
        state = state.withTradeRoutes(List.of(missing));
        var missingCoverage = HubFuelCoverage.inspect(state, state.commercialHubs().get(1), hydrolox);
        assertEquals(0, missingCoverage.suppliers().directedRoutes());
        assertEquals(1, missingCoverage.suppliers().unavailableRoutes());
        assertTrue(missingCoverage.warnings().contains("No active supplier route with an assigned cargo ship."));
        state = state.withTradeRoutes(List.of(new TradeRoute("roamer", "Trader", "owner", "source", "third", "steel", 20, 0, 1000,
                List.of("ship"), 0, true).withRoaming(true)));
        var roamingCoverage = HubFuelCoverage.inspect(state, state.commercialHubs().get(1), hydrolox);
        assertEquals(1, roamingCoverage.suppliers().roamingRoutes());
        assertEquals(0, roamingCoverage.suppliers().directedRoutes());
        assertTrue(roamingCoverage.warnings().stream().anyMatch(message -> message.startsWith("Only potential roaming")));
    }

    @Test void emptyIngredientsAreReportedTogetherAndAutomaticPolicyHasNoInventedThreshold() {
        var state = world();
        var hub = state.commercialHubs().get(1);
        hub = new CommercialHub(hub.id(), hub.entityId(), 0, 100000, 0, 10, Map.of(), Map.of());
        var coverage = HubFuelCoverage.inspect(state, hub, hydrolox);
        assertEquals(0, coverage.usableKg());
        assertEquals(List.of("liquid_hydrogen", "liquid_oxygen"), coverage.limitingIngredients());
        assertTrue(coverage.ingredients().stream().noneMatch(HubFuelCoverage.Ingredient::configured));
        assertTrue(coverage.ingredients().stream().allMatch(item -> item.requestedKg() == 0));
        assertTrue(coverage.warnings().getFirst().contains("liquid_hydrogen, liquid_oxygen"));
    }
}
