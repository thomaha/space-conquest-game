package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.MarketOrder;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HubFuelStockSuggestionsTest {
    private HubFuelStockSuggestions.Fuel hydrolox() {
        return HubFuelStockSuggestions.fuels().stream().filter(fuel -> fuel.id().equals("mod_hydrolox_rocket")).findFirst().orElseThrow();
    }

    @Test void coversOneAtomicDepartureAndDemandThroughCycleAndDelayInMixtureRatio() {
        var plan = HubFuelStockSuggestions.calculate(hydrolox(), new HubFuelStockSuggestions.Inputs(120000, 5000, 14, 2));
        assertEquals(200000, plan.reorderMixtureKg());
        assertEquals(320000, plan.targetMixtureKg());
        assertEquals(34000, plan.targets().get("liquid_hydrogen").reorderThresholdKg(), 1e-6);
        assertEquals(166000, plan.targets().get("liquid_oxygen").reorderThresholdKg(), 1e-6);
        assertEquals(54400, plan.targets().get("liquid_hydrogen").targetStockKg(), 1e-6);
        assertEquals(265600, plan.targets().get("liquid_oxygen").targetStockKg(), 1e-6);
        assertThrows(UnsupportedOperationException.class, () -> plan.targets().clear());
    }

    @Test void targetAddsCycleDemandWhenItExceedsAnAtomicDeparture() {
        var shortCycle = HubFuelStockSuggestions.calculate(hydrolox(), new HubFuelStockSuggestions.Inputs(100, 20, 3, 2));
        var longCycle = HubFuelStockSuggestions.calculate(hydrolox(), new HubFuelStockSuggestions.Inputs(100, 20, 10, 2));
        assertEquals(200, shortCycle.reorderMixtureKg());
        assertEquals(300, shortCycle.targetMixtureKg());
        assertEquals(340, longCycle.reorderMixtureKg());
        assertEquals(540, longCycle.targetMixtureKg());
        assertEquals(200, HubFuelStockSuggestions.calculate(hydrolox(),
                new HubFuelStockSuggestions.Inputs(100, 0, 10, 2)).targetMixtureKg());
    }

    @Test void rejectsUnknownTurnaroundInvalidInputsAndOverflow() {
        for (double invalid : List.of(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException.class, () -> new HubFuelStockSuggestions.Inputs(invalid, 1, 1, 1));
            assertThrows(IllegalArgumentException.class, () -> new HubFuelStockSuggestions.Inputs(1, invalid, 1, 1));
            assertThrows(IllegalArgumentException.class, () -> new HubFuelStockSuggestions.Inputs(1, 1, invalid, 1));
            assertThrows(IllegalArgumentException.class, () -> new HubFuelStockSuggestions.Inputs(1, 1, 1, invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> new HubFuelStockSuggestions.Inputs(1, 1, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> new HubFuelStockSuggestions.Inputs(0, 0, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> HubFuelStockSuggestions.calculate(hydrolox(),
                new HubFuelStockSuggestions.Inputs(1, Double.MAX_VALUE, 10, 2)));
    }

    @Test void estimatesFullFleetRefillAndBothLocalSupplierLegsWithoutPurchases() {
        var state = new RoamingTradePlannerTest().state(false, false);
        var rp1 = HubFuelStockSuggestions.fuels().stream().filter(fuel -> fuel.id().equals("mod_chemical_rocket")).findFirst().orElseThrow();
        var hub = state.commercialHubs().get(1);
        var estimate = HubFuelStockSuggestions.estimate(state, hub, rp1);
        assertEquals(6000, estimate.departureKg(), 1e-6);
        assertEquals(4, estimate.cycleDays());
        assertEquals(0, estimate.dailyKg());
        assertTrue(estimate.explanation().contains("no completed usage history"));
        assertEquals(10000, state.empires().getFirst().treasuryCredits());
        assertEquals(0, state.tradeRoutes().getFirst().onboardKg());
        assertTrue(hub.stockTargets().isEmpty());
        var unavailable = HubFuelStockSuggestions.estimate(state.withTradeRoutes(List.of()), hub, rp1);
        assertEquals(0, unavailable.cycleDays());
        assertEquals(0, unavailable.departureKg());
        assertTrue(unavailable.explanation().contains("manually"));
    }

    @Test void modeledIngredientDemandUsesTheLimitingMixtureRatioAndFullTanksStillNeedFutureBuffers() {
        var state = new RoamingTradePlannerTest().state(false, false);
        var hub = state.commercialHubs().get(1);
        var orders = new HashMap<>(hub.activeOrders());
        orders.put("liquid_hydrogen", new MarketOrder("liquid_hydrogen", 1000, 17, 1, 0));
        orders.put("liquid_oxygen", new MarketOrder("liquid_oxygen", 1000, 166, 1, 0));
        hub = new CommercialHub(hub.id(), hub.entityId(), 0, hub.storageCapacityKg(), 2000, 10, orders);
        assertEquals(200, HubFuelStockSuggestions.estimate(state, hub, hydrolox()).dailyKg(), 1e-6);
        var uranium = HubFuelStockSuggestions.fuels().stream().filter(fuel -> fuel.id().equals("refined_uranium")).findFirst().orElseThrow();
        assertEquals(1, HubFuelStockSuggestions.calculate(uranium,
                new HubFuelStockSuggestions.Inputs(1, 0, 2, 2)).targets().size());
    }
}
