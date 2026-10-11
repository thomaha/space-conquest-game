package com.spaceconquest.control;

import com.spaceconquest.control.command.SetHubStockTargetCommand;
import com.spaceconquest.control.command.SetTariffRateCommand;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.market.HubStockTarget;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class HubStockTargetCommandTest {
    private GameState world() {
        var hub = new CommercialHub("hub", "station", 0, 1000, 0, 10, Map.of(
                "steel", new MarketOrder("steel", 0, 0, 2, 0)));
        var station = new OrbitalStation("station", "Station", "sol", "", "owner", "PUBLIC_STATE", 10,
                List.of(), Map.of(), 0, 0, 0, 0, 100, 100, "steel", 0, true);
        var owner = new Empire("owner", "Owner", "human", "Individualist", 1000, 0,
                List.of("sol"), List.of(), Map.of(), List.of(), List.of());
        return GameState.builder().empires(List.of(owner)).orbitalStations(List.of(station))
                .commercialHubs(List.of(hub)).build();
    }

    @Test void configuresAndRemovesTargetsWithoutChangingStockOrCash() {
        var state = world();
        var command = new SetHubStockTargetCommand("owner", "hub", "steel", 100, 300);
        assertTrue(command.validate(state));
        var configured = command.apply(state);
        assertEquals(Map.of("steel", new HubStockTarget(100, 300)), configured.commercialHubs().getFirst().stockTargets());
        assertEquals(state.empires(), configured.empires());
        assertEquals(state.commercialHubs().getFirst().activeOrders(), configured.commercialHubs().getFirst().activeOrders());
        var taxed = new SetTariffRateCommand("owner", "hub", .1).apply(configured);
        assertEquals(configured.commercialHubs().getFirst().stockTargets(), taxed.commercialHubs().getFirst().stockTargets());
        assertTrue(new SetHubStockTargetCommand("owner", "hub", "steel", 0, 0).apply(taxed)
                .commercialHubs().getFirst().stockTargets().isEmpty());
    }

    @Test void rejectsInvalidQuantitiesUnauthorizedHubsAndCombinedOvercommitment() {
        var state = world();
        for (double[] values : List.of(new double[]{-1, 300}, new double[]{400, 300},
                new double[]{0, Double.NaN}, new double[]{Double.POSITIVE_INFINITY, 300}, new double[]{100, 1001})) {
            var command = new SetHubStockTargetCommand("owner", "hub", "steel", values[0], values[1]);
            assertFalse(command.validate(state));
            assertSame(state, command.apply(state));
        }
        assertFalse(new SetHubStockTargetCommand("stranger", "hub", "steel", 100, 300).validate(state));
        assertFalse(new SetHubStockTargetCommand("owner", "missing", "steel", 100, 300).validate(state));
        assertFalse(new SetHubStockTargetCommand("owner", "hub", "unknown", 100, 300).validate(state));
        var hub = state.commercialHubs().getFirst().withStockTargets(Map.of("food_matrix", new HubStockTarget(500, 800)));
        assertFalse(new SetHubStockTargetCommand("owner", "hub", "steel", 100, 300)
                .validate(state.withCommercialHubs(List.of(hub))));
    }

    @Test void canRequestFuelBeforeTheHubHasEverStockedIt() {
        var state = world();
        var supplier = new CommercialHub("source", "elsewhere", 0, 10000, 5000, 10,
                Map.of("liquid_hydrogen", new MarketOrder("liquid_hydrogen", 5000, 0, .01, 0)));
        state = state.withCommercialHubs(List.of(state.commercialHubs().getFirst(), supplier));
        var command = new SetHubStockTargetCommand("owner", "hub", "liquid_hydrogen", 200, 600);
        assertTrue(command.validate(state));
        var configured = command.apply(state).commercialHubs().getFirst();
        assertEquals(0, configured.activeOrders().get("liquid_hydrogen").supplyKg());
        assertEquals(new HubStockTarget(200, 600), configured.stockTargets().get("liquid_hydrogen"));
    }
}
